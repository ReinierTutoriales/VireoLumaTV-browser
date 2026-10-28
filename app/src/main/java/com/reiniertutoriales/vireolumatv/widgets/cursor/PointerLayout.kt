package com.reiniertutoriales.vireolumatv.widgets.cursor

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.utils.RemoteInput

/** Hosts the page and draws the remote-driven pointer above it. */
class PointerLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    /** The user's pointer mode for the page (off = direct D-pad navigation of the page). */
    var cursorEnabled = true
        set(value) {
            if (field == value) return
            field = value
            resetInput()
            invalidate()
        }

    /** Browser chrome owns the remote while it covers the page; [cursorEnabled] is kept. */
    var cursorSuppressed = false
        set(value) {
            if (field == value) return
            field = value
            resetInput()
            invalidate()
        }

    var onPageTouch: (() -> Unit)? = null

    lateinit var cursorDrawerDelegate: PointerController

    private val remoteInput = RemoteInput(
        target = object : RemoteInput.Target {
            override fun onKey(event: KeyEvent) = cursorDrawerDelegate.dispatchKeyEvent(event)
            override fun onStick(x: Float, y: Float) = cursorDrawerDelegate.onStick(x, y)
        },
        stickEnabled = { !AppContext.config.disableMotionAxesDpadNavigation },
        // The OK press that focuses an editor must still deliver its release to the page.
        isTyping = { !cursorDrawerDelegate.isSelectionPressed && (isTextInputActive() || imeVisible()) },
    )

    init {
        if (!isInEditMode) {
            setWillNotDraw(false)
            cursorDrawerDelegate = PointerController(context, this).apply { init() }
        }
    }

    fun isTextInputActive(): Boolean = findFocus()?.onCheckIsTextEditor() == true

    private fun imeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(rootView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private val pointerActive: Boolean get() = cursorEnabled && !cursorSuppressed

    fun resetInput() {
        remoteInput.resetState()
        if (::cursorDrawerDelegate.isInitialized) cursorDrawerDelegate.resetInput()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isInEditMode) cursorDrawerDelegate.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) resetInput()
    }

    override fun onDetachedFromWindow() {
        resetInput()
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Resume the page before this same DOWN reaches it, so a search field needs no second tap.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onPageTouch?.invoke()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (pointerActive && remoteInput.dispatchKeyEvent(event)) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        (pointerActive && remoteInput.dispatchGenericMotionEvent(event)) || super.dispatchGenericMotionEvent(event)

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!isInEditMode && pointerActive && !isTextInputActive()) cursorDrawerDelegate.dispatchDraw(canvas)
    }
}
