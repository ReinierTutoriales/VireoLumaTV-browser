package com.reiniertutoriales.vireolumatv.activity.main.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.reiniertutoriales.vireolumatv.activity.main.compose.BrowserCommandActions
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvBrowserShell
import com.reiniertutoriales.vireolumatv.model.BrowserTab

/**
 * Single Compose host for the browser controls overlay.
 *
 * Replaces the previous pair of FrameLayouts (BrowserControls + TabStrip).
 * The activity talks to this host with the same method names it used before;
 * the shell owns presentation and focus.
 */
class ComposeControlsHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val address = mutableStateOf("")
    private val canBack = mutableStateOf(false)
    private val canForward = mutableStateOf(false)
    private val blockerEnabled = mutableStateOf(false)
    private val blockerDescription = mutableStateOf("")
    private val blockerReady = mutableStateOf(false)
    private val focusRequest = mutableIntStateOf(0)
    private val cancelRequest = mutableIntStateOf(0)
    private val downloads = mutableIntStateOf(0)
    private val submitRequest = mutableStateOf(0 to "")
    private val editing = mutableStateOf(false)
    private val actions = mutableStateOf<BrowserCommandActions?>(null)

    private val tabs = mutableStateListOf<BrowserTab>()
    private val selectedTab = mutableIntStateOf(0)

    /** Window-level Down handling applies only to the tab row, not the command row. */
    var tabStripHasFocus: Boolean = false
        private set

    var onTabFocus: ((Int) -> Unit)? = null
    var onTabActivate: ((Int) -> Unit)? = null
    var onTabClose: ((BrowserTab) -> Unit)? = null
    var onTabOptions: ((BrowserTab) -> Unit)? = null
    var onAddTab: (() -> Unit)? = null
    var onSubmitAddress: ((String) -> Unit)? = null
    var onCancelAddress: (() -> Unit)? = null
    var onAddressDraftChanged: ((String) -> Unit)? = null
    var onAddressEditingChanged: ((Boolean) -> Unit)? = null
    var onFocusSuggestions: (() -> Boolean)? = null

    /** Mirrors the old BrowserControls.isEditingAddress for existing call sites. */
    val isEditingAddress: Boolean get() = editing.value

    /** Mirrors the old TabStrip.current. */
    var current: Int
        get() = selectedTab.intValue
        set(value) { selectedTab.intValue = value }

    init {
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val currentActions = actions.value ?: return@setContent
                TvBrowserShell(
                    address = address.value,
                    onSubmitAddress = { onSubmitAddress?.invoke(it) },
                    onCancelAddress = { onCancelAddress?.invoke() },
                    onAddressDraftChanged = { onAddressDraftChanged?.invoke(it) },
                    onAddressEditingChanged = { active ->
                        editing.value = active
                        onAddressEditingChanged?.invoke(active)
                    },
                    onFocusSuggestions = { onFocusSuggestions?.invoke() == true },
                    cancelAddressRequest = cancelRequest.intValue,
                    submitAddressRequest = submitRequest.value,
                    actions = currentActions,
                    canGoBack = canBack.value,
                    canGoForward = canForward.value,
                    blockerEnabled = blockerEnabled.value,
                    blockerDescription = blockerDescription.value,
                    blockerReady = blockerReady.value,
                    focusRequest = focusRequest.intValue,
                    downloadsInProgress = downloads.intValue,
                    tabs = tabs,
                    selectedTab = selectedTab.intValue,
                    onTabFocus = { onTabFocus?.invoke(it) },
                    onTabActivate = { onTabActivate?.invoke(it) },
                    onTabClose = { onTabClose?.invoke(it) },
                    onTabOptions = { onTabOptions?.invoke(it) },
                    onAddTab = { onAddTab?.invoke() },
                    onTabStripFocusChanged = { tabStripHasFocus = it },
                )
            }
        }
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun bindActions(value: BrowserCommandActions) { actions.value = value }

    fun showAddress(text: String) { address.value = text }

    fun updateNavigation(back: Boolean, forward: Boolean) {
        canBack.value = back
        canForward.value = forward
    }

    fun showBlockerEnabled(enabled: Boolean) { blockerEnabled.value = enabled }

    /** Running downloads, shown on the menu button and in its spoken label. */
    fun showDownloads(count: Int) { downloads.intValue = count }

    fun updateBlockerStatus(description: String, ready: Boolean) {
        blockerDescription.value = description
        blockerReady.value = ready
    }

    fun setTabs(list: List<BrowserTab>, selected: Int) {
        // Avoid invalidating the entire Compose tab row when only selection changes.
        // Compare identity: BrowserTab is a mutable model, not an immutable UI value.
        if (tabs.size != list.size || tabs.indices.any { tabs[it] !== list[it] }) {
            tabs.clear()
            tabs.addAll(list)
        }
        selectedTab.intValue = selected
    }

    fun submit(text: String) {
        if (editing.value) submitRequest.value = (submitRequest.value.first + 1) to text
    }

    fun closeAddressEditing() {
        if (editing.value) cancelRequest.intValue += 1
    }

    fun cancelAddressInput() {
        if (editing.value) cancelRequest.intValue += 1 else requestCommandFocus()
    }

    fun tintAddress(@Suppress("UNUSED_PARAMETER") color: Int) {
        // Address color is owned by the Compose theme.
    }

    fun takeFocus() {
        if (!editing.value) focusRequest.intValue += 1
    }

    fun requestCommandFocus() { focusRequest.intValue += 1 }
}
