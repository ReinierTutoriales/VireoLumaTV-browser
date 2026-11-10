package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
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
import androidx.compose.ui.platform.testTag
import com.reiniertutoriales.vireolumatv.R

/**
 * Two explicit modes prevent D-pad focus from opening the IME: a real editable text field is only
 * mounted after the user activates the address. [cancelRequest] (count, restore focus) ends editing;
 * Back restores focus to the address, closing the capsule does not. A new [editorFocusRequest]
 * returns focus to the text being edited. No keyboard or focus side effects occur during composition.
 */
@Composable
fun TvAddressField(
    address: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {},
    onDraftChanged: (String) -> Unit = {},
    onEditingChanged: (Boolean) -> Unit = {},
    onFocusSuggestions: () -> Boolean = { false },
    cancelRequest: Pair<Int, Boolean> = 0 to false,
    submitRequest: Pair<Int, String> = 0 to "",
    editorFocusRequest: Int = 0,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(address) }
    val editorFocus = remember { FocusRequester() }
    var refocusAddress by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(10.dp)
    val hint = stringResource(R.string.address_hint)
    fun cancelEditing(restoreFocus: Boolean) {
        editing = false
        onEditingChanged(false)
        draft = address
        keyboard?.hide()
        focusManager.clearFocus()
        refocusAddress = restoreFocus
        onCancel()
    }
    fun submitEditing() {
        val target = draft.trim()
        if (target.isNotEmpty()) {
            editing = false
            onEditingChanged(false)
            keyboard?.hide()
            focusManager.clearFocus()
            onSubmit(target)
        }
    }

    LaunchedEffect(cancelRequest) {
        if (cancelRequest.first > 0 && editing) cancelEditing(cancelRequest.second)
    }
    LaunchedEffect(editorFocusRequest) {
        if (editorFocusRequest > 0 && editing) runCatching { editorFocus.requestFocus() }
    }
    LaunchedEffect(submitRequest) {
        if (submitRequest.first > 0 && editing) {
            draft = submitRequest.second
            submitEditing()
        }
    }

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
            onValueChange = { draft = it; onDraftChanged(it) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
            keyboardActions = KeyboardActions(onGo = { submitEditing() }),
            modifier = modifier
                .testTag("home_search")
                .focusRequester(editorFocus)
                .fillMaxWidth()
                .background(Color.Black, shape)
                .border(2.dp, Color.White, shape)
                .onPreviewKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyUp &&
                            (event.key == Key.Escape || event.key == Key.Back) -> {
                            cancelEditing(restoreFocus = true)
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown ->
                            onFocusSuggestions()
                        else -> false
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    } else {
        val addressFocus = remember { FocusRequester() }
        LaunchedEffect(refocusAddress) {
            if (refocusAddress) {
                refocusAddress = false
                runCatching { addressFocus.requestFocus() }
            }
        }
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            modifier = modifier
                .testTag("home_search")
                .focusRequester(addressFocus)
                .fillMaxWidth()
                .background(TvControlColors.surface(focused, hovered), shape)
                .border(if (focused) 2.dp else 1.dp, TvControlColors.border(focused, hovered), shape)
                .semantics { contentDescription = hint }
                .clickable(interactionSource = interaction, indication = null) {
                    draft = address
                    editing = true
                    onEditingChanged(true)
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = address.ifBlank { hint },
                color = if (address.isBlank()) Color.Gray else Color.White,
                fontSize = 17.sp,
                maxLines = 1,
            )
        }
    }
}
