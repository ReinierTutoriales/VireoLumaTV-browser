package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Address input with explicit editing state: D-pad focus alone does not open the IME.
 * The parent owns the page URL and performs navigation only after confirmation.
 */
@Composable
fun TvAddressField(
    address: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(address, editing) { mutableStateOf(address) }
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)

    BasicTextField(
        value = if (editing) draft else address,
        onValueChange = { if (editing) draft = it },
        readOnly = !editing,
        singleLine = true,
        interactionSource = interaction,
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = {
            val target = draft.trim()
            if (target.isNotEmpty()) onSubmit(target)
            editing = false
            keyboard?.hide()
        }),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.DarkGray, shape)
            .clickable {
                editing = true
                keyboard?.show()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            if (address.isBlank() && !editing) {
                Text("Search or enter address", color = Color.Gray)
            }
            inner()
        },
    )
}
