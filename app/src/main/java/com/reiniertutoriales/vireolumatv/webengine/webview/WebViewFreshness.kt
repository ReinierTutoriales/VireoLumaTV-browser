package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.SharedPreferences
import android.content.pm.PackageInfo
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat

/**
 * Security fixes for the page engine reach the device only as updates of the WebView provider
 * (Chromium refreshes its stable channel every week). A provider that has gone [STALE_AFTER_MS]
 * without an update is missing many of them, so the user is told once per provider version.
 */
internal object WebViewFreshness {
    const val STALE_AFTER_MS = 180L * 24 * 60 * 60 * 1000
    private const val KEY = "webview_stale_notice"

    /** The provider version to warn about now, or null; an unknown install time never warns. */
    fun pendingNotice(prefs: SharedPreferences, provider: PackageInfo?, now: Long): String? {
        if (provider == null || provider.lastUpdateTime <= 0L) return null
        if (now - provider.lastUpdateTime < STALE_AFTER_MS) return null
        val id = "${provider.packageName}:${PackageInfoCompat.getLongVersionCode(provider)}"
        return id.takeIf { prefs.getString(KEY, null) != it }
    }

    fun noticeShown(prefs: SharedPreferences, id: String) = prefs.edit { putString(KEY, id) }
}
