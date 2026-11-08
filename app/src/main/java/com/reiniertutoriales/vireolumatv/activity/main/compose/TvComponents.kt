package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/*
 * Shared TV controls. Foundation's clickable handles D-pad OK, touch, mouse and pointer-remote
 * clicks and reports hover; TV Material's clickable surfaces (1.1.0) react only to D-pad Enter and
 * accessibility. Focus (D-pad) is the strongest state, hover is subtler (TvControlColors).
 */

private val RowShape = RoundedCornerShape(12.dp)

/**
 * One row of a TV list: optional leading symbol, a title, an optional supporting line and an
 * optional trailing text. [onLongClick] keeps the long-press options of the remote.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: TvSymbol? = null,
    supporting: String? = null,
    trailing: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val content = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .background(if (selected && !focused) TvControlColors.Selected else TvControlColors.surface(focused, hovered), RowShape)
            .border(if (focused) 2.dp else 0.dp, TvControlColors.border(focused, hovered), RowShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (symbol != null) TvSymbolIcon(symbol, Modifier.size(26.dp), tint = content)
        Column(Modifier.weight(1f)) {
            Text(title, color = content, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!supporting.isNullOrEmpty()) {
                Text(supporting, color = Color.Gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!trailing.isNullOrEmpty()) Text(trailing, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
    }
}

/** A text button for dialogs and screen actions. */
@Composable
fun TvTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier.defaultMinSize(minWidth = 96.dp, minHeight = 48.dp)
            .background(TvControlColors.surface(focused, hovered), shape)
            .border(if (focused) 2.dp else 1.dp, TvControlColors.border(focused, hovered), shape)
            .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled,
                role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray, fontSize = 16.sp, maxLines = 1)
    }
}

/** A single-line text field with a visible focus border; [password] hides the characters. */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
        cursorBrush = SolidColor(Color.White),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = hint },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
                    .background(TvControlColors.surface(focused, hovered), shape)
                    .border(if (focused) 2.dp else 1.dp, TvControlColors.border(focused, hovered), shape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(hint, color = Color.Gray, fontSize = 17.sp, maxLines = 1)
                inner()
            }
        },
    )
}
