package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri
import android.webkit.WebResourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Requests made by a service worker reach the blocker with the worker's site as their page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServiceWorkerFilterTest {
    private class Request(private val address: String, private val headers: Map<String, String>) : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(address)
        override fun isForMainFrame() = false
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = headers
    }

    private class Rules(var enabled: Boolean = true) : ServiceWorkerFilter.Policy {
        val asked = mutableListOf<Triple<String, String?, String>>()
        val sites = mutableListOf<String>()
        override fun blocksAds(site: Uri): Boolean = enabled.also { sites += site.toString() }
        override fun isAd(url: Uri, type: String?, site: Uri): Boolean {
            asked += Triple(url.toString(), type, site.toString())
            return url.host == "ads.example"
        }
        override fun redirect(url: Uri, type: String?, site: Uri): String? = null
    }

    private fun filter(rules: ServiceWorkerFilter.Policy?) =
        ServiceWorkerFilter(RuntimeEnvironment.getApplication()) { rules }

    private val workerHeaders = mapOf("Referer" to "https://video.example/sw.js", "Sec-Fetch-Dest" to "script")

    @Test fun anAdFetchedByAWorkerIsBlockedWithTheWorkersSiteAsPage() {
        val rules = Rules()
        val response = filter(rules).shouldInterceptRequest(Request("https://ads.example/tag.js", workerHeaders))
        assertNotNull("A worker's ad request must not reach the network", response)
        assertEquals(403, response!!.statusCode)
        assertEquals(Triple("https://ads.example/tag.js", "script", "https://video.example/sw.js"), rules.asked.single())
    }

    @Test fun ordinaryWorkerRequestsPass() {
        val rules = Rules()
        assertNull(filter(rules).shouldInterceptRequest(Request("https://video.example/app.js", workerHeaders)))
        assertEquals(1, rules.asked.size)
    }

    @Test fun withoutAReferrerTheSiteIsUnknownAndNothingIsGuessed() {
        val rules = Rules()
        assertNull(filter(rules).shouldInterceptRequest(Request("https://ads.example/pixel", emptyMap())))
        assertEquals(0, rules.asked.size)
        assertEquals(0, rules.sites.size)
    }

    /** The real engine: party-dependent rules see the worker's site, never the request itself. */
    @Test fun thirdPartyRulesUseTheWorkersSite() {
        val engine = com.reiniertutoriales.vireolumatv.adblock.RustAdBlockEngine {
            com.reiniertutoriales.vireolumatv.adblock.AdblockResources.json(RuntimeEnvironment.getApplication())
        }
        val blocker = engine.compile("||tracker.example^${'$'}third-party")!!
        try {
            val rules = object : ServiceWorkerFilter.Policy {
                override fun blocksAds(site: Uri) = true
                override fun isAd(url: Uri, type: String?, site: Uri) =
                    blocker.check(url, type, site) != com.reiniertutoriales.vireolumatv.adblock.ContentBlocker.ALLOW
                override fun redirect(url: Uri, type: String?, site: Uri): String? = null
            }
            val sw = filter(rules)
            assertNotNull("Third-party tracker fetched by another site's worker",
                sw.shouldInterceptRequest(Request("https://tracker.example/t.js", mapOf("Referer" to "https://video.example/sw.js"))))
            assertNull("The tracker's own worker is first-party",
                sw.shouldInterceptRequest(Request("https://tracker.example/t.js", mapOf("Referer" to "https://tracker.example/sw.js"))))
            assertNull("Without a site the rule is not applied as if first- or third-party",
                sw.shouldInterceptRequest(Request("https://tracker.example/t.js", emptyMap())))
        } finally { blocker.release() }
    }

    @Test fun theShownTabsChoiceAppliesOnlyToItsSite() {
        val shown = ServiceWorkerFilter.ShownSite("video.example", false)
        assertEquals(false, shown.blocks(Uri.parse("https://VIDEO.example/sw.js"), byDefault = true))
        assertEquals(true, shown.blocks(Uri.parse("https://other.example/sw.js"), byDefault = true))
        assertEquals(true, ServiceWorkerFilter.ShownSite("video.example", null).blocks(Uri.parse("https://video.example/"), true))
        assertEquals(false, ServiceWorkerFilter.ShownSite.NONE.blocks(Uri.parse("https://video.example/"), false))
    }

    @Test fun aSiteWithBlockingOffAndAClosedWindowLetEverythingThrough() {
        val off = Rules(enabled = false)
        assertNull(filter(off).shouldInterceptRequest(Request("https://ads.example/tag.js", workerHeaders)))
        assertEquals(listOf("https://video.example/sw.js"), off.sites)
        assertEquals(0, off.asked.size)
        assertNull(filter(null).shouldInterceptRequest(Request("https://ads.example/tag.js", workerHeaders)))
    }

    @Test fun nonWebAddressesAreNotJudged() {
        val rules = Rules()
        assertNull(filter(rules).shouldInterceptRequest(Request("data:text/plain,x", workerHeaders)))
        assertEquals(0, rules.asked.size)
    }
}
