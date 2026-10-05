package com.reiniertutoriales.vireolumatv.activity.main

import android.net.Uri
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.adblock.ContentBlocker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class AdblockDecisionCacheTest {
    private class Blocker(var result: Boolean = false) : ContentBlocker {
        var calls = 0
        var fail = false
        override fun shouldBlock(url: Uri, type: String?, baseHost: String): Boolean {
            calls++
            if (fail) throw IllegalStateException("temporary matcher error")
            return result
        }
        override fun serialize(file: File) = true
    }
    private fun install(model: AdblockModel, blocker: ContentBlocker) {
        AdblockModel::class.java.getDeclaredMethod("installClient", ContentBlocker::class.java, String::class.java)
            .apply { isAccessible = true }.invoke(model, blocker, "test")
    }
    @Test fun repeatedPlaylistsUseOneMatchButContextTypeAndTokensRemainIndependent() {
        val model = AdblockModel(autoLoad = false)
        val blocker = Blocker()
        val url = Uri.parse("https://stream.test/live.m3u8?token=one")
        val page = Uri.parse("https://page.test")
        try {
            install(model, blocker)
            repeat(100) { assertFalse(model.isAd(url, "xhr", page)) }
            assertEquals(1, blocker.calls)
            model.isAd(url, "media", page)
            model.isAd(url, "xhr", Uri.parse("https://other.test"))
            model.isAd(Uri.parse("https://stream.test/live.m3u8?token=two"), "xhr", page)
            assertEquals(4, blocker.calls)
            val updated = Blocker(true)
            install(model, updated)
            assertTrue(model.isAd(url, "xhr", page))
            assertEquals(1, updated.calls)
            model.clear()
            assertFalse(model.isAd(url, "xhr", page))
        } finally { model.clear() }
    }
    @Test fun cacheIsBoundedAndOversizedUrlsAreNotRetained() {
        val model = AdblockModel(autoLoad = false)
        val blocker = Blocker()
        val page = Uri.parse("https://page.test")
        val first = Uri.parse("https://stream.test/0.m3u8?" + "x".repeat(1000))
        try {
            install(model, blocker)
            model.isAd(first, "xhr", page)
            repeat(100) { model.isAd(Uri.parse("https://stream.test/${it + 1}.m3u8?" + "x".repeat(1000)), "xhr", page) }
            val before = blocker.calls
            model.isAd(first, "xhr", page)
            assertEquals(before + 1, blocker.calls)
            val huge = Uri.parse("https://stream.test/live.m3u8?" + "x".repeat(3000))
            repeat(2) { model.isAd(huge, "xhr", page) }
            assertEquals(before + 3, blocker.calls)
        } finally { model.clear() }
    }
    @Test fun matcherFailuresAreNotCachedAsAllowedDecisions() {
        val model = AdblockModel(autoLoad = false)
        val blocker = Blocker(true).apply { fail = true }
        val url = Uri.parse("https://stream.test/live.m3u8")
        val page = Uri.parse("https://page.test")
        try {
            install(model, blocker)
            assertFalse(model.isAd(url, "xhr", page))
            blocker.fail = false
            assertTrue(model.isAd(url, "xhr", page))
            assertEquals(2, blocker.calls)
        } finally { model.clear() }
    }
}
