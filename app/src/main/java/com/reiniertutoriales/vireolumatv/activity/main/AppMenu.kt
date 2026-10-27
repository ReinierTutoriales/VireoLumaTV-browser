package com.reiniertutoriales.vireolumatv.activity.main

import androidx.annotation.StringRes
import com.reiniertutoriales.vireolumatv.R

/** Actions offered by the application menu, in display order. */
internal enum class AppMenuEntry(@StringRes val label: Int) {
    HISTORY(R.string.history),
    FAVORITES(R.string.favorites),
    DOWNLOADS(R.string.downloads),
    INCOGNITO(R.string.incognito_mode),
    SETTINGS(R.string.settings),
    CLOSE_TAB(R.string.close_tab),
    CLOSE_APPLICATION(R.string.close_application),
}

internal fun appMenuEntries(): List<AppMenuEntry> = AppMenuEntry.entries.toList()
