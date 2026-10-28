package com.reiniertutoriales.vireolumatv.activity.main

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDialog
import com.reiniertutoriales.vireolumatv.R

/** Actions offered by the application menu, in display order. */
internal enum class AppMenuEntry(@StringRes val label: Int, @DrawableRes val icon: Int) {
    HISTORY(R.string.history, R.drawable.ic_history),
    FAVORITES(R.string.favorites, R.drawable.ic_bookmark),
    DOWNLOADS(R.string.downloads, R.drawable.ic_download),
    INCOGNITO(R.string.incognito_mode, R.drawable.ic_incognito),
    SETTINGS(R.string.settings, R.drawable.ic_settings),
    CLOSE_TAB(R.string.close_tab, R.drawable.ic_tab_close),
    CLOSE_APPLICATION(R.string.close_application, R.drawable.ic_exit),
}

internal fun appMenuEntries(): List<AppMenuEntry> = AppMenuEntry.entries.toList()

/**
 * The application menu as a TV side panel: full height at the end of the screen, one row per
 * entry, focus on the first. Back closes it; choosing an entry closes it and runs [onPick].
 */
internal class AppMenuPanel(context: Context, entries: List<AppMenuEntry>, onPick: (AppMenuEntry) -> Unit) :
    AppCompatDialog(context, R.style.SidePanel) {
    init {
        setContentView(R.layout.dialog_app_menu)
        val list = checkNotNull(findViewById<LinearLayout>(R.id.menuEntries))
        val inflater = LayoutInflater.from(context)
        val rows = entries.map { entry ->
            val row = inflater.inflate(R.layout.view_menu_entry, list, false)
            row.findViewById<ImageView>(R.id.ivIcon).setImageResource(entry.icon)
            row.findViewById<TextView>(R.id.tvLabel).setText(entry.label)
            row.setOnClickListener {
                dismiss()
                onPick(entry)
            }
            list.addView(row)
            row
        }
        window?.apply {
            setGravity(Gravity.END)
            setLayout(context.resources.getDimensionPixelSize(R.dimen.side_panel_width), ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setOnShowListener { rows.firstOrNull()?.requestFocus() }
    }
}
