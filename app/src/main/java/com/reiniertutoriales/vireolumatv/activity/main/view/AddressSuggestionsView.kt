package com.reiniertutoriales.vireolumatv.activity.main.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.activity.main.AddressSuggestion
import com.reiniertutoriales.vireolumatv.activity.main.compose.LocalChromeMetrics
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvListRow
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.utils.RemoteInput
import com.reiniertutoriales.vireolumatv.widgets.cursor.PointerController

/**
 * Suggestions under the address while it is edited, drawn in Compose. D-pad Down from the address
 * enters the list ([focusFirst]), Up from the first row returns to it through [onExitUp], and Down
 * stops at the last row so focus never leaves an edit half-way. OK opens the suggestion through [onPick]. Focus
 * follows the focused address when the results change. With the pointer, this view draws its own
 * pointer over the rows ([focusPointer]).
 */
class AddressSuggestionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    var onPick: ((AddressSuggestion) -> Unit)? = null
    /** Returns focus to the address being edited; true when it did. */
    var onExitUp: (() -> Boolean)? = null
    private val pointer = lazy { PointerController(context, this).also { it.resize(width, height) } }
    internal val pointerController by pointer
    private var pointerMode = false
    private val remote = RemoteInput(
        target = object : RemoteInput.Target {
            override fun onKey(event: KeyEvent) = pointerController.onKey(event)
            override fun onStick(x: Float, y: Float) = pointerController.onStick(x, y)
        },
        stickEnabled = { !AppContext.config.ignoreStickAxes },
        isTyping = { false },
    )
    private var shown by mutableStateOf<List<AddressSuggestion>>(emptyList())
    private var requesters: List<FocusRequester> = emptyList()
    /** The address of the focused row, so focus follows it when the results change. */
    private var focusedUrl: String? = null
    /** A row to focus once the new results are composed; -1 for none. */
    private var pendingFocus by mutableIntStateOf(-1)

    init {
        isFocusableInTouchMode = true
        isVisible = false
        addView(ComposeView(context).apply { setContent { VireoTvTheme { Rows() } } })
    }

    val hasSuggestions: Boolean get() = shown.isNotEmpty()

    fun show(suggestions: List<AddressSuggestion>) {
        if (suggestions == shown) return
        val keepFocus = hasFocus()
        val focusedIndex = shown.indexOfFirst { it.url == focusedUrl }.coerceAtLeast(0)
        shown = suggestions
        requesters = List(suggestions.size) { FocusRequester() }
        isVisible = suggestions.isNotEmpty()
        if (suggestions.isEmpty()) stopPointer()
        else if (keepFocus) {
            if (pointerMode) requestFocus() else {
                pendingFocus = suggestions.indexOfFirst { it.url == focusedUrl }.takeIf { it >= 0 }
                    ?: focusedIndex.coerceAtMost(suggestions.lastIndex)
            }
        }
    }

    fun clear() = show(emptyList())

    /** Moves focus to the first suggestion; false when there is none. */
    fun focusFirst(): Boolean {
        stopPointer()
        val first = requesters.firstOrNull() ?: return false
        return runCatching { first.requestFocus() }.isSuccess
    }

    /** A separate pointer surface over the rows; the page's hidden pointer stays suppressed. */
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

    @Composable
    private fun Rows() {
        val items = shown
        val focus = requesters
        // The panel spans the window; its rows line up with the address, inside the safe margins.
        val metrics = LocalChromeMetrics.current
        Column(Modifier.semantics { testTagsAsResourceId = true }.padding(horizontal = metrics.edge, vertical = 12.dp)) {
            items.forEachIndexed { index, suggestion ->
                TvListRow(
                    title = suggestion.title,
                    supporting = suggestion.url.takeIf { it != suggestion.title },
                    symbol = if (suggestion.bookmark) TvSymbol.Bookmark else TvSymbol.History,
                    onClick = { onPick?.invoke(suggestion) },
                    modifier = Modifier.padding(bottom = 2.dp).testTag("suggestion_${suggestion.url}")
                        .focusRequester(focus[index])
                        .onFocusChanged { if (it.isFocused) focusedUrl = suggestion.url }
                        .onPreviewKeyEvent { event ->
                            when {
                                // Down stops at the last row, so focus never leaves an edit half-way.
                                index == items.lastIndex && event.key == Key.DirectionDown -> true
                                // Up from the first row goes back to the address, not to the row above it.
                                index == 0 && event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown ->
                                    onExitUp?.invoke() == true
                                else -> false
                            }
                        },
                )
            }
        }
        LaunchedEffect(items, pendingFocus) {
            val target = pendingFocus
            if (target in focus.indices) runCatching { focus[target].requestFocus() }
            pendingFocus = -1
        }
    }

    private fun stopPointer() {
        pointerMode = false
        remote.resetState()
        if (!isInEditMode && pointer.isInitialized()) pointerController.resetInput()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isInEditMode && pointer.isInitialized()) pointerController.resize(w, h)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (pointerMode && hasFocus()) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN &&
                event.repeatCount == 0 && pointerController.position.y <= 12 * resources.displayMetrics.density) {
                stopPointer()
                if (onExitUp?.invoke() != true) focusSearch(View.FOCUS_UP)?.requestFocus()
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
}
