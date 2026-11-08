package com.reiniertutoriales.vireolumatv.activity.main.compose

import android.content.Context
import android.content.DialogInterface
import androidx.activity.ComponentDialog
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R

/**
 * The app's dialog: a Compose panel in a [ComponentDialog], built like an AlertDialog so existing
 * flows keep their callbacks. Back cancels it unless it is not cancelable. Initial focus follows
 * AlertDialog's default: the checked or first option, else the first field, else the neutral or
 * negative button (the safe answer), else the positive one.
 */
class TvDialog private constructor(context: Context, private val spec: Spec) :
    ComponentDialog(context, R.style.TvDialog) {

    /** A text field of the dialog; read its value with [fieldValue] in a button callback. */
    class Field(@StringRes val hint: Int, val password: Boolean = false)

    private class Button(val text: CharSequence, val onClick: DialogInterface.OnClickListener?)

    private class Spec(
        val title: CharSequence?,
        val message: CharSequence?,
        val items: List<CharSequence>,
        val onItem: DialogInterface.OnClickListener?,
        val checked: Int,
        val fields: List<Field>,
        val buttons: Map<Int, Button>,
    )

    private val values = mutableStateListOf<String>().apply { repeat(spec.fields.size) { add("") } }

    init {
        setContentView(ComposeView(context).apply {
            setContent { VireoTvTheme { Panel() } }
        })
    }

    /** The current text of field [index]. */
    fun fieldValue(index: Int): String = values[index]

    /** Sets field [index] as if typed: for tests and for prefilled forms. */
    fun setFieldValue(index: Int, value: String) { values[index] = value }

    /** The title and message shown. */
    val title: CharSequence? get() = spec.title
    val message: CharSequence? get() = spec.message

    /** The options shown, in order. */
    val items: List<CharSequence> get() = spec.items

    /** The text of button [which] (DialogInterface.BUTTON_*), or null when the dialog has none. */
    fun buttonText(which: Int): CharSequence? = spec.buttons[which]?.text

    /** Chooses option [index], as a click on its row does: runs the callback, then dismisses. */
    fun choose(index: Int) {
        if (!isShowing) return
        spec.onItem?.onClick(this, index)
        dismiss()
    }

    /** Presses button [which] (DialogInterface.BUTTON_*), as a click on it does. */
    fun press(which: Int) {
        if (!isShowing) return
        val button = spec.buttons[which] ?: return
        button.onClick?.onClick(this, which)
        dismiss()
    }

    @Composable
    private fun Panel() {
        val first = remember { FocusRequester() }
        val firstButton = listOf(DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_POSITIVE)
            .firstOrNull { it in spec.buttons }
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.widthIn(min = 360.dp, max = 640.dp)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(1.dp, Color.DarkGray, shape)
                .padding(24.dp),
        ) {
            spec.title?.let {
                Text(it.toString(), color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp,
                    modifier = Modifier.padding(bottom = 12.dp))
            }
            spec.message?.let {
                Text(it.toString(), color = Color.LightGray, fontSize = 16.sp,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp))
            }
            if (spec.items.isNotEmpty()) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    spec.items.forEachIndexed { index, item ->
                        TvListRow(
                            title = item.toString(),
                            onClick = { choose(index) },
                            selected = index == spec.checked,
                            modifier = Modifier.padding(bottom = 4.dp).testTag("dialog_item_$index")
                                .then(if (index == maxOf(spec.checked, 0)) Modifier.focusRequester(first) else Modifier),
                        )
                    }
                }
            }
            spec.fields.forEachIndexed { index, field ->
                TvTextField(
                    value = values[index],
                    onValueChange = { values[index] = it },
                    hint = context.getString(field.hint),
                    password = field.password,
                    modifier = Modifier.padding(bottom = 10.dp).testTag("dialog_field_$index")
                        .then(if (index == 0 && spec.items.isEmpty()) Modifier.focusRequester(first) else Modifier),
                )
            }
            if (spec.buttons.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    for (which in listOf(DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_POSITIVE)) {
                        val button = spec.buttons[which] ?: continue
                        val takesFocus = spec.items.isEmpty() && spec.fields.isEmpty() && which == firstButton
                        TvTextButton(
                            text = button.text.toString(),
                            onClick = { press(which) },
                            modifier = Modifier.testTag("dialog_button_$which")
                                .then(if (takesFocus) Modifier.focusRequester(first) else Modifier),
                        )
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }

    /** Same calls as AlertDialog.Builder for the parts the app uses. */
    class Builder(private val context: Context) {
        private var title: CharSequence? = null
        private var message: CharSequence? = null
        private var items: List<CharSequence> = emptyList()
        private var onItem: DialogInterface.OnClickListener? = null
        private var checked = -1
        private var fields: List<Field> = emptyList()
        private val buttons = linkedMapOf<Int, Button>()
        private var cancelable = true
        private var onDismiss: DialogInterface.OnDismissListener? = null
        private var onCancel: DialogInterface.OnCancelListener? = null
        private var onKey: DialogInterface.OnKeyListener? = null

        fun setTitle(@StringRes text: Int) = apply { title = context.getText(text) }
        fun setTitle(text: CharSequence?) = apply { title = text }
        fun setMessage(@StringRes text: Int) = apply { message = context.getText(text) }
        fun setMessage(text: CharSequence?) = apply { message = text }
        fun setItems(options: Array<out CharSequence>, listener: DialogInterface.OnClickListener?) =
            apply { items = options.toList(); onItem = listener }
        fun setItems(@ArrayRes options: Int, listener: DialogInterface.OnClickListener?) =
            setItems(context.resources.getTextArray(options), listener)
        /** Options with the current one marked; focus starts on it. */
        fun setSingleChoiceItems(@ArrayRes options: Int, checkedItem: Int, listener: DialogInterface.OnClickListener?) =
            setItems(options, listener).also { checked = checkedItem }
        fun setFields(vararg field: Field) = apply { fields = field.toList() }
        fun setPositiveButton(@StringRes text: Int, listener: DialogInterface.OnClickListener?) =
            setPositiveButton(context.getText(text), listener)
        fun setPositiveButton(text: CharSequence, listener: DialogInterface.OnClickListener?) =
            apply { buttons[DialogInterface.BUTTON_POSITIVE] = Button(text, listener) }
        fun setNegativeButton(@StringRes text: Int, listener: DialogInterface.OnClickListener?) =
            setNegativeButton(context.getText(text), listener)
        fun setNegativeButton(text: CharSequence, listener: DialogInterface.OnClickListener?) =
            apply { buttons[DialogInterface.BUTTON_NEGATIVE] = Button(text, listener) }
        fun setNeutralButton(@StringRes text: Int, listener: DialogInterface.OnClickListener?) =
            setNeutralButton(context.getText(text), listener)
        fun setNeutralButton(text: CharSequence, listener: DialogInterface.OnClickListener?) =
            apply { buttons[DialogInterface.BUTTON_NEUTRAL] = Button(text, listener) }
        fun setCancelable(value: Boolean) = apply { cancelable = value }
        fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) = apply { onDismiss = listener }
        fun setOnCancelListener(listener: DialogInterface.OnCancelListener?) = apply { onCancel = listener }
        fun setOnKeyListener(listener: DialogInterface.OnKeyListener?) = apply { onKey = listener }

        fun create(): TvDialog = TvDialog(context, Spec(title, message, items, onItem, checked, fields, buttons.toMap())).also {
            it.setCancelable(cancelable)
            it.setOnDismissListener(onDismiss)
            it.setOnCancelListener(onCancel)
            onKey?.let(it::setOnKeyListener)
        }

        fun show(): TvDialog = create().also { it.show() }
    }
}
