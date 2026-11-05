package com.reiniertutoriales.vireolumatv.activity.main.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvConnectedBrowserCommandBar

/**
 * Native Compose TV toolbar. No XML inflation, hidden buttons, or legacy EditText.
 * The existing browser owns navigation, WebView, tabs, and blocking.
 */
class BrowserControls @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val composeView = ComposeView(context)
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

    data class CommandActions(
        val back: () -> Unit,
        val forward: () -> Unit,
        val reload: () -> Unit,
        val home: () -> Unit,
        val shield: () -> Unit,
        val shieldDetails: () -> Unit,
        val menu: () -> Unit,
    )

    /** Immutable observable contract for JVM tests and the Compose presentation. */
    data class ToolbarSnapshot(
        val address: String,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
        val blockerEnabled: Boolean,
        val blockerDescription: String,
        val blockerReady: Boolean,
        val isEditingAddress: Boolean,
    )

    val snapshot: ToolbarSnapshot
        get() = ToolbarSnapshot(
            address = address.value,
            canGoBack = canBack.value,
            canGoForward = canForward.value,
            blockerEnabled = blockerEnabled.value,
            blockerDescription = blockerDescription.value,
            blockerReady = blockerReady.value,
            isEditingAddress = editingState.value,
        )

    var callback: Callback? = null
    private val actionsState = mutableStateOf<CommandActions?>(null)
    var commandActions: CommandActions?
        get() = actionsState.value
        set(value) { actionsState.value = value }

    private val address = mutableStateOf("")
    private val canBack = mutableStateOf(false)
    private val canForward = mutableStateOf(false)
    private val blockerEnabled = mutableStateOf(false)
    private val blockerDescription = mutableStateOf("")
    private val blockerReady = mutableStateOf(false)
    private val focusRequest = mutableStateOf(0)
    private val cancelRequest = mutableStateOf(0)
    private val submitRequest = mutableStateOf(0 to "")
    private val editingState = mutableStateOf(false)
    private val addressSubmitHandler: (String) -> Unit = { target ->
        callback?.onUrlInputDone()
        callback?.search(target)
    }
    val isEditingAddress: Boolean get() = editingState.value

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        blockerEnabled.value = AppContext.config.adBlockEnabled
        composeView.setContent {
            actionsState.value?.let { actions ->
                TvConnectedBrowserCommandBar(
                    actions = actions,
                    address = address.value,
                    onSubmitAddress = addressSubmitHandler,
                    onCancelAddress = { callback?.onAddressInputCancelled() },
                    onAddressDraftChanged = { callback?.onAddressTyped(it) },
                    onAddressEditingChanged = { active ->
                        editingState.value = active
                        if (active) callback?.onAddressEditing()
                    },
                    onFocusSuggestions = { callback?.focusSuggestions() == true },
                    cancelAddressRequest = cancelRequest.value,
                    submitAddressRequest = submitRequest.value,
                    canGoBack = canBack.value,
                    canGoForward = canForward.value,
                    blockerEnabled = blockerEnabled.value,
                    blockerDescription = blockerDescription.value,
                    blockerReady = blockerReady.value,
                    focusRequest = focusRequest.value,
                )
            }
        }
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun showAddress(text: String) {
        address.value = text.takeUnless { it == Config.HOME_PAGE_URL }.orEmpty()
    }

    fun updateNavigation(back: Boolean, forward: Boolean) {
        canBack.value = back
        canForward.value = forward
    }

    fun showBlockerEnabled(enabled: Boolean) {
        blockerEnabled.value = enabled
    }

    fun updateBlockerStatus(description: String, ready: Boolean) {
        blockerDescription.value = description
        blockerReady.value = ready
    }

    fun submit(text: String) {
        if (editingState.value) submitRequest.value = (submitRequest.value.first + 1) to text
    }

    fun closeAddressEditing() {
        if (editingState.value) cancelRequest.value += 1
    }

    fun cancelAddressInput() {
        if (editingState.value) cancelRequest.value += 1 else takeFocus()
    }

    fun tintAddress(@Suppress("UNUSED_PARAMETER") color: Int) {
        // Address foreground is owned by the AMOLED Compose theme.
    }

    fun takeFocus() {
        if (!editingState.value) focusRequest.value += 1
    }
}
