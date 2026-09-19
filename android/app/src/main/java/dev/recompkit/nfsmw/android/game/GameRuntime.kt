package dev.recompkit_nfsmw.android.game

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.recompkit_nfsmw.android.BuildInfo

/**
 * Owns the game thread and translates the launcher's intent into ABI calls.
 *
 * The game runs in its own thread (nfsmw_android_init blocks until the game
 * exits); input arrives on the UI thread and is forwarded straight into the
 * bridge - the ABI requires the host to queue it.
 */
class GameRuntime {

    enum class Phase { IDLE, RUNNING, STOPPING, STOPPED, FAILED }

    var phase: Phase = Phase.IDLE
        private set

    /** Set when the game fails to start (or exits nonzero). */
    var failReason: String? = null
        private set

    var onPhase: ((Phase) -> Unit)? = null

    private var thread: Thread? = null
    private var symbolMask = 0
    private val ui = Handler(Looper.getMainLooper())

    fun hasSymbol(flag: Int): Boolean = symbolMask and flag != 0

    /**
     * Loads the game and starts it. Returns false (and sets [failReason])
     * when the game cannot start at all - the game thread never blocks, so
     * this call is safe on the UI thread.
     */
    fun start(widthPx: Int, heightPx: Int, dpi: Int, gameDir: String, settingsPath: String): Boolean {
        if (phase != Phase.IDLE) return false
        if (!BuildInfo.hasGame) {
            fail("This build does not contain the game library (development shell). " +
                "Build an APK from a release zip - see docs/android.md.")
            return false
        }
        if (!GameNative.loadBridge()) {
            fail("the launcher bridge could not be loaded: " + GameNative.lastError())
            return false
        }
        if (!GameNative.loadGameLibrary()) {
            fail("the game library (lib" + GameNative.GAME_LIB_NAME + ".so) could not be loaded: " +
                GameNative.lastError())
            return false
        }
        val mask = GameNative.probeGame()
        if (mask < 0) {
            fail(GameNative.lastError())
            return false
        }
        symbolMask = mask
        thread = Thread({
            val rc = try {
                GameNative.gameStart(widthPx, heightPx, dpi, gameDir, settingsPath)
            } catch (t: Throwable) {
                Log.e("nfsbridge", "game thread crashed", t)
                -999
            }
            if (rc != 0) {
                failReason = "the game exited with code $rc" +
                    (GameNative.lastError().ifEmpty { null } ?: " (see logcat: tag nfsbridge / nfsmw-game)")
                ui.post { setPhase(Phase.FAILED) }
            } else {
                GameNative.gameShutdown()
                ui.post { setPhase(Phase.STOPPED) }
            }
        }, "nfsmw-game").apply { start() }
        setPhase(Phase.RUNNING)
        return true
    }

    fun key(code: Int, down: Boolean) {
        if (phase == Phase.RUNNING) GameNative.gameKey(code, if (down) 1 else 0)
    }

    fun touch(pointerId: Int, action: Int, x: Float, y: Float) {
        if (phase == Phase.RUNNING && hasSymbol(GameSymbols.TOUCH)) {
            GameNative.gameTouch(pointerId, action, x, y)
        }
    }

    fun pause() {
        if (phase == Phase.RUNNING) GameNative.gamePause()
    }

    fun resume() {
        if (phase == Phase.RUNNING) GameNative.gameResume()
    }

    /**
     * Asks the game to quit (its menu's quit path). Returns false when the
     * game does not implement nfsmw_android_request_shutdown - the caller
     * then falls back to key events.
     */
    fun requestQuit(): Boolean {
        if (phase != Phase.RUNNING) return false
        if (!hasSymbol(GameSymbols.REQUEST_SHUTDOWN)) return false
        GameNative.gameRequestShutdown()
        return true
    }

    /** Called from onDestroy: request the quit, wait for the game to end. */
    fun finish() {
        if (phase == Phase.STOPPED || phase == Phase.STOPPING) return
        setPhase(Phase.STOPPING)
        GameNative.gameRequestShutdown()
        thread?.let { t ->
            try {
                t.join(3000)
            } catch (e: InterruptedException) {
                // leaving anyway
            }
        }
        setPhase(Phase.STOPPED)
    }

    private fun fail(reason: String) {
        failReason = reason
        setPhase(Phase.FAILED)
    }

    private fun setPhase(p: Phase) {
        phase = p
        onPhase?.invoke(p)
    }
}
