package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.ShortcutDialog
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.Shortcut
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.ShortcutMgr

/** One row per remote shortcut with its assigned key; activating a row edits that shortcut. */
class ShortcutsSettingsView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    init {
        val rows = SettingsRows(LayoutInflater.from(context).inflate(R.layout.view_settings_main, this, true)
            .findViewById<LinearLayout>(R.id.settingsList))
        val shortcuts = ShortcutMgr.getInstance()
        Shortcut.entries.forEach { entry ->
            lateinit var key: TextView
            key = rows.value(context.getString(entry.titleResId), keyText(shortcuts, entry)) {
                ShortcutDialog(context, entry).apply {
                    setOnDismissListener { key.text = keyText(shortcuts, entry) }
                    show()
                }
            }
        }
    }

    private fun keyText(shortcuts: ShortcutMgr, shortcut: Shortcut): String =
        shortcuts.binding(shortcut)?.describe(context) ?: context.getString(R.string.not_set)
}
