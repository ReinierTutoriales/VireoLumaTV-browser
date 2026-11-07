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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.BrowserTab

/** Native Compose TV tab strip; browser operations stay in the host. */
@Composable
fun TvTabStrip(
    tabs: List<BrowserTab>,
    selected: Int,
    onFocus: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onOptions: (BrowserTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val newTab = stringResource(R.string.tabs_new)
    val closeTab = stringResource(R.string.menu_close_tab)
    Row(
        modifier = modifier.background(Color.Black).horizontalScroll(rememberScrollState())
            .focusGroup().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val interaction = remember(tab) { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val hovered by interaction.collectIsHoveredAsState()
            LaunchedEffect(focused) { if (focused) onFocus(index) }
            Row(
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    .background(if (focused) Color(0xFF262626) else if (selected == index) Color(0xFF171717) else TvControlColors.surface(false, hovered), shape)
                    .border(if (focused) 2.dp else 1.dp, TvControlColors.border(focused, hovered), shape)
                    .semantics { role = Role.Button }
                    .combinedClickable(interactionSource = interaction, indication = null,
                        onClick = { onActivate(index) }, onLongClick = { onOptions(tab) })
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tab.title.ifBlank { tab.url }.ifBlank { newTab },
                    color = Color.White, fontSize = 15.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                val closeInteraction = remember(tab) { MutableInteractionSource() }
                val closeFocused by closeInteraction.collectIsFocusedAsState()
                val closeHovered by closeInteraction.collectIsHoveredAsState()
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .background(if (closeFocused) Color(0xFF383838) else if (closeHovered) TvControlColors.Hovered else Color.Transparent, shape)
                        .border(if (closeFocused) 2.dp else if (closeHovered) 1.dp else 0.dp, TvControlColors.border(closeFocused, closeHovered), shape)
                        .semantics { role = Role.Button; contentDescription = closeTab }
                        .combinedClickable(
                            interactionSource = closeInteraction,
                            indication = null,
                            onClick = { onClose(tab) },
                            onLongClick = { onOptions(tab) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    TvSymbolIcon(
                        symbol = TvSymbol.TabClose,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        val addInteraction = remember { MutableInteractionSource() }
        val addFocused by addInteraction.collectIsFocusedAsState()
        val addHovered by addInteraction.collectIsHoveredAsState()
        Box(
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .background(if (addFocused) Color(0xFF292929) else TvControlColors.surface(false, addHovered), shape)
                .border(if (addFocused) 2.dp else 1.dp, TvControlColors.border(addFocused, addHovered), shape)
                .semantics { role = Role.Button; contentDescription = newTab }
                .combinedClickable(interactionSource = addInteraction, indication = null, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            TvSymbolIcon(
                symbol = TvSymbol.Add,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
