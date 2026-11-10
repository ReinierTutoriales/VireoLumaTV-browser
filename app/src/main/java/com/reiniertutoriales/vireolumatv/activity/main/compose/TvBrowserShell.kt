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
 * The browser controls overlay: the command row and, unless address suggestions take its place,
 * the tab strip, in one focus group. Browser operations (navigation, WebView, tabs, blocking)
 * remain owned by the activity.
 */
@Composable
fun TvBrowserShell(
    // Command row
    address: String,
    addressWarning: Boolean,
    onSubmitAddress: (String) -> Unit,
    onCancelAddress: () -> Unit,
    onAddressDraftChanged: (String) -> Unit,
    onAddressEditingChanged: (Boolean) -> Unit,
    onFocusSuggestions: () -> Boolean,
    cancelAddressRequest: Pair<Int, Boolean>,
    submitAddressRequest: Pair<Int, String>,
    editorFocusRequest: Int,
    actions: BrowserCommandActions,
    canGoBack: Boolean,
    canGoForward: Boolean,
    blockerEnabled: Boolean,
    blockerDescription: String,
    focusRequest: Int,
    downloadsInProgress: Int,
    // Tab strip
    tabs: List<TabChip>,
    tabsVisible: Boolean,
    tabFocusRequest: Pair<Int, Int>,
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
        TvBrowserCommandBar(
            actions = actions,
            address = address,
            addressWarning = addressWarning,
            onSubmitAddress = onSubmitAddress,
            onCancelAddress = onCancelAddress,
            onAddressDraftChanged = onAddressDraftChanged,
            onAddressEditingChanged = onAddressEditingChanged,
            onFocusSuggestions = onFocusSuggestions,
            cancelAddressRequest = cancelAddressRequest,
            submitAddressRequest = submitAddressRequest,
            editorFocusRequest = editorFocusRequest,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            blockerEnabled = blockerEnabled,
            blockerDescription = blockerDescription,
            focusRequest = focusRequest,
            downloadsInProgress = downloadsInProgress,
            modifier = Modifier.fillMaxWidth(),
        )
        if (tabsVisible) {
            TvTabStrip(
                tabs = tabs,
                selected = selectedTab,
                onFocus = onTabFocus,
                onActivate = onTabActivate,
                onClose = onTabClose,
                onOptions = onTabOptions,
                onAdd = onAddTab,
                onFocusWithinChanged = onTabStripFocusChanged,
                focusRequest = tabFocusRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}
