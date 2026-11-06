package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag

/**
 * Reusable AMOLED action for remote-control navigation.
 * Bundled vector drawables remain local: no fonts, network or Play Services.
 * Callers own the action and browser state; this component never touches WebView.
 */
@Composable
fun TvBrowserAction(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    showLabel: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val surface by animateColorAsState(
        targetValue = when {
            pressed -> Color(0xFF343434)
            focused -> Color(0xFF202020)
            else -> Color.Black
        },
        label = "tv-action-surface",
    )
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .defaultMinSize(minHeight = 48.dp)
            .semantics { contentDescription = label; role = Role.Button }
            .clip(shape)
            .background(surface)
            .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.DarkGray), shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) "More options: $label" else null,
            )
            .padding(horizontal = if (showLabel) 14.dp else 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) tint else Color.Gray,
            modifier = Modifier.size(24.dp),
        )
        if (showLabel) {
            Text(label, color = if (enabled) Color.White else Color.Gray, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
