package com.reiniertutoriales.vireolumatv.activity.main.home

import android.app.Application
import android.net.Uri
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class HomeShortcutsTest {
    @Test fun existingFormatIsBoundedDeduplicatedAndSurvivesMalformedEntries() {
        val raw = JSONArray().put("example.test").put("https://example.test/")
            .put(JSONObjectNull).put(12).put("javascript:alert(1)").put("http://user:pass@evil.test/")
            .put("https://other.test/path?q=1#anchor").toString()
        assertEquals(listOf("https://example.test/", "https://other.test/path?q=1#anchor"), HomeShortcuts.read(raw, true).urls)
        assertTrue(HomeShortcuts.read(raw, true).youtubeHidden)
        val many = JSONArray((1..60).map { "https://site$it.test/" }).toString()
        assertEquals(24, HomeShortcuts.read(many, false).urls.size)
        assertEquals(emptyList<String>(), HomeShortcuts.read("[broken", false).urls)
        assertEquals(emptyList<String>(), HomeShortcuts.read(" ".repeat(65537), false).urls)
        assertEquals(emptyList<String>(), HomeShortcuts.read("{}", false).urls)
    }

    @Test fun safeWebDestinationsKeepPathsQueriesFragmentsAndNormalizeHostAndDefaultPort() {
        assertEquals("https://example.test/", HomeShortcuts.address(" EXAMPLE.test "))
        assertEquals("https://example.test/a?x=1#b", HomeShortcuts.address("https://EXAMPLE.test:443/a?x=1#b"))
        assertEquals("http://example.test:8080/", HomeShortcuts.address("http://example.test:8080"))
        assertEquals("https://xn--bcher-kva.test/", HomeShortcuts.address("https://bücher.test/"))
        assertEquals("https://[::1]/", HomeShortcuts.address("https://[::1]/"))
        for (bad in listOf("", "javascript:alert(1)", "data:text/html,test", "file:///a", "intent://a",
            "https://u:p@site.test/", "https://u%40p@site.test/", "https://", "https://host:bad/",
            "https://site.test:65536/", "https://site.test/\nscript", "https://site.test\\@evil.test/", "a".repeat(2049))) {
            assertNull(bad, HomeShortcuts.address(bad))
        }
    }

    @Test fun searchesKeepUntrustedTextInOneDuckDuckGoQuery() {
        assertNull(HomeShortcuts.search(" \t "))
        for (text in listOf("android tv", "café & música", "a+b?#", "<script>alert(1)</script>", "javascript:alert(1)")) {
            val uri = Uri.parse(HomeShortcuts.search(" $text "))
            assertEquals("https", uri.scheme)
            assertEquals("duckduckgo.com", uri.host)
            assertEquals(setOf("q"), uri.queryParameterNames)
            assertEquals(text, uri.getQueryParameter("q"))
        }
    }

    @Test fun onlyTheExistingYoutubeRootAliasesRestoreTheDefaultShortcut() {
        for (url in listOf(HomeShortcuts.YOUTUBE, "https://www.youtube.com/", "https://youtube.com/"))
            assertTrue(HomeShortcuts.isYoutube(url))
        assertFalse(HomeShortcuts.isYoutube("https://m.youtube.com/watch?v=123"))
        assertFalse(HomeShortcuts.isYoutube("https://youtube.com.evil.test/"))
    }

    @Test fun savedOrderArrangesTilesAndKeepsNewOnesAfterIt() {
        val order = JSONArray().put("shortcut:b").put("youtube").put(5).put("shortcut:b").toString()
        val shortcuts = HomeShortcuts.read("[]", false, order)
        assertEquals(listOf("shortcut:b", "youtube"), shortcuts.order)
        assertEquals(listOf("shortcut:b", "youtube", "favorite:a", "shortcut:c"),
            shortcuts.arrange(listOf("youtube", "favorite:a", "shortcut:b", "shortcut:c")) { it })
        assertEquals(emptyList<String>(), HomeShortcuts.read("[]", false, "[broken").order)
        assertEquals(emptyList<String>(), HomeShortcuts.read("[]", false, null).order)
    }

    private val JSONObjectNull = org.json.JSONObject.NULL
}
