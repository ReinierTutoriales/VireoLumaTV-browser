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
 * Optional host for [TvBrowserShell].
 *
 * Not referenced by browser_window.xml yet. The activity can later swap the
 * controls_overlay for this single ComposeView without changing command contracts.
 * Until that wiring exists, the existing BrowserControls + TabStrip path remains
 * the one exercised by CI.
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
    private val focusRequest = mutableStateOf(0)
    private val cancelRequest = mutableStateOf(0)
    private val submitRequest = mutableStateOf(0 to "")
    private val actions = mutableStateOf<BrowserCommandActions?>(null)

    private val tabs = mutableStateListOf<BrowserTab>()
    private val selectedTab = mutableIntStateOf(0)

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
                    onAddressEditingChanged = { onAddressEditingChanged?.invoke(it) },
                    onFocusSuggestions = { onFocusSuggestions?.invoke() == true },
                    cancelAddressRequest = cancelRequest.value,
                    submitAddressRequest = submitRequest.value,
                    actions = currentActions,
                    canGoBack = canBack.value,
                    canGoForward = canForward.value,
                    blockerEnabled = blockerEnabled.value,
                    blockerDescription = blockerDescription.value,
                    blockerReady = blockerReady.value,
                    focusRequest = focusRequest.value,
                    tabs = tabs,
                    selectedTab = selectedTab.intValue,
                    onTabFocus = { onTabFocus?.invoke(it) },
                    onTabActivate = { onTabActivate?.invoke(it) },
                    onTabClose = { onTabClose?.invoke(it) },
                    onTabOptions = { onTabOptions?.invoke(it) },
                    onAddTab = { onAddTab?.invoke() },
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
    fun updateBlockerStatus(description: String, ready: Boolean) {
        blockerDescription.value = description
        blockerReady.value = ready
    }
    fun setTabs(list: List<BrowserTab>, selected: Int) {
        tabs.clear()
        tabs.addAll(list)
        selectedTab.intValue = selected
    }
    fun requestCommandFocus() { focusRequest.value += 1 }
}
