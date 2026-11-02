package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.model.Favorite
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig
import java.lang.reflect.Proxy

/** The home page shows the favorites the user saved, read-only and only to itself. */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(application = VireoLumaTVApp::class, sdk = [28])
class HomeFavoritesTest {
    private fun favorite(title: String?, url: String?) = Favorite().apply { this.title = title; this.url = url }

    @Test fun webFavoritesInTheOrderTheyWereAddedOnceEachAndBounded() {
        // The table answers newest first.
        val newestFirst = listOf(
            favorite("Música", "https://music.test/"),
            favorite("Folder", null),
            favorite("Script", "javascript:alert(1)"),
            favorite("Again", "https://news.test/today"),
            favorite(null, "https://untitled.test/"),
            favorite("  Noticias ", "https://news.test/today"),
        )
        val list = JSONArray(HomeFavorites.json(newestFirst))
        assertEquals(3, list.length())
        assertEquals("Noticias", list.getJSONObject(0).getString("title"))
        assertEquals("https://news.test/today", list.getJSONObject(0).getString("url"))
        assertEquals("", list.getJSONObject(1).getString("title"))
        assertEquals("https://music.test/", list.getJSONObject(2).getString("url"))
        val many = (1..60).map { favorite("t$it", "https://s$it.test/") }
        assertEquals(HomeFavorites.MAX, JSONArray(HomeFavorites.json(many)).length())
    }

    @Test fun theAnswerIsNeverCachedNorSharedWithOtherSitesAndAFailureIsAnEmptyList() {
        val answer = HomeFavorites.response { listOf(favorite("A", "https://a.test/")) }
        assertEquals(200, answer.statusCode)
        assertEquals("application/json", answer.mimeType)
        assertEquals("no-store", answer.responseHeaders["Cache-Control"])
        assertFalse(answer.responseHeaders.keys.any { it.startsWith("Access-Control", ignoreCase = true) })
        assertEquals(1, JSONArray(answer.data.bufferedReader().readText()).length())
        val failed = HomeFavorites.response { error("database unavailable") }
        assertEquals("[]", failed.data.bufferedReader().readText())
    }

    @Test fun onlyTheHomePageReceivesTheFavorites() {
        val engine = WebViewPageEngine(BrowserTab())
        try {
            val page = engine.createView(RuntimeEnvironment.getApplication()) as BrowserWebView
            val client = page.webViewClient
            val address = Config.HOME_PAGE_URL + HomeFavorites.PATH
            page.currentOriginalUrl = Uri.parse(Config.HOME_PAGE_URL)
            val served: WebResourceResponse? = client.shouldInterceptRequest(page, request(address))
            assertEquals("application/json", served?.mimeType)
            assertTrue(JSONArray(served!!.data.bufferedReader().readText()).length() >= 0)
            // Another site asking for the same address gets nothing from the browser.
            page.currentOriginalUrl = Uri.parse("https://other.test/")
            assertNull(client.shouldInterceptRequest(page, request(address))?.takeIf { it.mimeType == "application/json" })
        } finally { engine.detach(destroyView = true) }
    }

    private fun request(url: String) = Proxy.newProxyInstance(WebResourceRequest::class.java.classLoader,
        arrayOf(WebResourceRequest::class.java)) { _, method, _ ->
        when (method.name) {
            "getUrl" -> Uri.parse(url)
            "isForMainFrame" -> false
            "getRequestHeaders" -> emptyMap<String, String>()
            "getMethod" -> "GET"
            else -> null
        }
    } as WebResourceRequest
}
