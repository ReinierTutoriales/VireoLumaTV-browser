package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reiniertutoriales.vireolumatv.model.BrowserTab

/**
 * Unified Compose shell for the browser controls overlay.
 *
 * Groups the command bar and tab strip under one focus group so the entire
 * capsule can be hosted by a single ComposeView. Browser operations (navigation,
 * WebView, tabs, blocking) remain owned by the activity.
 *
 * This is the first step toward a fully Compose window: the shell can later
 * absorb suggestions, the home surface, and the page host without changing
 * the command contracts already used by CI and the activity.
 */
@Composable
fun TvBrowserShell(
    // Command bar
    address: String,
    onSubmitAddress: (String) -> Unit,
    onCancelAddress: () -> Unit,
    onAddressDraftChanged: (String) -> Unit,
    onAddressEditingChanged: (Boolean) -> Unit,
    onFocusSuggestions: () -> Boolean,
    cancelAddressRequest: Int,
    submitAddressRequest: Pair<Int, String>,
    actions: BrowserCommandActions,
    canGoBack: Boolean,
    canGoForward: Boolean,
    blockerEnabled: Boolean,
    blockerDescription: String,
    blockerReady: Boolean,
    focusRequest: Int,
    downloadsInProgress: Int,
    // Tab strip
    tabs: List<BrowserTab>,
    selectedTab: Int,
    onTabFocus: (Int) -> Unit,
    onTabActivate: (Int) -> Unit,
    onTabClose: (BrowserTab) -> Unit,
    onTabOptions: (BrowserTab) -> Unit,
    onAddTab: () -> Unit,
    modifier: Modifier = Modifier,
    onTabStripFocusChanged: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .padding(bottom = 8.dp),
    ) {
        TvConnectedBrowserCommandBar(
            actions = actions,
            address = address,
            onSubmitAddress = onSubmitAddress,
            onCancelAddress = onCancelAddress,
            onAddressDraftChanged = onAddressDraftChanged,
            onAddressEditingChanged = onAddressEditingChanged,
            onFocusSuggestions = onFocusSuggestions,
            cancelAddressRequest = cancelAddressRequest,
            submitAddressRequest = submitAddressRequest,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            blockerEnabled = blockerEnabled,
            blockerDescription = blockerDescription,
            blockerReady = blockerReady,
            focusRequest = focusRequest,
            downloadsInProgress = downloadsInProgress,
            modifier = Modifier.fillMaxWidth(),
        )
        TvTabStrip(
            tabs = tabs,
            selected = selectedTab,
            onFocus = onTabFocus,
            onActivate = onTabActivate,
            onClose = onTabClose,
            onOptions = onTabOptions,
            onAdd = onAddTab,
            onFocusWithinChanged = onTabStripFocusChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}
