package com.reiniertutoriales.vireolumatv.activity.main.view

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.downloads.ActiveDownloadsModel
import com.reiniertutoriales.vireolumatv.databinding.ViewActionbarBinding
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository

/** One TV command row. Focusing the address does not start editing or open the keyboard. */
class ActionBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    interface Callback {
        fun showAppMenu()
        fun search(text: String)
        fun onUrlInputDone()
        fun onExtendedAddressBarMode() {}
        fun onAddressInputCancelled() {}
        fun onAddressTyped(text: String) {}
        fun focusSuggestions(): Boolean = false
    }

    var callback: Callback? = null
    private val ui = ViewActionbarBinding.inflate(LayoutInflater.from(context), this)
    val backButton: ImageButton get() = ui.ibBack
    val forwardButton: ImageButton get() = ui.ibForward
    val refreshButton: ImageButton get() = ui.ibRefresh
    val homeButton: ImageButton get() = ui.ibHome
    val adBlockButton: ImageButton get() = ui.ibAdBlock
    private val commands = listOf(ui.ibBack, ui.ibForward, ui.ibRefresh, ui.ibHome, ui.ibAdBlock, ui.ibMenu)
    private var pageAddress = ""
    private var editing = false
    private var enteringSuggestions = false
    val isEditingAddress get() = editing
    private val keyboard get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val selectText = Runnable { if (editing && ui.etUrl.hasFocus()) ui.etUrl.selectAll() }

    init {
        orientation = HORIZONTAL
        if (!isInEditMode) {
            ui.ibMenu.setOnClickListener { callback?.showAppMenu() }
            renderEditing(false)
            ui.etUrl.setOnClickListener { beginEditing() }
            ui.etUrl.setOnFocusChangeListener { _, focused ->
                if (!focused && editing && !enteringSuggestions) finishEditing(cancelled = true)
            }
            ui.etUrl.doAfterTextChanged { if (editing) callback?.onAddressTyped(it?.toString().orEmpty()) }
            ui.etUrl.setOnKeyListener { _, code, event -> handleAddressKey(code, event) }
            ui.etUrl.setOnEditorActionListener { _, action, _ ->
                when (action) {
                    EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_DONE -> {
                        confirmAddress()
                        true
                    }
                    else -> false
                }
            }
            val owner = context as AppCompatActivity
            ActiveModelsRepository.get(ActiveDownloadsModel::class, context).activeDownloads.subscribe(owner) {
                val count = it.size
                ui.ibMenu.setImageResource(if (count == 0) R.drawable.ic_more else R.drawable.ic_menu_downloads)
                ui.ibMenu.contentDescription = if (count == 0) context.getString(R.string.nav_menu)
                    else context.getString(R.string.nav_menu_downloads, count)
            }
        }
    }

    private fun renderEditing(value: Boolean) {
        editing = value
        ui.etUrl.isCursorVisible = value
        ui.etUrl.showSoftInputOnFocus = value
        commands.forEach { it.visibility = if (value) GONE else VISIBLE }
    }

    private fun beginEditing() {
        if (editing) return
        ui.etUrl.requestFocus()
        renderEditing(true)
        callback?.onExtendedAddressBarMode()
        keyboard.showSoftInput(ui.etUrl, InputMethodManager.SHOW_IMPLICIT)
        removeCallbacks(selectText)
        postDelayed(selectText, 500)
    }

    private fun finishEditing(cancelled: Boolean) {
        removeCallbacks(selectText)
        keyboard.hideSoftInputFromWindow(ui.etUrl.windowToken, 0)
        renderEditing(false)
        if (cancelled) {
            ui.etUrl.setText(pageAddress)
            callback?.onAddressInputCancelled()
        }
    }

    private fun confirmAddress() {
        if (!editing) return
        val destination = ui.etUrl.text.toString().trim()
        if (destination.isEmpty()) return
        finishEditing(cancelled = false)
        ui.etUrl.clearFocus()
        callback?.onUrlInputDone()
        callback?.search(destination)
    }

    private fun handleAddressKey(code: Int, event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        return when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    if (editing) confirmAddress() else beginEditing()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!editing) false else if (!down) true else {
                    enteringSuggestions = true
                    try { callback?.focusSuggestions() == true } finally { enteringSuggestions = false }
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (editing) false else {
                    if (down && event.repeatCount == 0) backButton.requestFocus()
                    true
                }
            }
            else -> false
        }
    }

    fun submit(text: String) {
        if (editing) {
            ui.etUrl.setText(text)
            confirmAddress()
        }
    }

    fun setAddressBoxText(text: String) {
        pageAddress = text.takeUnless { it == Config.HOME_PAGE_URL }.orEmpty()
        if (!editing) ui.etUrl.setText(pageAddress)
    }

    fun setAddressBoxTextColor(color: Int) = ui.etUrl.setTextColor(color)

    fun dismissExtendedAddressBarMode() {
        if (editing) finishEditing(cancelled = false)
    }

    fun cancelAddressInput() {
        if (editing) finishEditing(cancelled = true)
        ui.etUrl.clearFocus()
        catchFocus()
    }

    fun catchFocus() { backButton.requestFocus() }

    override fun onDetachedFromWindow() {
        removeCallbacks(selectText)
        super.onDetachedFromWindow()
    }
}
