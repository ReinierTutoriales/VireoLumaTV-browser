package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvListRow

/*
 * Settings rows. Each row is one column stop, so the D-pad order is the order of the calls; headings
 * and information text are never focus stops.
 */

private val RowGap = Modifier.padding(bottom = 6.dp)

/** A heading that groups the rows after it; read as a heading by accessibility. */
@Composable
internal fun SettingsSection(@StringRes title: Int) {
    Text(stringResource(title), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp).semantics { heading() })
}

/** Read-only text between rows. */
@Composable
internal fun SettingsInfo(text: String, modifier: Modifier = Modifier, sizeSp: Int = 16) {
    Text(text, color = Color.LightGray, fontSize = sizeSp.sp, modifier = modifier.padding(start = 4.dp, bottom = 10.dp))
}

/** A row that opens or runs something; [value] is shown below the title. */
@Composable
internal fun SettingsValue(title: String, value: String?, tag: String, onClick: () -> Unit) {
    TvListRow(title = title, supporting = value, onClick = onClick, modifier = RowGap.testTag(tag))
}

/** A list choice: the row shows the chosen option and opens a dialog with the current one marked. */
@Composable
internal fun SettingsChoice(context: Context, @StringRes title: Int, tag: String, options: Array<String>,
                            selected: Int, onSelected: (Int) -> Unit) {
    SettingsValue(stringResource(title), options.getOrNull(selected), tag) {
        TvDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(options, selected) { _, which -> if (which != selected) onSelected(which) }
            .show()
    }
}

/**
 * A text value edited in a dialog, so passing the row with the D-pad never opens the keyboard.
 * [onChanged] receives the confirmed text.
 */
@Composable
internal fun SettingsText(context: Context, @StringRes title: Int, @StringRes hint: Int, tag: String,
                          value: String, onChanged: (String) -> Unit) {
    SettingsValue(stringResource(title), value, tag) { editText(context, title, hint, value, onChanged) }
}

/** Opens the editor of a text value prefilled with [value]. */
internal fun editText(context: Context, @StringRes title: Int, @StringRes hint: Int, value: String,
                      onChanged: (String) -> Unit) {
    TvDialog.Builder(context)
        .setTitle(title)
        .setFields(TvDialog.Field(hint))
        .setPositiveButton(android.R.string.ok) { dialog, _ -> onChanged((dialog as TvDialog).fieldValue(0)) }
        .setNegativeButton(android.R.string.cancel, null)
        .create()
        .apply { setFieldValue(0, value) }
        .show()
}

/** An on/off row: activating it flips [checked]. */
@Composable
internal fun SettingsToggle(@StringRes title: Int, tag: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    TvListRow(
        title = stringResource(title),
        onClick = { onChanged(!checked) },
        role = Role.Switch,
        modifier = RowGap.testTag(tag).semantics { toggleableState = ToggleableState(checked) },
        trailingContent = { SwitchMark(checked) },
    )
}

@Composable
private fun SwitchMark(checked: Boolean) {
    val track = if (checked) Color.White else Color.DarkGray
    Box(Modifier.width(44.dp).height(24.dp).background(track, CircleShape).padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(18.dp).background(if (checked) Color.Black else Color.LightGray, CircleShape))
    }
}

/**
 * A value in [min]..[max]: D-pad Left and Right change it by [step]; a tap or click on the track sets
 * it at that point. [label] prints the value beside the track.
 */
@Composable
internal fun SettingsSlider(@StringRes title: Int, tag: String, min: Int, max: Int, value: Int, step: Int,
                            label: (Int) -> String, onChanged: (Int) -> Unit) {
    fun set(next: Int) = next.coerceIn(min, max).let { if (it != value) onChanged(it) }
    val currentSet = rememberUpdatedState<(Int) -> Unit> { set(it) }
    TvListRow(
        title = stringResource(title),
        trailing = label(value),
        onClick = {},
        modifier = RowGap.testTag(tag)
            .onKeyEvent { event ->
                val delta = when (event.key) {
                    Key.DirectionLeft -> -step
                    Key.DirectionRight -> step
                    else -> return@onKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) set(value + delta)
                true
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), min.toFloat()..max.toFloat())
                setProgress { target -> set(Math.round(target)); true }
            },
        trailingContent = {
            val fraction = (value - min).toFloat() / (max - min)
            Box(Modifier.width(160.dp).height(24.dp).pointerInput(min, max) {
                detectTapGestures { offset -> currentSet.value(min + Math.round(offset.x / size.width * (max - min))) }
            }, contentAlignment = Alignment.CenterStart) {
                val shape = RoundedCornerShape(3.dp)
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color.DarkGray, shape))
                Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(Color.White, shape))
            }
        },
    )
}

/** Runs [onConfirmed] after the warning is accepted, [onCancelled] when it is refused or dismissed. */
internal fun confirmWarning(context: Context, @StringRes title: Int, @StringRes message: Int,
                            onConfirmed: () -> Unit, onCancelled: () -> Unit) {
    TvDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirmed() }
        .setNegativeButton(R.string.action_cancel) { _, _ -> onCancelled() }
        .setOnCancelListener(DialogInterface.OnCancelListener { onCancelled() })
        .show()
}
