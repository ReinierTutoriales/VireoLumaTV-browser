package com.reiniertutoriales.vireolumatv.widgets.cursor

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.utils.RemoteInput

/** Hosts the page and draws the remote-driven pointer above it. */
class PointerLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    /** The user's pointer mode for the page (off = direct D-pad navigation of the page). */
    var pointerEnabled = true
        set(value) {
            if (field == value) return
            field = value
            resetInput()
            invalidate()
        }

    /** Browser chrome owns the remote while it covers the page; [pointerEnabled] is kept. */
    var cursorSuppressed = false
        set(value) {
            if (field == value) return
            field = value
            resetInput()
            invalidate()
        }

    var onPageTouch: (() -> Unit)? = null

    lateinit var pointerController: PointerController

    private val remoteInput = RemoteInput(
        target = object : RemoteInput.Target {
            override fun onKey(event: KeyEvent) = pointerController.onKey(event)
            override fun onStick(x: Float, y: Float) = pointerController.onStick(x, y)
        },
        stickEnabled = { !AppContext.config.ignoreStickAxes },
        // The OK press that focuses an editor must still deliver its release to the page.
        isTyping = { !pointerController.isSelectionPressed && (isTextInputActive() || imeVisible()) },
    )

    init {
        if (!isInEditMode) {
            setWillNotDraw(false)
            pointerController = PointerController(context, this)
        }
    }

    fun isTextInputActive(): Boolean {
        val focused = findFocus() ?: return false
        // WebView can retain an editable DOM focus after the IME is dismissed. That must
        // not redirect remote clicks away from the pointer back into the search field.
        return focused.onCheckIsTextEditor() && (focused !is WebView || imeVisible())
    }

    private fun imeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(rootView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private val pointerActive: Boolean get() = pointerEnabled && !cursorSuppressed

    fun resetInput() {
        remoteInput.resetState()
        if (::pointerController.isInitialized) pointerController.resetInput()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isInEditMode) pointerController.resize(w, h)
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
        if (!isInEditMode && pointerActive && !isTextInputActive()) pointerController.draw(canvas)
    }
}
