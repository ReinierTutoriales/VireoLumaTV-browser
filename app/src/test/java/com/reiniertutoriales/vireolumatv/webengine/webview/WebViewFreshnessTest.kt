package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.Context
import android.content.pm.PackageInfo
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebViewFreshnessTest {
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("freshness-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val now = 1_800_000_000_000L

    private fun provider(updated: Long, version: Long = 7) = PackageInfo().apply {
        packageName = "com.google.android.webview"
        lastUpdateTime = updated
        @Suppress("DEPRECATION")
        versionCode = version.toInt()
    }

    @Test fun aRecentlyUpdatedOrUnknownProviderNeverWarns() {
        assertNull(WebViewFreshness.pendingNotice(prefs, null, now))
        assertNull(WebViewFreshness.pendingNotice(prefs, provider(0), now))
        assertNull(WebViewFreshness.pendingNotice(prefs, provider(now - WebViewFreshness.STALE_AFTER_MS + 1), now))
    }

    @Test fun aStaleProviderWarnsOncePerVersion() {
        val stale = provider(now - WebViewFreshness.STALE_AFTER_MS)
        val id = WebViewFreshness.pendingNotice(prefs, stale, now)
        assertEquals("com.google.android.webview:7", id)
        WebViewFreshness.noticeShown(prefs, id!!)
        assertNull("Shown once for this version", WebViewFreshness.pendingNotice(prefs, stale, now))
        val otherStaleVersion = provider(now - 2 * WebViewFreshness.STALE_AFTER_MS, version = 8)
        assertEquals("com.google.android.webview:8", WebViewFreshness.pendingNotice(prefs, otherStaleVersion, now))
    }
}
