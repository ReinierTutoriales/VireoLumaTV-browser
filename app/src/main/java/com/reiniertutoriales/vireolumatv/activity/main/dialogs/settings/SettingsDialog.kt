package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import android.widget.RadioGroup
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.SettingsModel

class SettingsDialog(context: Context, val model: SettingsModel) :
    Dialog(context, R.style.SettingsDialog),
    DialogInterface.OnDismissListener, VersionSettingsView.Callback {
    private var mainView: MainSettingsView? = null
    init {
        setTitle(R.string.settings)
        setContentView(R.layout.dialog_settings)

        SettingsPages(
            findViewById<RadioGroup>(R.id.sbTabs),
            findViewById(R.id.flTabsContent),
            setOf(R.id.btnMainTab, R.id.btnShortcutsTab, R.id.btnVersionTab),
        ) { id ->
            when (id) {
                R.id.btnMainTab -> MainSettingsView(context).also { mainView = it }
                R.id.btnShortcutsTab -> ShortcutsSettingsView(context)
                R.id.btnVersionTab -> VersionSettingsView(context).also { it.callback = this }
                else -> error("Unknown settings page: $id")
            }
        }

        setOnDismissListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT)
    }

    override fun onDismiss(dialog: DialogInterface?) {
        mainView?.save()
    }

    override fun onNeedToCloseSettings() {
        dismiss()
    }
}
