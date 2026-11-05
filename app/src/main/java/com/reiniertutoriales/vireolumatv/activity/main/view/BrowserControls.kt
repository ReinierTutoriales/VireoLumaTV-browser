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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvConnectedBrowserCommandBar
import androidx.core.widget.doAfterTextChanged
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.downloads.TransferBoard
import com.reiniertutoriales.vireolumatv.databinding.BrowserControlsBinding
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.utils.openKeyboard

/** One TV command row. Focusing the address does not start editing or open the keyboard. */
class BrowserControls @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    interface Callback {
        fun showAppMenu()
        fun search(text: String)
        fun onUrlInputDone()
        fun onAddressEditing() {}
        fun onAddressInputCancelled() {}
        fun onAddressTyped(text: String) {}
        fun focusSuggestions(): Boolean = false
        fun suggestionsHaveFocus(): Boolean = false
    }

    var callback: Callback? = null
    /** Shared commands can be used by either View or Compose without hidden button proxies. */
    data class CommandActions(
        val back: () -> Unit,
        val forward: () -> Unit,
        val reload: () -> Unit,
        val home: () -> Unit,
        val shield: () -> Unit,
        val shieldDetails: () -> Unit,
        val menu: () -> Unit,
    )
    var commandActions by mutableStateOf<CommandActions?>(null)
    private val ui = BrowserControlsBinding.inflate(LayoutInflater.from(context), this)
    val backButton: ImageButton get() = ui.backButton
    val forwardButton: ImageButton get() = ui.forwardButton
    val refreshButton: ImageButton get() = ui.reloadButton
    val homeButton: ImageButton get() = ui.homeButton
    val adBlockButton: ImageButton get() = ui.blockerButton
    private val commands = listOf(ui.backButton, ui.forwardButton, ui.reloadButton, ui.homeButton, ui.blockerButton, ui.menuButton)
    private val composeRow = ComposeView(context)
    private val backAvailable = mutableStateOf(false)
    private val forwardAvailable = mutableStateOf(false)
    private val blockerActive = mutableStateOf(false)
    private val focusRow = listOf<View>(ui.addressField, composeRow)
    private var pageAddress = ""
    private var editing = false
    private var enteringSuggestions = false
    val isEditingAddress get() = editing
    private val keyboard get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val selectText = Runnable { if (editing && ui.addressField.hasFocus()) ui.addressField.selectAll() }

    private val checkAddressFocus = Runnable {
        if (editing && !ui.addressField.hasFocus() && callback?.suggestionsHaveFocus() != true)
            finishEditing(cancelled = true)
    }

    init {
        orientation = HORIZONTAL
        if (!isInEditMode) {
            ui.blockerButton.imageTintList = context.getColorStateList(R.color.blocker_icon)
            showBlockerEnabled(AppContext.config.adBlockEnabled)
            ui.menuButton.setOnClickListener { commandActions?.menu?.invoke() ?: callback?.showAppMenu() }
            renderEditing(false)
            composeRow.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            composeRow.setContent {
                commandActions?.let { actions ->
                    TvConnectedBrowserCommandBar(
                        actions = actions,
                        canGoBack = backAvailable.value,
                        canGoForward = forwardAvailable.value,
                        blockerEnabled = blockerActive.value,
                    )
                }
            }
            addView(composeRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            ui.addressField.setOnClickListener { beginEditing() }
            ui.addressField.setOnFocusChangeListener { _, focused ->
                if (!focused && editing && !enteringSuggestions) {
                    removeCallbacks(checkAddressFocus)
                    post(checkAddressFocus)
                }
            }
            ui.addressField.doAfterTextChanged { if (editing) callback?.onAddressTyped(it?.toString().orEmpty()) }
            ui.addressField.setOnKeyListener { _, code, event -> handleAddressKey(code, event) }
            ui.addressField.setOnEditorActionListener { _, action, _ ->
                when (action) {
                    EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_DONE -> {
                        confirmAddress()
                        true
                    }
                    else -> false
                }
            }
            val owner = context as AppCompatActivity
            ScreenModels.get(TransferBoard::class, context).transfers.observe(owner) {
                val count = it.size
                ui.menuButton.setImageResource(if (count == 0) R.drawable.ic_more else R.drawable.ic_menu_downloads)
                ui.menuButton.contentDescription = if (count == 0) context.getString(R.string.nav_menu)
                    else context.resources.getQuantityString(R.plurals.nav_menu_downloads, count, count)
            }
        }
    }

    fun showBlockerEnabled(enabled: Boolean) {
        blockerActive.value = enabled
        if (adBlockButton.isSelected == enabled) return
        adBlockButton.isSelected = enabled
        adBlockButton.setImageResource(if (enabled) R.drawable.ic_shield_on else R.drawable.ic_shield_off)
    }

    private fun renderEditing(value: Boolean) {
        editing = value
        ui.addressField.isCursorVisible = value
        ui.addressField.showSoftInputOnFocus = value
        val targetVisibility = if (value) GONE else VISIBLE
        commands.forEach { it.visibility = GONE }
        composeRow.visibility = targetVisibility
    }

    private fun beginEditing() {
        if (editing) return
        ui.addressField.requestFocus()
        renderEditing(true)
        callback?.onAddressEditing()
        ui.addressField.openKeyboard()
        removeCallbacks(selectText)
        // Select on the next UI traversal instead of waiting a fixed half-second.
        post(selectText)
    }

    private fun finishEditing(cancelled: Boolean) {
        removeCallbacks(selectText)
        keyboard.hideSoftInputFromWindow(ui.addressField.windowToken, 0)
        renderEditing(false)
        if (cancelled) {
            ui.addressField.setText(pageAddress)
            callback?.onAddressInputCancelled()
        }
    }

    private fun confirmAddress() {
        if (!editing) return
        val destination = ui.addressField.text.toString().trim()
        if (destination.isEmpty()) return
        finishEditing(cancelled = false)
        ui.addressField.clearFocus()
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
                    if (down && event.repeatCount == 0) composeRow.requestFocus()
                    true
                }
            }
            else -> false
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!editing && (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            val current = focusRow.indexOfFirst { it.hasFocus() }
            if (current >= 0) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val towardsEnd = (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != (layoutDirection == LAYOUT_DIRECTION_RTL)
                    val step = if (towardsEnd) 1 else -1
                    var next = current + step
                    while (next in focusRow.indices) {
                        val target = focusRow[next]
                        if (target.isEnabled && target.visibility == VISIBLE && target.isFocusable) {
                            target.requestFocus()
                            break
                        }
                        next += step
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun submit(text: String) {
        if (editing) {
            ui.addressField.setText(text)
            confirmAddress()
        }
    }

    fun showAddress(text: String) {
        pageAddress = text.takeUnless { it == Config.HOME_PAGE_URL }.orEmpty()
        if (!editing && ui.addressField.text.toString() != pageAddress) ui.addressField.setText(pageAddress)
    }

    fun tintAddress(color: Int) = ui.addressField.setTextColor(color)

    fun closeAddressEditing() {
        if (editing) finishEditing(cancelled = false)
    }

    fun cancelAddressInput() {
        if (editing) finishEditing(cancelled = true)
        ui.addressField.clearFocus()
        takeFocus()
    }

    fun updateNavigation(canBack: Boolean, canForward: Boolean) {
        backAvailable.value = canBack
        forwardAvailable.value = canForward
    }

    fun takeFocus() {
        if (!editing && composeRow.requestFocus()) return
        for (command in commands) {
            if (command.isEnabled && command.visibility == VISIBLE && command.requestFocus()) return
        }
        ui.addressField.requestFocus()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(selectText)
        removeCallbacks(checkAddressFocus)
        super.onDetachedFromWindow()
    }
}
