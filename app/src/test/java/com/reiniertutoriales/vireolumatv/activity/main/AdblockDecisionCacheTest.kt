package com.reiniertutoriales.vireolumatv.activity.main

import android.net.Uri
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.adblock.AdVerdict
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
    private fun install(model: AdBlocker, blocker: ContentBlocker) {
        AdBlocker::class.java.getDeclaredMethod("installClient", ContentBlocker::class.java, String::class.java)
            .apply { isAccessible = true }.invoke(model, blocker, "test")
    }
    @Test fun repeatedPlaylistsUseOneMatchButContextTypeAndTokensRemainIndependent() {
        val model = AdBlocker(autoLoad = false)
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
    /** Counts native matches: [verdicts] answers by host, as one native call would. */
    private class VerdictBlocker(private val verdicts: Map<String, AdVerdict>) : ContentBlocker {
        var matches = 0
        override val isConcurrent get() = true
        override fun shouldBlock(url: Uri, type: String?, baseHost: String) = error("matched through verdict only")
        override fun verdict(url: Uri, type: String?, page: Uri): AdVerdict {
            matches++
            return verdicts[url.host] ?: AdVerdict.ALLOW
        }
        override fun serialize(file: File) = true
    }

    @Test fun aNewRequestIsMatchedOnceAndARepeatedOneNotAtAllRedirectIncluded() {
        val model = AdBlocker(autoLoad = false)
        val noop = AdVerdict.redirect("data:application/javascript;base64,KGZ1bmN0aW9uKCl7fSkoKQ==")
        val blocker = VerdictBlocker(mapOf("cdn.test" to noop, "ads.test" to AdVerdict.BLOCK))
        val page = Uri.parse("https://page.test")
        try {
            install(model, blocker)
            val redirected = Uri.parse("https://cdn.test/ads/loader.js")
            assertSame(noop, model.verdict(redirected, "script", page))
            assertEquals(1, blocker.matches)
            repeat(50) { assertSame(noop, model.verdict(redirected, "script", page)) }
            assertTrue(model.isAd(redirected, "script", page))
            assertEquals("redirects come from the cache too", 1, blocker.matches)
            assertSame(AdVerdict.BLOCK, model.verdict(Uri.parse("https://ads.test/b.js"), "script", page))
            assertSame(AdVerdict.ALLOW, model.verdict(Uri.parse("https://page.test/app.js"), "script", page))
            repeat(10) {
                model.verdict(Uri.parse("https://ads.test/b.js"), "script", page)
                model.verdict(Uri.parse("https://page.test/app.js"), "script", page)
            }
            assertEquals(3, blocker.matches)
            // Type and page context are part of the key.
            model.verdict(redirected, "xhr", page)
            model.verdict(redirected, "script", Uri.parse("https://other.test"))
            assertEquals(5, blocker.matches)
            // New rules are a new generation: nothing cached under the old ones is reused.
            val updated = VerdictBlocker(emptyMap())
            install(model, updated)
            assertSame(AdVerdict.ALLOW, model.verdict(redirected, "script", page))
            assertEquals(1, updated.matches)
        } finally { model.clear() }
    }

    @Test fun cacheIsBoundedAndOversizedUrlsAreNotRetained() {
        val model = AdBlocker(autoLoad = false)
        val blocker = Blocker()
        val page = Uri.parse("https://page.test")
        val first = Uri.parse("https://stream.test/0.m3u8?" + "x".repeat(1000))
        try {
            install(model, blocker)
            model.isAd(first, "xhr", page)
            repeat(400) { model.isAd(Uri.parse("https://stream.test/${it + 1}.m3u8?" + "x".repeat(1000)), "xhr", page) }
            val before = blocker.calls
            model.isAd(first, "xhr", page)
            assertEquals(before + 1, blocker.calls)
            val huge = Uri.parse("https://stream.test/live.m3u8?" + "x".repeat(3000))
            repeat(2) { model.isAd(huge, "xhr", page) }
            assertEquals(before + 3, blocker.calls)
        } finally { model.clear() }
    }
    @Test fun matcherFailuresAreNotCachedAsAllowedDecisions() {
        val model = AdBlocker(autoLoad = false)
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
    @Test fun replacingRulesDoesNotWaitForABusyNativeMatcher() {
        val model = AdBlocker(autoLoad = false)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val blocker = object : ContentBlocker {
            override fun shouldBlock(url: Uri, type: String?, baseHost: String): Boolean {
                entered.countDown()
                release.await(5, java.util.concurrent.TimeUnit.SECONDS)
                return false
            }
            override fun serialize(file: File) = true
        }
        try {
            install(model, blocker)
            val request = executor.submit<Boolean> {
                model.isAd(Uri.parse("https://ads.test/banner"), "image", Uri.parse("https://page.test"))
            }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            executor.submit { install(model, Blocker(true)) }.get(1, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(model.isAd(Uri.parse("https://ads.test/banner"), "image", Uri.parse("https://page.test")))
            release.countDown()
            assertFalse(request.get(2, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
            model.clear()
        }
    }

}
