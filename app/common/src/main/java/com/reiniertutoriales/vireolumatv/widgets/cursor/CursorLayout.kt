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
import com.reiniertutoriales.vireolumatv.utils.DPADNavigationEventsAdapter


/**
 * Created by PDT on 25.08.2016.
 */
class CursorLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null):
    FrameLayout(context, attrs) {
    // Drawing flags are framework optimizations, not navigation state.
    var cursorEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            inputEventsAdapter.resetState()
            if (::cursorDrawerDelegate.isInitialized) cursorDrawerDelegate.resetInput()
            invalidate()
        }
    // Chrome owns remote input while it overlays the live page. Preserve the user's cursor mode.
    var cursorSuppressed: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            resetInput()
            invalidate()
        }
    var onPageTouch: (() -> Unit)? = null
    fun isTextInputActive(): Boolean = findFocus()?.onCheckIsTextEditor() == true

    lateinit var cursorDrawerDelegate: CursorDrawerDelegate
    private val inputEventsAdapter = DPADNavigationEventsAdapter(
        onEmulatedKeyEvent = { keyEvent ->
            cursorDrawerDelegate.dispatchKeyEvent(keyEvent)
        },
        motionAxesTranslationEnabled = { !AppContext.provideConfig().disableMotionAxesDpadNavigation },
        isSoftwareKeyboardVisible = {
            isTextInputActive() || ViewCompat.getRootWindowInsets(rootView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        },
    )

    init {
        init()
    }

    private fun init() {
        if (isInEditMode) {
            return
        }
        setWillNotDraw(false)
        cursorDrawerDelegate = CursorDrawerDelegate(context, this)
        cursorDrawerDelegate.init()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (isInEditMode) {
            return
        }
        cursorDrawerDelegate.onSizeChanged(w, h, ow, oh)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) resetInput()
    }

    override fun onDetachedFromWindow() {
        resetInput()
        super.onDetachedFromWindow()
    }

    fun resetInput() {
        inputEventsAdapter.resetState()
        if (::cursorDrawerDelegate.isInitialized) cursorDrawerDelegate.resetInput()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Resume the page before forwarding this same DOWN. A search field needs no second click.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onPageTouch?.invoke()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {

        if (!cursorEnabled || cursorSuppressed) return super.dispatchKeyEvent(event)

        if (inputEventsAdapter.dispatchKeyEvent(event)) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {

        if (!cursorEnabled || cursorSuppressed) return super.dispatchGenericMotionEvent(event)

        if (inputEventsAdapter.dispatchGenericMotionEvent(event)) {
            return true
        }

        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isInEditMode || !cursorEnabled || cursorSuppressed || isTextInputActive()) {
            return
        }

        cursorDrawerDelegate.dispatchDraw(canvas)
    }
}
