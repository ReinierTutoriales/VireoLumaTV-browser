package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.reiniertutoriales.vireolumatv.R

/**
 * Presentation-only command bar. All navigation and WebView operations are owned
 * by the activity, keeping the UI replaceable without duplicating browser logic.
 */
data class BrowserCommand(
    val label: String,
    @DrawableRes val icon: Int,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val testTag: String? = null,
)

@Composable
fun TvBrowserCommandBar(
    address: String? = null,
    onSubmitAddress: (String) -> Unit = {},
    onCancelAddress: () -> Unit = {},
    onAddressDraftChanged: (String) -> Unit = {},
    onAddressEditingChanged: (Boolean) -> Unit = {},
    onFocusSuggestions: () -> Boolean = { false },
    cancelAddressRequest: Int = 0,
    submitAddressRequest: Pair<Int, String> = 0 to "",
    back: BrowserCommand,
    forward: BrowserCommand,
    refresh: BrowserCommand,
    home: BrowserCommand,
    menu: BrowserCommand,
    blockerEnabled: Boolean,
    blockerDescription: String = "",
    blockerReady: Boolean = false,
    focusRequest: Int = 0,
    onToggleBlocker: () -> Unit,
    onShowBlockerDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val backFocus = remember { FocusRequester() }
    val homeFocus = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            if (back.enabled) backFocus.requestFocus() else homeFocus.requestFocus()
        }
    }
    Row(
        modifier = modifier.focusGroup().focusRestorer().background(Color.Black).padding(horizontal = if (address == null) 0.dp else 24.dp, vertical = 8.dp),
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
        CommandAction(back, Modifier.focusRequester(backFocus))
        CommandAction(forward)
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
            iconRes = menu.icon,
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
    TvBrowserAction(
        label = description.ifBlank { if (enabled) "Shield on" else "Shield off" },
        iconRes = if (enabled) R.drawable.ic_shield_on else R.drawable.ic_shield_off,
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
        iconRes = command.icon,
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
    cancelAddressRequest: Int = 0,
    submitAddressRequest: Pair<Int, String> = 0 to "",
    canGoBack: Boolean,
    canGoForward: Boolean,
    blockerEnabled: Boolean,
    blockerDescription: String = "",
    blockerReady: Boolean = false,
    focusRequest: Int = 0,
    modifier: Modifier = Modifier,
) {
    val backLabel = androidx.compose.ui.res.stringResource(R.string.nav_back)
    val forwardLabel = androidx.compose.ui.res.stringResource(R.string.nav_forward)
    val reloadLabel = androidx.compose.ui.res.stringResource(R.string.nav_reload)
    val homeLabel = androidx.compose.ui.res.stringResource(R.string.nav_home)
    val menuLabel = androidx.compose.ui.res.stringResource(R.string.nav_menu)
    TvBrowserCommandBar(
        address = address,
        onSubmitAddress = onSubmitAddress,
        onCancelAddress = onCancelAddress,
        onAddressDraftChanged = onAddressDraftChanged,
        onAddressEditingChanged = onAddressEditingChanged,
        onFocusSuggestions = onFocusSuggestions,
        cancelAddressRequest = cancelAddressRequest,
        submitAddressRequest = submitAddressRequest,
        back = BrowserCommand(backLabel, R.drawable.ic_back, canGoBack, actions.back, testTag = "back_button"),
        forward = BrowserCommand(forwardLabel, R.drawable.ic_forward, canGoForward, actions.forward, testTag = "forward_button"),
        refresh = BrowserCommand(reloadLabel, R.drawable.ic_refresh, onClick = actions.reload, testTag = "refresh_button"),
        home = BrowserCommand(homeLabel, R.drawable.ic_home, onClick = actions.home, testTag = "home_button"),
        menu = BrowserCommand(menuLabel, R.drawable.ic_more, onClick = actions.menu, testTag = "menu_button"),
        blockerEnabled = blockerEnabled,
        blockerDescription = blockerDescription,
        blockerReady = blockerReady,
        focusRequest = focusRequest,
        onToggleBlocker = actions.shield,
        onShowBlockerDetails = actions.shieldDetails,
        modifier = modifier,
    )
}
