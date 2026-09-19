package dev.recompkit_nfsmw.android.controls

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import dev.recompkit_nfsmw.android.R
import dev.recompkit_nfsmw.android.layout.ButtonId
import dev.recompkit_nfsmw.android.layout.ControlLayout

/**
 * The edit-mode toolbar: one chip per button (tap = select, long-press =
 * hide or show), a size slider for the selected button, reset and done.
 * Shared by the launcher's controls screen and the in-game Edit button.
 */
class EditPanel(
    context: Context,
    private val overlay: TouchControlsOverlay,
    private val onDone: () -> Unit,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val chips: LinearLayout
    private val sizeSlider: SeekBar
    private val sizeLabel: TextView

    init {
        inflater.inflate(R.layout.view_edit_panel, this)
        chips = findViewById(R.id.chip_row)
        sizeSlider = findViewById(R.id.size_slider)
        sizeSlider.max = ControlLayout.MAX_SIZE_DP - ControlLayout.MIN_SIZE_DP
        sizeLabel = findViewById(R.id.size_label)

        val reset: ImageButton = findViewById(R.id.reset_button)
        val done: ImageButton = findViewById(R.id.done_button)
        reset.setOnClickListener {
            overlay.layout = ControlLayout.defaults().clampAll(
                overlay.width, overlay.height, resources.displayMetrics.density
            )
            overlay.selected = null
            refresh()
            overlay.onLayoutChanged?.invoke()
        }
        done.setOnClickListener { onDone() }

        sizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val size = ControlLayout.MIN_SIZE_DP + progress
                sizeLabel.text = context.getString(R.string.edit_size_value, size)
                val sel = overlay.selected
                if (sel != null && fromUser && overlay.layout.button(sel).sizeDp != size) {
                    val c = overlay.layout.copy()
                    c.button(sel).sizeDp = size
                    overlay.layout = c.clampAll(
                        overlay.width, overlay.height, resources.displayMetrics.density
                    )
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                overlay.onLayoutChanged?.invoke()
            }
        })

        refresh()
    }

    /** Rebuild the chips and sync the slider (call on any layout change). */
    fun refresh() {
        chips.removeAllViews()
        for (id in ButtonId.values()) {
            val b = overlay.layout.button(id)
            val chip = inflater.inflate(R.layout.item_control_chip, chips, false) as FrameLayout
            val icon: ImageView = chip.findViewById(R.id.chip_icon)
            val eye: ImageView = chip.findViewById(R.id.chip_eye)
            icon.setImageResource(overlay.iconResFor(id))
            eye.setImageResource(if (b.visible) R.drawable.ic_eye else R.drawable.ic_eye_off)
            eye.alpha = if (b.visible) 0.9f else 0.5f

            chip.setOnClickListener {
                val c = overlay.layout.copy()
                if (!c.button(id).visible) c.button(id).visible = true
                overlay.layout = c
                overlay.selected = id
                refresh()
            }
            chip.setOnLongClickListener {
                val c = overlay.layout.copy()
                c.button(id).visible = !c.button(id).visible
                overlay.layout = c
                overlay.onLayoutChanged?.invoke()
                refresh()
                true
            }
            chips.addView(chip)
        }
        val sel = overlay.selected
        if (sel != null) {
            val size = overlay.layout.button(sel).sizeDp
            sizeSlider.progress = (size - ControlLayout.MIN_SIZE_DP).coerceIn(0, sizeSlider.max)
            sizeLabel.text = context.getString(R.string.edit_size_value, size)
        } else {
            sizeLabel.setText(R.string.edit_size_none)
        }
    }
}
