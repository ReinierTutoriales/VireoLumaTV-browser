package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.ShortcutKeyDialog
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.RemoteShortcut
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.Shortcuts

/** One row per remote shortcut with its assigned key; activating a row edits that shortcut. */
@Composable
internal fun ShortcutsPage(context: Context) {
    val shortcuts = remember { Shortcuts.getInstance() }
    // Read again after each edit, since the bindings live outside Compose.
    var edits by remember { mutableIntStateOf(0) }
    Column {
        RemoteShortcut.entries.forEach { entry ->
            val key = edits.let { shortcuts.binding(entry)?.describe(context) } ?: stringResource(R.string.shortcut_unassigned)
            SettingsValue(stringResource(entry.titleResId), key, "shortcut_${entry.name}") {
                ShortcutKeyDialog(context, entry).apply {
                    setOnDismissListener { edits++ }
                    show()
                }
            }
        }
    }
}
