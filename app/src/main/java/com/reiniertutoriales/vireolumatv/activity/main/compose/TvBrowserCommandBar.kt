package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.reiniertutoriales.vireolumatv.R

/** The capsule's commands; the activity owns navigation and WebView. */
data class BrowserCommandActions(
    val back: () -> Unit,
    val forward: () -> Unit,
    val reload: () -> Unit,
    val home: () -> Unit,
    val shield: () -> Unit,
    val shieldDetails: () -> Unit,
    val menu: () -> Unit,
)

/**
 * Presentation-only command row: address, Back, Forward, Reload, Home, the blocker shield and the
 * menu. A new [focusRequest] focuses Back, or Home while Back is unavailable.
 */
@Composable
fun TvBrowserCommandBar(
    actions: BrowserCommandActions,
    address: String,
    addressWarning: Boolean,
    onSubmitAddress: (String) -> Unit,
    onCancelAddress: () -> Unit,
    onAddressDraftChanged: (String) -> Unit,
    onAddressEditingChanged: (Boolean) -> Unit,
    onFocusSuggestions: () -> Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    blockerEnabled: Boolean,
    modifier: Modifier = Modifier,
    cancelAddressRequest: Pair<Int, Boolean> = 0 to false,
    submitAddressRequest: Pair<Int, String> = 0 to "",
    editorFocusRequest: Int = 0,
    blockerDescription: String = "",
    focusRequest: Int = 0,
    downloadsInProgress: Int = 0,
) {
    val backFocus = remember { FocusRequester() }
    val homeFocus = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            if (canGoBack) backFocus.requestFocus() else homeFocus.requestFocus()
        }
    }
    // A history update can disable Back or Forward while it holds focus; the platform would then
    // move focus to the first focusable View behind the capsule. Keep it on Home instead.
    val holder = remember { mutableStateOf<String?>(null) }
    val holderDisabled = (holder.value == BACK && !canGoBack) || (holder.value == FORWARD && !canGoForward)
    LaunchedEffect(holderDisabled) {
        if (holderDisabled) {
            holder.value = null
            homeFocus.requestFocus()
        }
    }
    // Focus left while still enabled means the user moved it; a disabled command keeps the record.
    fun tracked(name: String, enabled: Boolean) = Modifier.onFocusChanged { state ->
        if (state.isFocused) holder.value = name
        else if (holder.value == name && enabled) holder.value = null
    }
    // Running downloads change the menu's icon and spoken label.
    val menuLabel = if (downloadsInProgress == 0) stringResource(R.string.nav_menu)
        else pluralStringResource(R.plurals.nav_menu_downloads, downloadsInProgress, downloadsInProgress)
    Row(
        modifier = modifier.semantics { testTagsAsResourceId = true }.focusGroup().focusRestorer()
            .background(Color.Black).padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvAddressField(
            address = address,
            warning = addressWarning,
            onSubmit = onSubmitAddress,
            onCancel = onCancelAddress,
            onDraftChanged = onAddressDraftChanged,
            onEditingChanged = onAddressEditingChanged,
            onFocusSuggestions = onFocusSuggestions,
            cancelRequest = cancelAddressRequest,
            submitRequest = submitAddressRequest,
            editorFocusRequest = editorFocusRequest,
            modifier = Modifier.weight(1f),
        )
        TvBrowserAction(stringResource(R.string.nav_back), TvSymbol.Back, actions.back,
            tracked(BACK, canGoBack).focusRequester(backFocus), enabled = canGoBack, testTag = "back_button")
        TvBrowserAction(stringResource(R.string.nav_forward), TvSymbol.Forward, actions.forward,
            tracked(FORWARD, canGoForward), enabled = canGoForward, testTag = "forward_button")
        TvBrowserAction(stringResource(R.string.nav_reload), TvSymbol.Refresh, actions.reload, testTag = "refresh_button")
        TvBrowserAction(stringResource(R.string.nav_home), TvSymbol.Home, actions.home,
            Modifier.focusRequester(homeFocus), testTag = "home_button")
        TvBrowserAction(
            label = blockerDescription.ifBlank { stringResource(R.string.blocker_toggle) },
            symbol = if (blockerEnabled) TvSymbol.ShieldOn else TvSymbol.ShieldOff,
            onClick = actions.shield,
            onLongClick = actions.shieldDetails,
            tint = if (blockerEnabled) Color(0xFF4ADE80) else Color(0xFFF87171),
            testTag = "blocker_button",
        )
        TvBrowserAction(menuLabel, if (downloadsInProgress == 0) TvSymbol.More else TvSymbol.MenuDownloads,
            actions.menu, testTag = "menu_button")
    }
}

private const val BACK = "back"
private const val FORWARD = "forward"
