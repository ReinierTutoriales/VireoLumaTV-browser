package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Earlier versions let WebView remember every location answer for its origin. Now that no answer
 * is remembered, those stored grants are removed once per WebView profile (process). [clearAll]
 * runs after the profile exists; completion is recorded only after it returned, so an interrupted
 * run is simply repeated, which is harmless.
 */
internal object LegacyLocationGrants {
    private const val KEY = "legacy_location_grants_cleared"

    fun clearOnce(prefs: SharedPreferences, profile: String, clearAll: () -> Unit) {
        val key = "$KEY:$profile"
        if (prefs.getBoolean(key, false)) return
        clearAll()
        prefs.edit { putBoolean(key, true) }
    }
}
