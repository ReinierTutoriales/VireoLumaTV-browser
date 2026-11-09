package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.reiniertutoriales.vireolumatv.R

/**
 * Presentation-only command bar. All navigation and WebView operations are owned
 * by the activity, keeping the UI replaceable without duplicating browser logic.
 */
data class BrowserCommand(
    val label: String,
    val icon: TvSymbol,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val testTag: String? = null,
)

@Composable
fun TvBrowserCommandBar(
    back: BrowserCommand,
    forward: BrowserCommand,
    refresh: BrowserCommand,
    home: BrowserCommand,
    menu: BrowserCommand,
    blockerEnabled: Boolean,
    onToggleBlocker: () -> Unit,
    modifier: Modifier = Modifier,
    address: String? = null,
    onSubmitAddress: (String) -> Unit = {},
    onCancelAddress: () -> Unit = {},
    onAddressDraftChanged: (String) -> Unit = {},
    onAddressEditingChanged: (Boolean) -> Unit = {},
    onFocusSuggestions: () -> Boolean = { false },
    cancelAddressRequest: Int = 0,
    submitAddressRequest: Pair<Int, String> = 0 to "",
    blockerDescription: String = "",
    blockerReady: Boolean = false,
    focusRequest: Int = 0,
    onShowBlockerDetails: (() -> Unit)? = null,
) {
    val backFocus = remember { FocusRequester() }
    val homeFocus = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            if (back.enabled) backFocus.requestFocus() else homeFocus.requestFocus()
        }
    }
    // A history update can disable Back or Forward while it holds focus; the platform would then
    // move focus to the first focusable View behind the capsule. Keep it on Home instead.
    val holder = remember { mutableStateOf<String?>(null) }
    val holderDisabled = (holder.value == BACK && !back.enabled) || (holder.value == FORWARD && !forward.enabled)
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
    Row(
        modifier = modifier.semantics { testTagsAsResourceId = true }.focusGroup().focusRestorer().background(Color.Black).padding(horizontal = if (address == null) 0.dp else 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (address != null) {
            TvAddressField(
                address = address,
                onSubmit = onSubmitAddress,
                onCancel = onCancelAddress,
                onDraftChanged = onAddressDraftChanged,
                onEditingChanged = onAddressEditingChanged,
                onFocusSuggestions = onFocusSuggestions,
                cancelRequest = cancelAddressRequest,
                submitRequest = submitAddressRequest,
                modifier = Modifier.weight(1f),
            )
        }
        CommandAction(back, tracked(BACK, back.enabled).focusRequester(backFocus))
        CommandAction(forward, tracked(FORWARD, forward.enabled))
        CommandAction(refresh)
        CommandAction(home, Modifier.focusRequester(homeFocus))
        TvBlockerAction(
            enabled = blockerEnabled,
            description = blockerDescription,
            ready = blockerReady,
            onToggle = onToggleBlocker,
            onDetails = onShowBlockerDetails,
        )
        TvBrowserAction(
            label = menu.label,
            symbol = menu.icon,
            onClick = menu.onClick,
            enabled = menu.enabled,
            showLabel = false,
            testTag = menu.testTag,
        )
    }
}

/** Only browser-specific addition to the native Compose command row. */
@Composable
private fun TvBlockerAction(
    enabled: Boolean,
    description: String,
    ready: Boolean,
    onToggle: () -> Unit,
    onDetails: (() -> Unit)?,
) {
    val fallback = androidx.compose.ui.res.stringResource(R.string.blocker_toggle)
    TvBrowserAction(
        label = description.ifBlank { fallback },
        symbol = if (enabled) TvSymbol.ShieldOn else TvSymbol.ShieldOff,
        onClick = onToggle,
        onLongClick = onDetails,
        tint = if (enabled) Color(0xFF4ADE80) else Color(0xFFF87171),
        showLabel = false,
        testTag = "blocker_button",
    )
}

@Composable
private fun CommandAction(command: BrowserCommand, modifier: Modifier = Modifier) {
    TvBrowserAction(
        label = command.label,
        symbol = command.icon,
        modifier = modifier,
        onClick = command.onClick,
        enabled = command.enabled,
        showLabel = false,
        testTag = command.testTag,
    )
}

/** Actions are independent of the legacy Android View wrapper. */
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
 * Maps the activity's shared commands into Compose without forwarding clicks
 * to hidden Android Views. This adapter is also usable by an isolated TV preview.
 */
@Composable
fun TvConnectedBrowserCommandBar(
    actions: BrowserCommandActions,
    address: String,
    onSubmitAddress: (String) -> Unit,
    onCancelAddress: () -> Unit,
    onAddressDraftChanged: (String) -> Unit,
    onAddressEditingChanged: (Boolean) -> Unit,
    onFocusSuggestions: () -> Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    blockerEnabled: Boolean,
    modifier: Modifier = Modifier,
    cancelAddressRequest: Int = 0,
    submitAddressRequest: Pair<Int, String> = 0 to "",
    blockerDescription: String = "",
    blockerReady: Boolean = false,
    focusRequest: Int = 0,
    downloadsInProgress: Int = 0,
) {
    val backLabel = androidx.compose.ui.res.stringResource(R.string.nav_back)
    val forwardLabel = androidx.compose.ui.res.stringResource(R.string.nav_forward)
    val reloadLabel = androidx.compose.ui.res.stringResource(R.string.nav_reload)
    val homeLabel = androidx.compose.ui.res.stringResource(R.string.nav_home)
    // Running downloads change the menu's icon and spoken label, as before the move to Compose.
    val menuLabel = if (downloadsInProgress == 0) androidx.compose.ui.res.stringResource(R.string.nav_menu)
        else androidx.compose.ui.res.pluralStringResource(R.plurals.nav_menu_downloads, downloadsInProgress, downloadsInProgress)
    TvBrowserCommandBar(
        address = address,
        onSubmitAddress = onSubmitAddress,
        onCancelAddress = onCancelAddress,
        onAddressDraftChanged = onAddressDraftChanged,
        onAddressEditingChanged = onAddressEditingChanged,
        onFocusSuggestions = onFocusSuggestions,
        cancelAddressRequest = cancelAddressRequest,
        submitAddressRequest = submitAddressRequest,
        back = BrowserCommand(backLabel, TvSymbol.Back, canGoBack, actions.back, testTag = "back_button"),
        forward = BrowserCommand(forwardLabel, TvSymbol.Forward, canGoForward, actions.forward, testTag = "forward_button"),
        refresh = BrowserCommand(reloadLabel, TvSymbol.Refresh, onClick = actions.reload, testTag = "refresh_button"),
        home = BrowserCommand(homeLabel, TvSymbol.Home, onClick = actions.home, testTag = "home_button"),
        menu = BrowserCommand(menuLabel, if (downloadsInProgress == 0) TvSymbol.More else TvSymbol.MenuDownloads,
            onClick = actions.menu, testTag = "menu_button"),
        blockerEnabled = blockerEnabled,
        blockerDescription = blockerDescription,
        blockerReady = blockerReady,
        focusRequest = focusRequest,
        onToggleBlocker = actions.shield,
        onShowBlockerDetails = actions.shieldDetails,
        modifier = modifier,
    )
}

private const val BACK = "back"
private const val FORWARD = "forward"
