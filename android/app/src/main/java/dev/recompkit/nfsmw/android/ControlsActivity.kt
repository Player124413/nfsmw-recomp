package dev.recompkit_nfsmw.android

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import dev.recompkit_nfsmw.android.controls.EditPanel
import dev.recompkit_nfsmw.android.controls.TouchControlsOverlay
import dev.recompkit_nfsmw.android.layout.ControlStore

/**
 * The launcher's touch controls screen: the overlay in edit mode over a
 * neutral background, with the edit panel. Changes persist as they happen.
 */
class ControlsActivity : AppCompatActivity() {

    private lateinit var overlay: TouchControlsOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controls)

        overlay = findViewById(R.id.overlay)
        val metrics = resources.displayMetrics
        overlay.layout = ControlStore.load(this, metrics.widthPixels, metrics.heightPixels, metrics.density)
        overlay.editing = true
        overlay.hapticsEnabled = SettingsStore.load(this).haptics
        overlay.gameInput = null // no game here: everything is editing

        val panel: EditPanel = EditPanel(this, overlay, onDone = { finish() })
        val host: FrameLayout = findViewById(R.id.panel_host)
        host.addView(panel)

        overlay.onLayoutChanged = {
            ControlStore.save(this, overlay.layout)
            panel.refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        ControlStore.save(this, overlay.layout)
    }
}
