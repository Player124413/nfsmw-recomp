package dev.recompkit_nfsmw.android.game

import android.util.Log

/**
 * JNI bindings for the bridge (src/main/cpp/bridge.cpp). The bridge is what
 * calls the game library; everything about the game ABI stays on that side.
 */
object GameNative {

    private const val TAG = "nfsbridge"
    const val GAME_LIB_NAME = "nfsmw"

    private var bridgeLoaded = false
    private var gameLoaded = false

    fun loadBridge(): Boolean {
        if (bridgeLoaded) return true
        return try {
            System.loadLibrary("nfsbridge")
            bridgeLoaded = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "the launcher bridge failed to load", t)
            false
        }
    }

    fun loadGameLibrary(): Boolean {
        if (gameLoaded) return true
        return try {
            System.loadLibrary(GAME_LIB_NAME)
            gameLoaded = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "the game library could not be loaded", t)
            false
        }
    }

    // The NFSMW_SYM_* mask of the symbols the game exports, -1 when a
    // required symbol is missing (see lastError()).
    external fun probeGame(): Int
    external fun gameStart(widthPx: Int, heightPx: Int, dpi: Int, gameDir: String, settingsPath: String): Int
    external fun gameKey(code: Int, down: Int)
    external fun gameTouch(pointerId: Int, action: Int, x: Float, y: Float)
    external fun gamePause()
    external fun gameResume()
    external fun gameRequestShutdown(): Int
    external fun gameShutdown()
    external fun gameSetRenderScale(scale: Float)
    external fun gameSetFrameLimit(fps: Int)
    external fun gameResize(widthPx: Int, heightPx: Int)
    external fun lastError(): String
}
