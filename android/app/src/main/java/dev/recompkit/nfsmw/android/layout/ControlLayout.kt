package dev.recompkit_nfsmw.android.layout

/**
 * The touch control buttons. Coordinates are normalised (0..1, the button
 * centre), sizes are in dp. The layout is what the Edit screen edits and
 * what the game draws on top of the render.
 */
enum class ButtonId(val key: String) {
    ACCEL("accel"),
    BRAKE("brake"),
    STEER_LEFT("steer_left"),
    STEER_RIGHT("steer_right"),
    HANDBRAKE("handbrake"),
    MAP("map"),
    CAMERA("camera"),
    MENU("menu");

    companion object {
        fun fromKey(key: String): ButtonId? = values().firstOrNull { it.key == key }
    }
}

data class ControlButton(
    val id: ButtonId,
    var x: Float,
    var y: Float,
    var sizeDp: Int,
    var visible: Boolean
)

class ControlLayout(
    val version: Int,
    val buttons: LinkedHashMap<ButtonId, ControlButton>
) {

    fun button(id: ButtonId): ControlButton =
        buttons[id] ?: throw IllegalStateException("no button " + id)

    fun copy(): ControlLayout =
        ControlLayout(version, LinkedHashMap(buttons.mapValues { it.value.copy() }))

    companion object {
        const val LAYOUT_VERSION = 1
        const val MIN_SIZE_DP = 56
        const val MAX_SIZE_DP = 240

        /**
         * The default landscape layout: steering and handbrake under the left
         * thumb, gas and brake under the right, map/camera top-left, the
         * pause menu top-right (clear of the launcher's Edit button).
         */
        fun defaults(): ControlLayout {
            val m = LinkedHashMap<ButtonId, ControlButton>()
            fun def(id: ButtonId, x: Float, y: Float, size: Int) {
                m[id] = ControlButton(id, x, y, size, true)
            }
            def(ButtonId.STEER_LEFT, 0.065f, 0.70f, 128)
            def(ButtonId.STEER_RIGHT, 0.215f, 0.70f, 128)
            def(ButtonId.HANDBRAKE, 0.14f, 0.32f, 108)
            def(ButtonId.BRAKE, 0.685f, 0.74f, 112)
            def(ButtonId.ACCEL, 0.87f, 0.70f, 150)
            def(ButtonId.MAP, 0.05f, 0.10f, 72)
            def(ButtonId.CAMERA, 0.13f, 0.10f, 72)
            def(ButtonId.MENU, 0.84f, 0.10f, 76)
            return ControlLayout(LAYOUT_VERSION, m)
        }
    }

    /** A copy with every button clamped inside the screen. */
    fun clampAll(widthPx: Int, heightPx: Int, density: Float): ControlLayout {
        if (widthPx <= 0 || heightPx <= 0) return this
        val m = LinkedHashMap<ButtonId, ControlButton>()
        for ((id, b) in buttons) {
            val size = b.sizeDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
            val rPx = size * density / 2f
            val margin = 4f * density
            m[id] = b.copy(
                x = coerceCenter(b.x * widthPx, rPx + margin, widthPx - rPx - margin) / widthPx,
                y = coerceCenter(b.y * heightPx, rPx + margin, heightPx - rPx - margin) / heightPx,
                sizeDp = size
            )
        }
        return ControlLayout(version, m)
    }

    /** Like [coerceIn], but a button bigger than the screen lands in the middle. */
    private fun coerceCenter(value: Float, min: Float, max: Float): Float =
        if (min >= max) (min + max) / 2f else value.coerceIn(min, max)
}
