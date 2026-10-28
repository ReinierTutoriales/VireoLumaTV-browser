package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialog
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.SettingsModel

/** Full-screen settings with General, Shortcuts and About pages; General text fields apply on close. */
class SettingsDialog(context: Context, val model: SettingsModel) : AppCompatDialog(context, R.style.SettingsDialog) {
    private var general: MainSettingsView? = null

    init {
        setContentView(R.layout.dialog_settings)
        SettingsPages(checkNotNull(findViewById(R.id.sbTabs)), checkNotNull(findViewById(R.id.flTabsContent)),
            setOf(R.id.btnMainTab, R.id.btnShortcutsTab, R.id.btnVersionTab)) { page ->
            when (page) {
                R.id.btnMainTab -> MainSettingsView(context).also { general = it }
                R.id.btnShortcutsTab -> ShortcutsSettingsView(context)
                R.id.btnVersionTab -> VersionSettingsView(context).apply {
                    callback = object : VersionSettingsView.Callback {
                        override fun onNeedToCloseSettings() = dismiss()
                    }
                }
                else -> error("Unknown settings page: $page")
            }
        }
        setOnDismissListener { general?.save() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
