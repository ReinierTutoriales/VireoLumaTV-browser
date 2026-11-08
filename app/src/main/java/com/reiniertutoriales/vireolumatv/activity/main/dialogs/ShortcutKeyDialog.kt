package com.reiniertutoriales.vireolumatv.activity.main.dialogs

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvTextButton
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.KeyBinding
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.RemoteShortcut
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.Shortcuts
import com.reiniertutoriales.vireolumatv.utils.BackPresses
import com.reiniertutoriales.vireolumatv.utils.RemoteInput

/**
 * Edits the key of one [RemoteShortcut]. While recording, the next key press is stored: released before
 * the long-press timeout as a short binding, held past it as a long-press binding.
 */
class ShortcutKeyDialog(context: Context, private val shortcut: RemoteShortcut) : ComponentDialog(context, R.style.TvDialog) {
    private val shortcuts = Shortcuts.getInstance()
    private var key by mutableStateOf(describe())
    /** Waiting for the key to record; the dialog then takes every key before its controls. */
    var recording by mutableStateOf(false)
        private set

    init {
        setCancelable(true)
        setContentView(ComposeView(context).apply { setContent { VireoTvTheme { Panel() } } })
    }

    private fun describe(): String =
        shortcuts.binding(shortcut)?.describe(context) ?: context.getString(R.string.shortcut_unassigned)

    /** Starts or stops waiting for the key, as the Assign button does. */
    fun toggleRecording() { recording = !recording }

    /** Removes the key, as the Clear button does. */
    fun clearKey() {
        recording = false
        shortcuts.set(shortcut, null)
        key = describe()
    }

    /** Stores the press as the new key unless the browser needs that key for navigation. */
    private fun record(event: KeyEvent, longPress: Boolean) {
        recording = false
        val code = KeyBinding.codeOf(event)
        if (code in RemoteInput.NAVIGATION_KEYS || code in BackPresses.BACK_KEYS) {
            Toast.makeText(context, R.string.shortcut_reserved, Toast.LENGTH_SHORT).show()
            return
        }
        shortcuts.set(shortcut, KeyBinding(code, event.modifiers, longPress))
        key = describe()
    }

    // While recording, keys skip the controls (OK on Assign would stop it) and reach the callbacks below.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (recording) event.dispatch(this, window?.decorView?.keyDispatcherState, this) else super.dispatchKeyEvent(event)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!recording) return super.onKeyDown(keyCode, event)
        event.startTracking() // Lets the framework report onKeyLongPress for a held key.
        return true
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (!recording) return super.onKeyLongPress(keyCode, event)
        record(event, longPress = true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // After a long press recording is already off, so its release is not recorded again.
        if (!recording) return super.onKeyUp(keyCode, event)
        record(event, longPress = false)
        return true
    }

    @Composable
    private fun Panel() {
        val first = remember { FocusRequester() }
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.semantics { testTagsAsResourceId = true }.widthIn(min = 360.dp, max = 640.dp)
                .background(MaterialTheme.colorScheme.surface, shape).border(1.dp, Color.DarkGray, shape).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(shortcut.titleResId), color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp)
            Text(stringResource(R.string.shortcut_current_key), color = Color.LightGray, fontSize = 15.sp)
            Text(key, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, modifier = Modifier.testTag("shortcut_key"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvTextButton(stringResource(if (recording) R.string.shortcut_waiting else R.string.shortcut_assign),
                    ::toggleRecording, Modifier.testTag("shortcut_assign").focusRequester(first))
                TvTextButton(stringResource(R.string.action_clear), ::clearKey, Modifier.testTag("shortcut_clear"))
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}
