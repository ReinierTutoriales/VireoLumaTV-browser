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
import com.reiniertutoriales.vireolumatv.activity.main.compose.TabChip
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvBrowserShell
import com.reiniertutoriales.vireolumatv.model.BrowserTab

/**
 * Single Compose host for the browser controls overlay: the activity pushes browser state and
 * one-shot requests here, and the shell owns presentation and focus.
 */
class ComposeControlsHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val address = mutableStateOf("")
    private val addressWarning = mutableStateOf(false)
    private val canBack = mutableStateOf(false)
    private val canForward = mutableStateOf(false)
    private val blockerEnabled = mutableStateOf(false)
    private val blockerDescription = mutableStateOf("")
    private val focusRequest = mutableIntStateOf(0)
    /** (count, restore focus to the address): Back restores it, closing the capsule does not. */
    private val cancelRequest = mutableStateOf(0 to false)
    private val editorFocusRequest = mutableIntStateOf(0)
    private val downloads = mutableIntStateOf(0)
    private val submitRequest = mutableStateOf(0 to "")
    private val editing = mutableStateOf(false)
    private val actions = mutableStateOf<BrowserCommandActions?>(null)

    private val tabs = mutableStateListOf<TabChip>()
    private val selectedTab = mutableIntStateOf(0)
    private val tabsVisible = mutableStateOf(true)
    private val tabFocusRequest = mutableStateOf(0 to 0)

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

    val isEditingAddress: Boolean get() = editing.value

    /** The tab the strip shows as selected. */
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
                    addressWarning = addressWarning.value,
                    onSubmitAddress = { onSubmitAddress?.invoke(it) },
                    onCancelAddress = { onCancelAddress?.invoke() },
                    onAddressDraftChanged = { onAddressDraftChanged?.invoke(it) },
                    onAddressEditingChanged = { active ->
                        editing.value = active
                        onAddressEditingChanged?.invoke(active)
                    },
                    onFocusSuggestions = { onFocusSuggestions?.invoke() == true },
                    cancelAddressRequest = cancelRequest.value,
                    editorFocusRequest = editorFocusRequest.intValue,
                    submitAddressRequest = submitRequest.value,
                    actions = currentActions,
                    canGoBack = canBack.value,
                    canGoForward = canForward.value,
                    blockerEnabled = blockerEnabled.value,
                    blockerDescription = blockerDescription.value,
                    focusRequest = focusRequest.intValue,
                    downloadsInProgress = downloads.intValue,
                    tabs = tabs,
                    tabsVisible = tabsVisible.value,
                    tabFocusRequest = tabFocusRequest.value,
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

    /** Shows [tab]'s address, marked when its page is the certificate warning. */
    fun showAddress(tab: BrowserTab?) {
        address.value = tab?.url.orEmpty()
        addressWarning.value = tab?.engine?.showsCertificateWarning == true
    }

    fun updateNavigation(back: Boolean, forward: Boolean) {
        canBack.value = back
        canForward.value = forward
    }


    /** Running downloads, shown on the menu button and in its spoken label. */
    fun showDownloads(count: Int) { downloads.intValue = count }

    /** The shield's state and its spoken status. */
    fun showBlocker(enabled: Boolean, description: String) {
        blockerEnabled.value = enabled
        blockerDescription.value = description
    }

    /**
     * Publishes the open tabs. Each is snapshotted as a [TabChip], so a changed title or address is
     * redrawn, and only the chips that differ are replaced: a selection change recomposes no tab.
     */
    fun setTabs(list: List<BrowserTab>, selected: Int) {
        val chips = list.map(::TabChip)
        if (tabs.size != chips.size) {
            tabs.clear()
            tabs.addAll(chips)
        } else {
            chips.forEachIndexed { index, chip -> if (tabs[index] != chip) tabs[index] = chip }
        }
        selectedTab.intValue = selected
    }

    /** Moves focus to the title of the tab at [index], once it is composed. */
    fun focusTab(index: Int) {
        tabFocusRequest.value = (tabFocusRequest.value.first + 1) to index
    }

    /** Address suggestions take the tab row's place while there are any, so the capsule does not grow. */
    fun showTabStrip(visible: Boolean) {
        tabsVisible.value = visible
        if (!visible) tabStripHasFocus = false
    }

    /** Returns focus to the address being edited, e.g. Up from the first suggestion; false when not editing. */
    fun focusAddressEditor(): Boolean {
        if (!editing.value) return false
        editorFocusRequest.intValue += 1
        return true
    }

    fun submit(text: String) {
        if (editing.value) submitRequest.value = (submitRequest.value.first + 1) to text
    }

    /** Ends editing without moving focus: the capsule is opening or closing. */
    fun closeAddressEditing() {
        if (editing.value) cancelRequest.value = (cancelRequest.value.first + 1) to false
    }

    /** Back: ends editing and leaves focus on the address, or focuses the commands when not editing. */
    fun cancelAddressInput() {
        if (editing.value) cancelRequest.value = (cancelRequest.value.first + 1) to true else requestCommandFocus()
    }

    fun takeFocus() {
        if (!editing.value) focusRequest.intValue += 1
    }

    fun requestCommandFocus() { focusRequest.intValue += 1 }
}
