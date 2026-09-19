package dev.recompkit_nfsmw.android

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.recompkit_nfsmw.android.controls.EditPanel
import dev.recompkit_nfsmw.android.controls.TouchControlsOverlay
import dev.recompkit_nfsmw.android.game.GameCodes
import dev.recompkit_nfsmw.android.game.GameRuntime
import dev.recompkit_nfsmw.android.layout.ControlStore
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File

/**
 * Runs the game: the SurfaceView behind, the touch overlay in front, the
 * Edit button for control layout, and a quit flow that asks the game
 * (nfsmw_android_request_shutdown) with a force-stop fallback.
 */
class GameActivity : AppCompatActivity() {

    private lateinit var surface: SurfaceView
    private lateinit var overlay: TouchControlsOverlay
    private lateinit var editButton: ImageButton
    private lateinit var panelHost: FrameLayout
    private lateinit var runtime: GameRuntime
    private var panel: EditPanel? = null
    private var started = false
    private var errorShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        surface = findViewById(R.id.surface)
        overlay = findViewById(R.id.overlay)
        editButton = findViewById(R.id.edit_button)
        panelHost = findViewById(R.id.panel_host)
        runtime = GameRuntime()

        val metrics = resources.displayMetrics
        overlay.layout = ControlStore.load(this, metrics.widthPixels, metrics.heightPixels, metrics.density)
        overlay.hapticsEnabled = SettingsStore.load(this).haptics
        overlay.gameInput = object : TouchControlsOverlay.GameInput {
            override fun keyDown(code: Int) { runtime.key(code, true) }
            override fun keyUp(code: Int) { runtime.key(code, false) }
            override fun touchDown(id: Int, x: Float, y: Float) { runtime.touch(id, 0, x, y) }
            override fun touchMove(id: Int, x: Float, y: Float) { runtime.touch(id, 1, x, y) }
            override fun touchUp(id: Int, x: Float, y: Float) { runtime.touch(id, 2, x, y) }
        }
        overlay.onLayoutChanged = { ControlStore.save(this, overlay.layout); panel?.refresh() }

        editButton.setOnClickListener { toggleEditing() }

        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!started && !errorShown) {
                    started = true
                    startGame(surface.width, surface.height)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                runtime.pause()
            }
        })
    }

    private fun startGame(width: Int, height: Int) {
        val dpi = resources.displayMetrics.densityDpi
        val gameDir = File(filesDir, "game").absolutePath
        val settings = SettingsStore.load(this)
        SettingsStore.save(this, settings) // make sure the file exists for the game
        val ok = runtime.start(width, height, dpi, gameDir, SettingsStore.file(this).absolutePath)
        if (!ok) {
            started = false
            showError(runtime.failReason ?: getString(R.string.error_unknown), terminal = true)
        }
    }

    private fun toggleEditing() {
        val now = !overlay.editing
        overlay.editing = now
        if (now) {
            if (panel == null) {
                val p = EditPanel(this, overlay, onDone = { toggleEditing() })
                panel = p
                panelHost.addView(p)
            }
            panel?.refresh()
            panelHost.visibility = View.VISIBLE
        } else {
            panelHost.visibility = View.GONE
            ControlStore.save(this, overlay.layout)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // WindowCompat (not window.insetsController): the latter needs API 30.
        WindowCompat.getInsetsController(window, window.decorView)?.let { c ->
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            panelHost.visibility == View.VISIBLE -> toggleEditing()
            runtime.phase == GameRuntime.Phase.RUNNING -> showQuitDialog()
            else -> super.onBackPressed()
        }
    }

    private fun showQuitDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.quit_title)
            .setMessage(R.string.quit_message)
            .setPositiveButton(R.string.quit_request) { _, _ ->
                if (!runtime.requestQuit()) {
                    // The game has no request-shutdown hook: wake its pause
                    // menu instead; the in-game menu can quit.
                    runtime.key(GameCodes.ESCAPE, true)
                    runtime.key(GameCodes.ESCAPE, false)
                }
            }
            .setNegativeButton(R.string.quit_force) { _, _ -> forceStop() }
            .show()
    }

    private fun forceStop() {
        // Last resort: the game thread is not interruptible from here.
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun showError(message: String, terminal: Boolean) {
        if (errorShown) return
        errorShown = true
        val log = buildString {
            appendLine("NFS MW Recomp - Android")
            appendLine("build " + BuildInfo.BUILD_ID + " (" + BuildInfo.VERSION_NAME + ")")
            appendLine("reason: $message")
            if (BuildInfo.hasGame) appendLine("lib sha256: " + BuildInfo.LIB_SHA)
            if (BuildInfo.hasAssets) appendLine("assets: " + BuildInfo.ASSET_COUNT + " files")
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.error_title)
            .setMessage(log)
            .setNeutralButton(R.string.error_copy_log) { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("nfsmw-error", log))
            }
            .setPositiveButton(R.string.error_back) { _, _ ->
                if (terminal) forceStop() else finish()
            }
        builder.show()
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) runtime.pause()
    }

    override fun onResume() {
        super.onResume()
        runtime.resume()
        hideSystemBars()
    }

    override fun onDestroy() {
        runtime.finish()
        super.onDestroy()
    }
}
