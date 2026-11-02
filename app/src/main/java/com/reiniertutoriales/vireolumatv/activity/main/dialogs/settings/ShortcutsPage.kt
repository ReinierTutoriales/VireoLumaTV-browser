package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.ShortcutKeyDialog
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.RemoteShortcut
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.Shortcuts

/** One row per remote shortcut with its assigned key; activating a row edits that shortcut. */
class ShortcutsPage @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    init {
        val rows = SettingsRows(LayoutInflater.from(context).inflate(R.layout.settings_general, this, true)
            .findViewById<LinearLayout>(R.id.settingsList))
        val shortcuts = Shortcuts.getInstance()
        RemoteShortcut.entries.forEach { entry ->
            lateinit var key: TextView
            key = rows.value(context.getString(entry.titleResId), keyText(shortcuts, entry)) {
                ShortcutKeyDialog(context, entry).apply {
                    setOnDismissListener { key.text = keyText(shortcuts, entry) }
                    show()
                }
            }
        }
    }

    private fun keyText(shortcuts: Shortcuts, shortcut: RemoteShortcut): String =
        shortcuts.binding(shortcut)?.describe(context) ?: context.getString(R.string.shortcut_unassigned)
}
