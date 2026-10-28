package com.reiniertutoriales.vireolumatv.activity.main.view

import android.content.Context
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
    private var shown: List<AddressSuggestion> = emptyList()

    init {
        orientation = VERTICAL
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
                if (suggestion.bookmark) R.drawable.ic_suggestion_bookmark else R.drawable.ic_suggestion_history)
            row.findViewById<TextView>(R.id.tvTitle).text = suggestion.title
            row.findViewById<TextView>(R.id.tvUrl).apply {
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
        if (keepFocus) focusFirst()
    }

    fun clear() = show(emptyList())

    /** Moves focus to the first suggestion; false when there is none. */
    fun focusFirst(): Boolean = getChildAt(0)?.requestFocus() == true

    internal fun row(index: Int): View? = getChildAt(index)
}
