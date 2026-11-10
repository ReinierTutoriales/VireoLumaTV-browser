package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.BrowserTab

/**
 * What the strip draws for one tab. [BrowserTab] is mutable and not observable, so the host passes a
 * new value whenever the label changes; equality compares the tab by identity and the label by value.
 */
@Immutable
data class TabChip(val tab: BrowserTab, val label: String) {
    constructor(tab: BrowserTab) : this(tab, tab.title.ifBlank { tab.url })
}

/**
 * Native Compose TV tab strip; browser operations stay in the host. A new [focusRequest] value
 * (count, index) moves focus to that tab's title, e.g. after the focused tab was closed.
 */
@Composable
fun TvTabStrip(
    tabs: List<TabChip>,
    selected: Int,
    onFocus: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onOptions: (BrowserTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusWithinChanged: (Boolean) -> Unit = {},
    focusRequest: Pair<Int, Int> = 0 to 0,
) {
    val metrics = LocalChromeMetrics.current
    val tabHeight = maxOf(metrics.control, 48.dp)
    val shape = RoundedCornerShape(12.dp)
    val titleFocus = remember { HashMap<BrowserTab, FocusRequester>() }
    // A request made while the strip was hidden is stale when it comes back: start from the current one.
    val handledFocus = remember { intArrayOf(focusRequest.first) }
    LaunchedEffect(focusRequest) {
        if (focusRequest.first == handledFocus[0]) return@LaunchedEffect
        handledFocus[0] = focusRequest.first
        val target = tabs.getOrNull(focusRequest.second)?.tab ?: return@LaunchedEffect
        titleFocus[target]?.let { runCatching { it.requestFocus() } }
    }
    val newTab = stringResource(R.string.tabs_new)
    val closeTab = stringResource(R.string.menu_close_tab)
    Row(
        modifier = modifier.semantics { testTagsAsResourceId = true }.background(Color.Black).horizontalScroll(rememberScrollState())
            .onFocusChanged { onFocusWithinChanged(it.hasFocus) }
            // Inside the scroll: the first tab lines up with the address, later ones may scroll to the edge.
            .focusGroup().padding(horizontal = metrics.edge, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(metrics.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, chip ->
            val tab = chip.tab
            key(tab) {
            val focus = remember { FocusRequester() }
            DisposableEffect(tab) {
                titleFocus[tab] = focus
                onDispose { titleFocus.remove(tab) }
            }
            val interaction = remember(tab) { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val hovered by interaction.collectIsHoveredAsState()
            val closeInteraction = remember(tab) { MutableInteractionSource() }
            val closeFocused by closeInteraction.collectIsFocusedAsState()
            val closeHovered by closeInteraction.collectIsHoveredAsState()
            LaunchedEffect(focused, index) { if (focused) onFocus(index) }
            // The pill is not a focus target: the title and its X are sibling targets, so D-pad
            // Right moves title -> X -> next title. A nested X lies inside the title's bounds and
            // two-dimensional focus search would skip it.
            Row(
                modifier = Modifier.heightIn(min = tabHeight).widthIn(max = metrics.tabMaxWidth)
                    .background(
                        if (focused || closeFocused) Color(0xFF262626) else if (selected == index) Color(0xFF171717)
                        else TvControlColors.surface(false, hovered || closeHovered),
                        shape,
                    )
                    .border(if (focused || closeFocused) 2.dp else 1.dp,
                        TvControlColors.border(focused || closeFocused, hovered || closeHovered), shape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.heightIn(min = tabHeight).weight(1f, fill = false)
                        .testTag("tab_${index}_${System.identityHashCode(tab)}")
                        .focusRequester(focus)
                        .semantics { role = Role.Tab; this.selected = selected == index }
                        .combinedClickable(interactionSource = interaction, indication = null,
                            onClick = { onActivate(index) }, onLongClick = { onOptions(tab) })
                        .padding(start = 14.dp, end = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(chip.label.ifBlank { newTab },
                        color = Color.White, fontSize = metrics.label, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Box(
                    modifier = Modifier.sizeIn(minWidth = tabHeight, minHeight = tabHeight)
                        .testTag("tab_close_$index")
                        .semantics { role = Role.Button; contentDescription = closeTab }
                        .combinedClickable(
                            interactionSource = closeInteraction,
                            indication = null,
                            onClick = { onClose(tab) },
                            onLongClick = { onOptions(tab) },
                        )
                        .padding(4.dp)
                        .background(if (closeFocused) Color(0xFF383838) else if (closeHovered) TvControlColors.Hovered else Color.Transparent, CircleShape)
                        .then(if (closeFocused || closeHovered) Modifier.border(
                            if (closeFocused) 2.dp else 1.dp, TvControlColors.border(closeFocused, closeHovered), CircleShape,
                        ) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    TvSymbolIcon(
                        symbol = TvSymbol.TabClose,
                        tint = Color.White,
                        modifier = Modifier.size(metrics.icon),
                    )
                }
            }
            }
        }
        val addInteraction = remember { MutableInteractionSource() }
        val addFocused by addInteraction.collectIsFocusedAsState()
        val addHovered by addInteraction.collectIsHoveredAsState()
        Box(
            modifier = Modifier.sizeIn(minWidth = metrics.control, minHeight = metrics.control)
                .background(if (addFocused) Color(0xFF292929) else TvControlColors.surface(false, addHovered), shape)
                .border(if (addFocused) 2.dp else 1.dp, TvControlColors.border(addFocused, addHovered), shape)
                .testTag("new_tab_button")
                .semantics { role = Role.Button; contentDescription = newTab }
                .combinedClickable(interactionSource = addInteraction, indication = null, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            TvSymbolIcon(
                symbol = TvSymbol.Add,
                tint = Color.White,
                modifier = Modifier.size(metrics.icon),
            )
        }
    }
}
