package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialog
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.BrowserSettings

/** Full-screen settings with General, Shortcuts and About pages; General text fields apply on close. */
class SettingsScreen(context: Context, val model: BrowserSettings) : AppCompatDialog(context, R.style.SettingsScreen) {
    private var general: GeneralPage? = null

    init {
        setContentView(R.layout.settings_screen)
        SettingsPages(checkNotNull(findViewById(R.id.page_tabs)), checkNotNull(findViewById(R.id.page_content)),
            setOf(R.id.general_page_tab, R.id.shortcuts_page_tab, R.id.about_page_tab)) { page ->
            when (page) {
                R.id.general_page_tab -> GeneralPage(context).also { general = it }
                R.id.shortcuts_page_tab -> ShortcutsPage(context)
                R.id.about_page_tab -> AboutPage(context).apply {
                    callback = object : AboutPage.Callback {
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
