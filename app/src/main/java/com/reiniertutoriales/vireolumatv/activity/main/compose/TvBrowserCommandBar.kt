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
)

@Composable
fun TvBrowserCommandBar(
    address: String? = null,
    onSubmitAddress: (String) -> Unit = {},
    onCancelAddress: () -> Unit = {},
    onAddressDraftChanged: (String) -> Unit = {},
    back: BrowserCommand,
    forward: BrowserCommand,
    refresh: BrowserCommand,
    home: BrowserCommand,
    menu: BrowserCommand,
    blockerEnabled: Boolean,
    onToggleBlocker: () -> Unit,
    onShowBlockerDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.focusRestorer().focusGroup().background(Color.Black).padding(horizontal = if (address == null) 0.dp else 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (address != null) {
            TvAddressField(
                address = address,
                onSubmit = onSubmitAddress,
                onCancel = onCancelAddress,
                onDraftChanged = onAddressDraftChanged,
                modifier = Modifier.weight(1f),
            )
        }
        CommandAction(back)
        CommandAction(forward)
        CommandAction(refresh)
        CommandAction(home)
        TvBrowserAction(
            label = if (blockerEnabled) "Shield on" else "Shield off",
            iconRes = if (blockerEnabled) R.drawable.ic_shield_on else R.drawable.ic_shield_off,
            onClick = onToggleBlocker,
            onLongClick = onShowBlockerDetails,
            tint = if (blockerEnabled) Color(0xFF4ADE80) else Color(0xFFF87171),
            showLabel = false,
        )
        TvBrowserAction(
            label = menu.label,
            iconRes = menu.icon,
            onClick = menu.onClick,
            enabled = menu.enabled,
            showLabel = false,
        )
    }
}

@Composable
private fun CommandAction(command: BrowserCommand) {
    TvBrowserAction(
        label = command.label,
        iconRes = command.icon,
        onClick = command.onClick,
        enabled = command.enabled,
        showLabel = false,
    )
}

/**
 * Maps the activity's shared commands into Compose without forwarding clicks
 * to hidden Android Views. This adapter is also usable by an isolated TV preview.
 */
@Composable
fun TvConnectedBrowserCommandBar(
    actions: com.reiniertutoriales.vireolumatv.activity.main.view.BrowserControls.CommandActions,
    canGoBack: Boolean,
    canGoForward: Boolean,
    blockerEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val backLabel = androidx.compose.ui.res.stringResource(R.string.nav_back)
    val forwardLabel = androidx.compose.ui.res.stringResource(R.string.nav_forward)
    val reloadLabel = androidx.compose.ui.res.stringResource(R.string.nav_reload)
    val homeLabel = androidx.compose.ui.res.stringResource(R.string.nav_home)
    val menuLabel = androidx.compose.ui.res.stringResource(R.string.nav_menu)
    TvBrowserCommandBar(
        back = BrowserCommand(backLabel, R.drawable.ic_back, canGoBack, actions.back),
        forward = BrowserCommand(forwardLabel, R.drawable.ic_forward, canGoForward, actions.forward),
        refresh = BrowserCommand(reloadLabel, R.drawable.ic_refresh, onClick = actions.reload),
        home = BrowserCommand(homeLabel, R.drawable.ic_home, onClick = actions.home),
        menu = BrowserCommand(menuLabel, R.drawable.ic_more, onClick = actions.menu),
        blockerEnabled = blockerEnabled,
        onToggleBlocker = actions.shield,
        onShowBlockerDetails = actions.shieldDetails,
        modifier = modifier,
    )
}
