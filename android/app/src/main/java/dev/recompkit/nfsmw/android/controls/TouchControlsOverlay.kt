package dev.recompkit_nfsmw.android.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import dev.recompkit_nfsmw.android.R
import dev.recompkit_nfsmw.android.game.GameCodes
import dev.recompkit_nfsmw.android.layout.ButtonId
import dev.recompkit_nfsmw.android.layout.ControlLayout
import kotlin.math.round
import kotlin.math.sqrt

/**
 * The on-screen touch controls.
 *
 * Game mode: a pointer that lands on a button drives that button's key (the
 * pointer is captured; the key releases when the finger lifts or slides
 * clear of the button). Pointers that land on nothing are forwarded to the
 * game untouched - menus and the like keep working.
 *
 * Edit mode (toggled by the Edit button, in the launcher's controls screen
 * and in game): drag a button to move it (8 dp grid), pinch it (or use the
 * panel's slider) to resize it, tap to select it, double-tap to hide or show
 * it. Changes are reported through [onLayoutChanged]; the activity persists
 * them. The overlay clamps itself on size changes, so a saved layout always
 * fits the screen it is drawn on.
 */
class TouchControlsOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Where key events and raw touches go. */
    interface GameInput {
        fun keyDown(code: Int)
        fun keyUp(code: Int)
        fun touchDown(pointerId: Int, x: Float, y: Float)
        fun touchMove(pointerId: Int, x: Float, y: Float)
        fun touchUp(pointerId: Int, x: Float, y: Float)
    }

    var layout: ControlLayout = ControlLayout.defaults()
        set(value) {
            field = value
            invalidate()
        }

    var editing: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                releaseAll()
                selected = null
                invalidate()
            }
        }

    var gameInput: GameInput? = null
    var hapticsEnabled: Boolean = true

    var selected: ButtonId? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** A drag ended, a resize finished, a visibility toggled - persist it. */
    var onLayoutChanged: (() -> Unit)? = null

    private val dp = resources.displayMetrics.density

    // Game mode state: pointers that drive buttons, pointers that are raw.
    private val buttonPointers = HashMap<Int, ButtonId>()  // pointerId -> button
    private val rawPointers = HashSet<Int>()

    // Edit mode state.
    private var dragButton: ButtonId? = null
    private var dragPointer = -1
    private var dragMoved = false
    private var scaling = false
    private var scaleStartSpan = 0f
    private var scaleStartSize = 0
    private var lastTapButton: ButtonId? = null
    private var lastTapTime = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * dp
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * dp
        textAlign = Paint.Align.CENTER
    }
    private val iconCache = HashMap<ButtonId, Drawable>()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layout = layout.clampAll(w, h, dp)
    }

    fun iconResFor(id: ButtonId): Int = when (id) {
        ButtonId.ACCEL -> R.drawable.ic_accel
        ButtonId.BRAKE -> R.drawable.ic_brake
        ButtonId.STEER_LEFT -> R.drawable.ic_left
        ButtonId.STEER_RIGHT -> R.drawable.ic_right
        ButtonId.HANDBRAKE -> R.drawable.ic_handbrake
        ButtonId.MAP -> R.drawable.ic_map
        ButtonId.CAMERA -> R.drawable.ic_camera
        ButtonId.MENU -> R.drawable.ic_menu
    }

    private fun iconFor(id: ButtonId): Drawable {
        var d = iconCache[id]
        if (d == null) {
            d = context.getDrawable(iconResFor(id), context.theme)!!.mutate()
            iconCache[id] = d
        }
        return d
    }

    private fun labelResFor(id: ButtonId): Int = when (id) {
        ButtonId.ACCEL -> R.string.label_accel
        ButtonId.BRAKE -> R.string.label_brake
        ButtonId.HANDBRAKE -> R.string.label_handbrake
        else -> 0
    }

    /** Last button in draw order wins (it is drawn on top). */
    private fun hitTest(x: Float, y: Float, tolerance: Float, includeHidden: Boolean): ButtonId? {
        var best: ButtonId? = null
        for (b in layout.buttons.values) {
            if (!b.visible && !includeHidden) continue
            val r = b.sizeDp * dp / 2f * tolerance
            val dx = x - b.x * width
            val dy = y - b.y * height
            if (dx * dx + dy * dy <= r * r) best = b.id
        }
        return best
    }

    private fun codeFor(id: ButtonId): Int = when (id) {
        ButtonId.ACCEL -> GameCodes.UP
        ButtonId.BRAKE -> GameCodes.DOWN
        ButtonId.STEER_LEFT -> GameCodes.LEFT
        ButtonId.STEER_RIGHT -> GameCodes.RIGHT
        ButtonId.HANDBRAKE -> GameCodes.SPACE
        ButtonId.MAP -> GameCodes.KEY_M
        ButtonId.CAMERA -> GameCodes.KEY_C
        ButtonId.MENU -> GameCodes.ESCAPE
    }

    /** Mutate the layout through a copy, then publish it. */
    private fun withCopy(block: (ControlLayout) -> Unit) {
        val c = layout.copy()
        block(c)
        layout = c
    }

    private fun span(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private fun releaseAll() {
        for (btn in buttonPointers.values) gameInput?.keyUp(codeFor(btn))
        buttonPointers.clear()
        rawPointers.clear()
        dragButton = null
        dragPointer = -1
        dragMoved = false
        scaling = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)
                if (editing) {
                    val hit = hitTest(x, y, 1.4f, includeHidden = true)
                    selected = hit
                    if (hit != null) {
                        val now = SystemClock.uptimeMillis()
                        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                            hit == lastTapButton && now - lastTapTime < 300
                        ) {
                            // Double-tap: hide or show the button.
                            lastTapButton = null
                            withCopy { l ->
                                l.button(hit).visible = !l.button(hit).visible
                            }
                            onLayoutChanged?.invoke()
                            return true
                        }
                        lastTapButton = hit
                        lastTapTime = now
                        if (hit == dragButton && event.pointerCount >= 2) {
                            // A second finger while dragging: pinch to resize.
                            scaling = true
                            scaleStartSpan = span(event)
                            scaleStartSize = layout.button(hit).sizeDp
                        } else {
                            dragButton = hit
                            dragPointer = pid
                            dragMoved = false
                            if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    }
                    return true
                }
                val hit = hitTest(x, y, 1.25f, includeHidden = false)
                if (hit != null) {
                    buttonPointers[pid] = hit
                    gameInput?.keyDown(codeFor(hit))
                    if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                } else {
                    rawPointers.add(pid)
                    gameInput?.touchDown(pid, x, y)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (editing) {
                    val b = dragButton ?: return true
                    if (scaling && event.pointerCount >= 2) {
                        val s = span(event)
                        if (scaleStartSpan > 0f) {
                            val size = (scaleStartSize * s / scaleStartSpan).toInt()
                            withCopy { l -> l.button(b).sizeDp = size }
                            layout = layout.clampAll(width, height, dp)
                            dragMoved = true
                        }
                    } else {
                        val idx = event.findPointerIndex(dragPointer)
                        if (idx >= 0) {
                            val px = event.getX(idx)
                            val py = event.getY(idx)
                            val grid = 8f * dp
                            withCopy { l ->
                                val btn = l.button(b)
                                btn.x = (round(px / grid) * grid / width).toFloat().coerceIn(0f, 1f)
                                btn.y = (round(py / grid) * grid / height).toFloat().coerceIn(0f, 1f)
                            }
                            layout = layout.clampAll(width, height, dp)
                            dragMoved = true
                        }
                    }
                    return true
                }
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val btn = buttonPointers[pid]
                    if (btn != null) {
                        val b = layout.button(btn)
                        val r = b.sizeDp * dp / 2f * 1.6f
                        val dx = event.getX(i) - b.x * width
                        val dy = event.getY(i) - b.y * height
                        if (dx * dx + dy * dy > r * r) {
                            // The finger slid clear of the button.
                            buttonPointers.remove(pid)
                            gameInput?.keyUp(codeFor(btn))
                        }
                    } else if (pid in rawPointers) {
                        gameInput?.touchMove(pid, event.getX(i), event.getY(i))
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                if (editing) {
                    if (scaling && event.pointerCount < 2) scaling = false
                    if (pid == dragPointer) {
                        dragButton = null
                        dragPointer = -1
                        if (dragMoved) onLayoutChanged?.invoke()
                        dragMoved = false
                    }
                    return true
                }
                val btn = buttonPointers.remove(pid)
                if (btn != null) {
                    gameInput?.keyUp(codeFor(btn))
                } else if (rawPointers.remove(pid)) {
                    gameInput?.touchUp(pid, event.getX(idx), event.getY(idx))
                }
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) releaseAll()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        for (b in layout.buttons.values) {
            if (!b.visible && !editing) continue
            val cx = b.x * w
            val cy = b.y * h
            val r = b.sizeDp * dp / 2f
            val isPressed = !editing && buttonPointers.values.contains(b.id)
            val isHidden = !b.visible
            val isSelected = editing && selected == b.id

            var alpha = 0.55f
            if (isPressed) alpha = 0.92f
            if (isHidden) alpha = 0.16f
            if (isSelected && isHidden) alpha = 0.32f

            fill.color = Color.argb((alpha * 255).toInt(), 24, 28, 36)
            canvas.drawCircle(cx, cy, r, fill)
            stroke.color = Color.argb((if (isPressed) 0.9f else 0.4f) * 255, 255, 255, 255)
            canvas.drawCircle(cx, cy, r - 1f, stroke)
            if (isSelected) {
                ring.color = Color.rgb(230, 57, 70)
                canvas.drawCircle(cx, cy, r + 4f * dp, ring)
            }

            val icon = iconFor(b.id)
            val iconSize = r * 0.92f
            val labelOffset = if (b.sizeDp >= 90 && labelResFor(b.id) != 0) 6f * dp else 0f
            icon.alpha = ((if (isHidden) 0.5f else if (isPressed) 1f else 0.85f) * 255).toInt()
            icon.setBounds(
                (cx - iconSize / 2f).toInt(),
                (cy - iconSize / 2f - labelOffset).toInt(),
                (cx + iconSize / 2f).toInt(),
                (cy + iconSize / 2f - labelOffset).toInt()
            )
            icon.draw(canvas)

            val lr = labelResFor(b.id)
            if (b.sizeDp >= 90 && lr != 0) {
                label.alpha = ((if (isHidden) 0.4f else 0.8f) * 255).toInt()
                canvas.drawText(context.getString(lr), cx, cy + r + 16f * dp, label)
            }
        }
    }
}
