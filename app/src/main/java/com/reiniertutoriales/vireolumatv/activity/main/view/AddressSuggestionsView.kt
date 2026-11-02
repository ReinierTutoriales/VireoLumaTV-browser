package com.reiniertutoriales.vireolumatv.activity.main.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.utils.RemoteInput
import com.reiniertutoriales.vireolumatv.widgets.cursor.PointerController
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.AddressSuggestion

/**
 * Suggestions under the address while it is edited. D-pad Down from the address enters the list
 * ([focusFirst]), Up from the first row returns to it, and Down stops at the last row so focus
 * never leaves an edit half-way. OK opens the suggestion through [onPick].
 */
class AddressSuggestionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    var onPick: ((AddressSuggestion) -> Unit)? = null
    internal val pointerController by lazy { PointerController(context, this) }
    private var pointerMode = false
    private val remote = RemoteInput(
        target = object : RemoteInput.Target {
            override fun onKey(event: KeyEvent) = pointerController.onKey(event)
            override fun onStick(x: Float, y: Float) = pointerController.onStick(x, y)
        },
        stickEnabled = { !AppContext.config.ignoreStickAxes },
        isTyping = { false },
    )
    private var shown: List<AddressSuggestion> = emptyList()

    init {
        orientation = VERTICAL
        isFocusableInTouchMode = true
    }

    val hasSuggestions: Boolean get() = shown.isNotEmpty()

    fun show(suggestions: List<AddressSuggestion>) {
        if (suggestions == shown) return
        val keepFocus = hasFocus()
        shown = suggestions
        removeAllViews()
        val inflater = LayoutInflater.from(context)
        suggestions.forEachIndexed { index, suggestion ->
            val row = inflater.inflate(R.layout.view_address_suggestion, this, false)
            row.findViewById<ImageView>(R.id.ivKind).setImageResource(
                if (suggestion.bookmark) R.drawable.ic_bookmark else R.drawable.ic_history)
            row.findViewById<TextView>(R.id.title_text).text = suggestion.title
            row.findViewById<TextView>(R.id.address_text).apply {
                text = suggestion.url
                isVisible = suggestion.url != suggestion.title
            }
            row.setOnClickListener { onPick?.invoke(suggestion) }
            if (index == suggestions.lastIndex) {
                row.setOnKeyListener { _, code, _ -> code == KeyEvent.KEYCODE_DPAD_DOWN }
            }
            addView(row)
        }
        isVisible = suggestions.isNotEmpty()
        if (suggestions.isEmpty()) stopPointer()
        else if (keepFocus) {
            if (pointerMode) requestFocus() else focusFirst()
        }
    }

    fun clear() = show(emptyList())

    /** Moves focus to the first suggestion; false when there is none. */
    fun focusFirst(): Boolean {
        stopPointer()
        return getChildAt(0)?.requestFocus() == true
    }

    /** A separate pointer surface over native rows; the page's hidden pointer stays suppressed. */
    fun focusPointer(): Boolean {
        if (!hasSuggestions) return false
        pointerMode = true
        val focused = requestFocus()
        if (focused) {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(windowToken, 0)
            pointerController.fadeIn()
        } else stopPointer()
        return focused
    }

    private fun stopPointer() {
        pointerMode = false
        remote.resetState()
        if (!isInEditMode) pointerController.resetInput()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isInEditMode) pointerController.resize(w, h)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (pointerMode && hasFocus()) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN &&
                event.repeatCount == 0 && pointerController.position.y <= 12 * resources.displayMetrics.density) {
                stopPointer()
                rootView.findViewById<View>(R.id.address_field)?.requestFocus()
                return true
            }
            if (remote.dispatchKeyEvent(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        (pointerMode && hasFocus() && remote.dispatchGenericMotionEvent(event)) || super.dispatchGenericMotionEvent(event)

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (pointerMode && hasFocus()) pointerController.draw(canvas)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus && pointerMode) stopPointer()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) stopPointer()
    }

    override fun onDetachedFromWindow() {
        stopPointer()
        super.onDetachedFromWindow()
    }

    internal fun row(index: Int): View? = getChildAt(index)
}
