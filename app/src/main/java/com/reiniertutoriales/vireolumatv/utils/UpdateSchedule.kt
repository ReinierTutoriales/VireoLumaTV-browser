package com.reiniertutoriales.vireolumatv.utils

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * When the generic build looks for a new release by itself: at most once a day, counted from the
 * last check that reached GitHub. A clock set backwards makes the check due again.
 */
internal object UpdateSchedule {
    const val LAST_CHECK_KEY = "release_checked_at"
    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    fun isDue(prefs: SharedPreferences, now: Long): Boolean {
        val last = prefs.getLong(LAST_CHECK_KEY, 0L)
        return now < last || now - last >= INTERVAL_MS
    }

    fun checked(prefs: SharedPreferences, now: Long) = prefs.edit { putLong(LAST_CHECK_KEY, now) }
}
