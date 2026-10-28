package com.reiniertutoriales.vireolumatv.widgets.cursor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewConfigurationCompat
import com.reiniertutoriales.vireolumatv.AppContext
import kotlin.math.abs

/**
 * Page pointer driven by the remote. D-pad keys accelerate it, the analog stick sets its speed
 * directly ([onStick]), OK taps the page as a finger, and moving against an edge scrolls the
 * content under the pointer with mouse-wheel events. Hover events follow the pointer so pages
 * react to it as to a mouse.
 */
class PointerController(val context: Context, val surface: View) {
    val cursorPosition = PointF(0f, 0f)
    var callback: Callback? = null
    val isSelectionPressed: Boolean get() = pressed

    interface Callback {
        fun onLongPress(x: Int, y: Int)
    }

    // Sizes scale with the display width so the pointer looks the same on 720p and 4K panels.
    private var radius = 0f
    private var pressedRadius = 0f
    private var strokeWidth = 0f
    private var speedBaseline = 0f
    private var edgeZone = 0
    private var appearScale = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Movement. Keys give a direction that accelerates; the stick gives a speed fraction.
    private var keyDirX = 0
    private var keyDirY = 0
    private var stickX = 0f
    private var stickY = 0f
    private var speedX = 0f
    private var speedY = 0f
    private var speedCap = 0f
    private var accelerationScale = 1f
    // Monotonic clock: wall-clock changes (NTP sync on TV boxes) must not freeze or hide the pointer.
    private var lastMove = SystemClock.uptimeMillis() - HIDE_AFTER_MS

    // Finger gesture for OK, long press and drag (grab mode).
    private var pressed = false
    private var dragScroll = false
    private var touchDownTime = 0L
    private var hovering = false

    private val wheelUnitX: Float
    private val wheelUnitY: Float
    private val mainThread = Handler(Looper.getMainLooper())
    private val redrawOnHide = Runnable { surface.invalidate() }

    init {
        val configuration = ViewConfiguration.get(context)
        wheelUnitX = ViewConfigurationCompat.getScaledHorizontalScrollFactor(configuration, context)
        wheelUnitY = ViewConfigurationCompat.getScaledVerticalScrollFactor(configuration, context)
    }

    private val hidden: Boolean get() = SystemClock.uptimeMillis() - lastMove > HIDE_AFTER_MS

    fun init() {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        radius = width / 100f
        pressedRadius = radius - 5f * metrics.density
        strokeWidth = width / 400f
        speedBaseline = width / 25f
        edgeZone = (width / 15f).toInt()
    }

    fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cursorPosition.set(w / 2f, h / 2f)
        scheduleHide()
    }

    fun canGoBackInPage(): Boolean = dragScroll

    fun consumeBack() {
        if (dragScroll) stopDragging()
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val (dx, dy) = DIRECTIONS[event.keyCode] ?: return if (event.keyCode in SELECTION_KEYS) {
            onSelection(event)
            true
        } else false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> onDirectionDown(event, dx, dy)
            KeyEvent.ACTION_UP -> onDirectionUp(event, dx, dy)
        }
        return true
    }

    private fun onDirectionDown(event: KeyEvent, dx: Int, dy: Int) {
        lastMove = SystemClock.uptimeMillis()
        if (surface.keyDispatcherState.isTracking(event)) return
        surface.keyDispatcherState.startTracking(event, this)
        // An axis the key does not use keeps its current direction, so two held keys combine.
        if (dx != 0 || dy == 0) keyDirX = dx
        if (dy != 0 || dx == 0) keyDirY = dy
        startMoving()
    }

    private fun onDirectionUp(event: KeyEvent, dx: Int, dy: Int) {
        lastMove = SystemClock.uptimeMillis()
        surface.keyDispatcherState.handleUpEvent(event)
        speedX = 0f
        speedY = 0f
        if (dx != 0) keyDirX = 0
        if (dy != 0) keyDirY = 0
    }

    /** Analog speed in -1..1 per axis, already outside the stick's dead zone; 0,0 releases it. */
    fun onStick(x: Float, y: Float) {
        val wasIdle = stickX == 0f && stickY == 0f
        stickX = x
        stickY = y
        lastMove = SystemClock.uptimeMillis()
        if (x == 0f && y == 0f) {
            speedX = 0f // A centred stick stops at once, like a released mouse.
            speedY = 0f
        } else if (wasIdle) startMoving()
    }

    private fun startMoving() {
        val config = AppContext.config
        speedCap = speedBaseline * config.pointerSpeedPercent / 100f
        accelerationScale = config.pointerAccelPercent / 100f
        surface.removeCallbacks(pointerTick)
        surface.post(pointerTick)
    }

    private fun onSelection(event: KeyEvent) {
        // OK, Enter and gamepad A may all arrive for one click; key tracking makes it one tap.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            !surface.keyDispatcherState.isTracking(event)) {
            if (dragScroll) {
                stopDragging()
                return
            }
            surface.keyDispatcherState.startTracking(event, this)
            if (!hidden && !pressed) {
                pressed = true
                touch(MotionEvent.ACTION_DOWN)
                surface.postInvalidate()
                surface.postDelayed(holdTimeout, LONG_PRESS_MS)
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            surface.keyDispatcherState.handleUpEvent(event)
            surface.removeCallbacks(holdTimeout)
            when {
                dragScroll -> Unit
                hidden -> { // The first OK after the pointer faded only shows it again.
                    lastMove = SystemClock.uptimeMillis()
                    scheduleHide()
                }
                pressed -> {
                    touch(MotionEvent.ACTION_UP)
                    pressed = false
                }
            }
            surface.postInvalidate()
        }
    }

    private val holdTimeout = Runnable {
        // End the finger before reporting, so WebView never sees a second DOWN while one is active.
        if (pressed) {
            touch(MotionEvent.ACTION_CANCEL)
            pressed = false
        }
        surface.keyDispatcherState.reset(this)
        dragScroll = false
        callback?.onLongPress(cursorPosition.x.toInt(), cursorPosition.y.toInt())
    }

    /** One display frame of movement; reschedules itself until the pointer is at rest. */
    private val pointerTick = object : Runnable {
        override fun run() {
            mainThread.removeCallbacks(redrawOnHide)
            val now = SystemClock.uptimeMillis()
            // Bounded so a long GC or renderer stall does not turn into a jump.
            val elapsed = (now - lastMove).coerceIn(1L, MAX_FRAME_MS)
            lastMove = now
            // Speeds are px per 60 Hz frame; scaling by real frame time keeps 30 fps TVs as fast.
            val frames = elapsed / REFERENCE_FRAME_MS
            if (stickX != 0f || stickY != 0f) {
                speedX = stickX * speedCap
                speedY = stickY * speedCap
            } else {
                val step = ACCELERATION * accelerationScale * elapsed
                speedX = (speedX + keyDirX * step).coerceIn(-speedCap, speedCap)
                speedY = (speedY + keyDirY * step).coerceIn(-speedCap, speedCap)
                if (abs(speedX) < MIN_SPEED) speedX = 0f
                if (abs(speedY) < MIN_SPEED) speedY = 0f
            }
            if (speedX == 0f && speedY == 0f && keyDirX == 0 && keyDirY == 0) {
                scheduleHide()
                return
            }
            val oldX = cursorPosition.x
            val oldY = cursorPosition.y
            cursorPosition.x = (oldX + speedX * frames).coerceIn(0f, surface.width - 1f)
            cursorPosition.y = (oldY + speedY * frames).coerceIn(0f, surface.height - 1f)
            val moved = oldX != cursorPosition.x || oldY != cursorPosition.y
            if (moved) surface.removeCallbacks(holdTimeout)
            when {
                pressed -> if (moved) touch(MotionEvent.ACTION_MOVE) // Drag the held finger.
                dragScroll -> if (moved) dragPage(cursorPosition.x - oldX, cursorPosition.y - oldY)
                else -> {
                    if (moved) hover()
                    scrollAtEdge(frames)
                }
            }
            surface.invalidate()
            // Once per display frame; a tight Handler loop would compete with video rendering.
            surface.postOnAnimation(this)
        }
    }

    /** Pushing against an edge scrolls whatever is under the pointer, like a mouse wheel. */
    private fun scrollAtEdge(frames: Float) {
        val x = cursorPosition.x
        val y = cursorPosition.y
        val dx = when {
            speedX > 0 && x > surface.width - edgeZone -> speedX * frames
            speedX < 0 && x < edgeZone -> speedX * frames
            else -> 0f
        }
        val dy = when {
            speedY > 0 && y > surface.height - edgeZone -> speedY * frames
            speedY < 0 && y < edgeZone -> speedY * frames
            else -> 0f
        }
        if (dx == 0f && dy == 0f) return
        // Positive VSCROLL scrolls up and positive HSCROLL scrolls right (MotionEvent axes).
        mouse(MotionEvent.ACTION_SCROLL) {
            setAxisValue(MotionEvent.AXIS_HSCROLL, dx / wheelUnitX)
            setAxisValue(MotionEvent.AXIS_VSCROLL, -dy / wheelUnitY)
        }
    }

    /** Grab mode: the content follows the pointer, as when dragging it with a finger. */
    private fun dragPage(dx: Float, dy: Float) = mouse(MotionEvent.ACTION_SCROLL) {
        setAxisValue(MotionEvent.AXIS_HSCROLL, -dx / wheelUnitX)
        setAxisValue(MotionEvent.AXIS_VSCROLL, dy / wheelUnitY)
    }

    private fun hover() {
        if (!hovering) {
            hovering = true
            mouse(MotionEvent.ACTION_HOVER_ENTER)
        }
        mouse(MotionEvent.ACTION_HOVER_MOVE)
    }

    private fun endHover() {
        if (!hovering) return
        hovering = false
        mouse(MotionEvent.ACTION_HOVER_EXIT)
    }

    private fun mouse(action: Int, axes: MotionEvent.PointerCoords.() -> Unit = {}) {
        val now = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val coords = MotionEvent.PointerCoords().apply {
            x = cursorPosition.x
            y = cursorPosition.y
            axes()
        }
        val event = MotionEvent.obtain(now, now, action, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        try { surface.dispatchGenericMotionEvent(event) } finally { event.recycle() }
    }

    /** A finger at the pointer: clicks stay touch taps, the most compatible input for web pages. */
    private fun touch(action: Int) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) touchDownTime = now
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply {
            x = cursorPosition.x
            y = cursorPosition.y
            pressure = 1f
            size = 1f
        }
        val event = MotionEvent.obtain(touchDownTime, now, action, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { surface.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    fun dispatchDraw(canvas: Canvas) {
        if (!dragScroll && hidden) return
        val x = cursorPosition.x
        val y = cursorPosition.y
        val r = if (pressed) pressedRadius else radius * appearScale
        paint.style = Paint.Style.FILL
        paint.color = if (dragScroll) GRAB_FILL else FILL
        canvas.drawCircle(x, y, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = Color.GRAY
        canvas.drawCircle(x, y, r, paint)
        if (dragScroll) { // A cross marks that moving now drags the page.
            canvas.drawLine(x - r / 2, y, x + r / 2, y, paint)
            canvas.drawLine(x, y - r / 2, x, y + r / 2, paint)
        }
    }

    fun startDragging() {
        dragScroll = true
        surface.postInvalidate()
    }

    fun stopDragging() {
        touch(MotionEvent.ACTION_UP)
        pressed = false
        dragScroll = false
        surface.postInvalidate()
    }

    fun fadeIn() {
        lastMove = SystemClock.uptimeMillis()
        scheduleHide()
        ValueAnimator.ofFloat(2f, 1f).apply {
            duration = APPEAR_MS
            addUpdateListener {
                appearScale = it.animatedValue as Float
                surface.postInvalidate()
            }
            start()
        }
    }

    /** Stop held keys and gestures when a menu, dialog or another tab takes the input. */
    fun resetInput() {
        surface.removeCallbacks(pointerTick)
        surface.removeCallbacks(holdTimeout)
        mainThread.removeCallbacks(redrawOnHide)
        if (pressed) touch(MotionEvent.ACTION_CANCEL)
        endHover()
        pressed = false
        dragScroll = false
        keyDirX = 0
        keyDirY = 0
        stickX = 0f
        stickY = 0f
        speedX = 0f
        speedY = 0f
        surface.keyDispatcherState?.reset(this)
    }

    fun hideCursor() {
        resetInput()
        lastMove = SystemClock.uptimeMillis() - HIDE_AFTER_MS - 1L
        surface.postInvalidate()
    }

    private fun scheduleHide() {
        mainThread.removeCallbacks(redrawOnHide)
        mainThread.postDelayed(redrawOnHide, HIDE_AFTER_MS)
    }

    private companion object {
        const val HIDE_AFTER_MS = 5000L
        const val REFERENCE_FRAME_MS = 1000f / 60f
        const val MAX_FRAME_MS = 50L
        const val ACCELERATION = 0.05f // px per frame gained per ms a key is held, at 100 %
        const val MIN_SPEED = 0.1f
        const val APPEAR_MS = 300L
        val FILL = Color.argb(128, 255, 255, 255)
        val GRAB_FILL = Color.argb(128, 200, 200, 255)
        // 100 ms past the system long-press timeout lets the page handle its own long press first.
        val LONG_PRESS_MS = ViewConfiguration.getLongPressTimeout() + 100L
        val SELECTION_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A)
        val DIRECTIONS = mapOf(
            KeyEvent.KEYCODE_DPAD_LEFT to (-1 to 0), KeyEvent.KEYCODE_DPAD_RIGHT to (1 to 0),
            KeyEvent.KEYCODE_DPAD_UP to (0 to -1), KeyEvent.KEYCODE_DPAD_DOWN to (0 to 1),
            KeyEvent.KEYCODE_DPAD_UP_LEFT to (-1 to -1), KeyEvent.KEYCODE_DPAD_UP_RIGHT to (1 to -1),
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT to (-1 to 1), KeyEvent.KEYCODE_DPAD_DOWN_RIGHT to (1 to 1),
        )
    }
}
