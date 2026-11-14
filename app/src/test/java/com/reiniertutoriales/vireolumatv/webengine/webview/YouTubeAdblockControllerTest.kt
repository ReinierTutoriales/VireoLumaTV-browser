package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Application
import android.webkit.ValueCallback
import android.webkit.WebView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class YouTubeAdblockControllerTest {
    @Test fun scopeAllowsHttpsYouTubeAndEmbedsOnly() {
        for (url in listOf("https://youtube.com/watch?v=x", "https://m.youtube.com/",
            "https://www.youtube-nocookie.com/embed/x", "https://WWW.YOUTUBE.COM/")) {
            assertTrue(url, YouTubeAdblockController.isYouTube(url))
        }
        for (url in listOf("https://youtube.com.evil.test/", "https://notyoutube.com/",
            "https://youtube.com@evil.test/", "http://youtube.com/", "about:blank",
            "file:///youtube.com/", "https://example.test/")) {
            assertFalse(url, YouTubeAdblockController.isYouTube(url))
        }
        assertFalse(YouTubeAdblockController.isYouTube(null))
    }

    @Test fun repeatedSetEnabledDoesNotReevaluateScriptOnSameState() {
        val evaluated = mutableListOf<String>()
        var currentUrl: String? = "https://www.youtube.com/watch?v=abc"
        val webView = object : WebView(RuntimeEnvironment.getApplication()) {
            override fun getUrl(): String? = currentUrl
            override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
                evaluated += script
            }
        }
        val controller = YouTubeAdblockController(webView)
        controller.setEnabled(true)
        assertEquals(1, evaluated.size)
        assertTrue(evaluated[0].contains("__vireoYouTubeAdblock"))

        controller.setEnabled(true)
        assertEquals("Repeated setEnabled(true) on resume must not re-evaluate", 1, evaluated.size)

        controller.setEnabled(false)
        assertEquals(2, evaluated.size)
        assertTrue(evaluated[1].contains("setEnabled(false)"))

        controller.setEnabled(false)
        assertEquals("Repeated setEnabled(false) must not re-evaluate", 2, evaluated.size)

        currentUrl = "https://example.test/"
        controller.setEnabled(true)
        assertEquals("Non-YouTube page must not receive YouTube adblock evaluation", 2, evaluated.size)

        controller.onPage("https://www.youtube.com/watch?v=xyz")
        assertEquals("Fallback onPage evaluates when document-start registration is absent", 3, evaluated.size)
        controller.onPage("https://example.test/")
        assertEquals(3, evaluated.size)
    }

    @Test fun mediaPolicyScriptReusesRenderedSourceUntilPolicyChanges() {
        val context = RuntimeEnvironment.getApplication()
        val jsonA = "{\"block\":[\"av1\"],\"maxHeight\":1080}"
        val jsonB = "{\"block\":[\"vp9\",\"av1\"],\"maxHeight\":720}"
        val first = MediaPolicy.script(context, jsonA)
        val second = MediaPolicy.script(context, jsonA)
        assertSame(first, second)
        assertTrue(first.contains(jsonA))
        val updated = MediaPolicy.script(context, jsonB)
        assertNotSame(first, updated)
        assertTrue(updated.contains(jsonB))
    }

    @Test fun compactedInjectedAssetsStayValidForLineCompaction() {
        val assets = RuntimeEnvironment.getApplication().assets
        for (name in listOf("generic_injects.js", "youtube_adblock.js", "media_policy.js",
            "window_open_decoy.js", "adblock/page_filters.js")) {
            val raw = assets.open(name).bufferedReader().use { it.readText() }
            assertFalse("$name must not contain backtick template literals when compacted line by line",
                raw.contains('`'))
            for (line in raw.lineSequence()) {
                val trimmed = line.trimEnd()
                assertFalse("$name must not use multi-line string continuation", trimmed.endsWith('\\'))
            }
            val compacted = AdblockPageController.compact(raw)
            assertTrue("$name compacted output must not be empty", compacted.isNotEmpty())
            assertTrue("$name compacted output must be shorter than raw source", compacted.length < raw.length)
            assertTrue("$name must remain a self-invoking function expression",
                compacted.startsWith("(function") && compacted.endsWith(");"))
            assertTrue("$name compacted lines must be trimmed and free of line comments",
                compacted.lineSequence().all { it.isNotEmpty() && it == it.trim() && !it.startsWith("//") })
        }
    }
}
