package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border

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
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .semantics { contentDescription = label }
            .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.DarkGray), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) tint else Color.Gray,
            modifier = Modifier.size(24.dp),
        )
        Text(label, color = if (enabled) Color.White else Color.Gray, fontSize = 16.sp)
    }
}
