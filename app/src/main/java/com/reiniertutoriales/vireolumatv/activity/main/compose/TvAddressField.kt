package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Two explicit modes prevent D-pad focus from opening the IME.
 * A real editable text field is only mounted after the user activates the address.
 * No keyboard or focus side effects occur during composition.
 */
@Composable
fun TvAddressField(
    address: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(address) }
    val editorFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(10.dp)

    // A tab switch must update the displayed draft, but never overwrite active typing.
    LaunchedEffect(address, editing) {
        if (!editing) draft = address
    }

    // If the host removes this field while the IME is active, release it explicitly.
    DisposableEffect(editing) {
        onDispose { if (editing) keyboard?.hide() }
    }

    if (editing) {
        LaunchedEffect(Unit) {
            editorFocus.requestFocus()
            keyboard?.show()
        }
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                val target = draft.trim()
                if (target.isNotEmpty()) {
                    editing = false
                    keyboard?.hide()
                    onSubmit(target)
                }
            }),
            modifier = modifier
                .focusRequester(editorFocus)
                .fillMaxWidth()
                .background(Color.Black, shape)
                .border(2.dp, Color.White, shape)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp &&
                        (event.key == Key.Escape || event.key == Key.Back)
                    ) {
                        editing = false
                        draft = address
                        keyboard?.hide()
                        true
                    } else false
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color.Black, shape)
                .border(1.dp, Color.DarkGray, shape)
                .clickable {
                    draft = address
                    editing = true
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = address.ifBlank { "Search or enter address" },
                color = if (address.isBlank()) Color.Gray else Color.White,
                fontSize = 17.sp,
                maxLines = 1,
            )
        }
    }
}
