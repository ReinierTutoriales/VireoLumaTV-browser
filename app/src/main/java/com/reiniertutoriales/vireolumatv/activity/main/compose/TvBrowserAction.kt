package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reiniertutoriales.vireolumatv.R

/**
 * Icon-only AMOLED action for remote-control navigation; [label] is its spoken name.
 * Callers own the action and browser state; this component never touches WebView.
 */
@Composable
fun TvBrowserAction(
    label: String,
    symbol: TvSymbol,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onLongClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val surface by animateColorAsState(
        targetValue = TvControlColors.surface(focused, hovered, pressed),
        label = "tv-action-surface",
    )
    val metrics = LocalChromeMetrics.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .sizeIn(minWidth = metrics.control, minHeight = metrics.control)
            .semantics { contentDescription = label; role = Role.Button }
            .clip(shape)
            .background(surface)
            .border(BorderStroke(if (focused) 2.dp else 1.dp, TvControlColors.border(focused, hovered)), shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.control_more_options, label) else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TvSymbolIcon(
            symbol = symbol,
            tint = if (enabled) tint else Color.Gray,
            modifier = Modifier.size(metrics.icon),
        )
    }
}
