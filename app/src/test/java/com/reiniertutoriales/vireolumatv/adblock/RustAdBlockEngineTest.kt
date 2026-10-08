package com.reiniertutoriales.vireolumatv.adblock

import android.net.Uri
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs the real adblock-rust engine (host build of native/adblock-jni) through the JNI bridge. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RustAdBlockEngineTest {
    private val rules = """
        ||ads.example^
        @@||ads.example/allowed^
        ||cdn.example/ads/*${'$'}script,redirect=noopjs
        ||video.example/vast/*${'$'}xhr,3p,redirect=noop-vast4.xml
        ||tracker.example^${'$'}3p
        stream.*##.player-overlay
        example.test##.ad-box
        stream.*##+js(nostif, adblock)
        example.test##+js(set, canRunAds, true)
        example.test#@#+js(set, canRunAds, true)
        example.test##+js(acs, document.createElement, popunder)
    """.trimIndent()

    private val engine = RustAdBlockEngine { AdblockResources.json(RuntimeEnvironment.getApplication()) }
    private val page = Uri.parse("https://stream.example/watch")

    private fun ContentBlocker.decision(url: String, type: String) = check(Uri.parse(url), type, page)

    @Test fun uBlockSyntaxBlocksRedirectsAndHonoursExceptions() {
        assertTrue("host library must be loaded", RustAdblock.isAvailable)
        val blocker = engine.compile(rules)!!
        try {
            assertTrue(blocker.isConcurrent)
            assertEquals(ContentBlocker.BLOCK, blocker.decision("https://ads.example/banner.js", "script"))
            assertEquals(ContentBlocker.ALLOW, blocker.decision("https://ads.example/allowed", "script"))
            assertEquals(ContentBlocker.ALLOW, blocker.decision("https://stream.example/app.js", "script"))
            // "3p" only applies to third-party requests.
            assertEquals(ContentBlocker.BLOCK, blocker.decision("https://tracker.example/t.gif", "image"))
            assertEquals(ContentBlocker.ALLOW, blocker.check(Uri.parse("https://tracker.example/t.gif"), "image",
                Uri.parse("https://tracker.example/")))
            // $redirect: blocked, but the page gets a harmless resource instead of an error.
            assertEquals(ContentBlocker.BLOCK_REDIRECT, blocker.decision("https://cdn.example/ads/loader.js", "script"))
            val js = blocker.redirect(Uri.parse("https://cdn.example/ads/loader.js"), "script", page)!!
            assertTrue(js, js.startsWith("data:application/javascript;base64,"))
            val vast = blocker.redirect(Uri.parse("https://video.example/vast/1.xml"), "xhr", page)!!
            assertTrue(String(android.util.Base64.decode(vast.substringAfter(','), 0)).contains("<VAST version=\"4.0\">"))
        } finally { blocker.release() }
    }

    @Test fun pageFiltersResolveEntitiesAliasesAndScriptletExceptions() {
        val blocker = engine.compile(rules)!!
        try {
            val stream = JSONObject(blocker.pageFilters("https://stream.example/watch"))
            assertEquals(".player-overlay", stream.getJSONArray("hide").getString(0))
            // The uBO alias "nostif" resolves to our canonical template.
            assertTrue(stream.getString("script"), stream.getString("script").contains("[\"no-setTimeout-if\",[\"adblock\""))
            val example = JSONObject(blocker.pageFilters("https://www.example.test/"))
            val script = example.getString("script")
            assertFalse("#@#+js exception removes set-constant", script.contains("set-constant"))
            assertTrue(script, script.contains("[\"abort-current-script\",[\"document.createElement\",\"popunder\""))
            assertEquals("{\"generichide\":false,\"hide\":[],\"procedural\":[],\"script\":\"\"}",
                blocker.pageFilters("https://unrelated.example/"))
        } finally { blocker.release() }
    }

    @Test fun redirectRulesWithoutAShippedResourceAreDroppedLikeInUBlockOrigin() {
        // uBO discards a filter whose redirect resource it lacks; blocking without a replacement
        // would break players waiting for e.g. the IMA SDK.
        val blocker = engine.compile(listOf(
            "||ima.example/ima3.js\$script,redirect=google-ima.js\n||media.example/ad.mp4\$media,redirect=noop-1s.mp4:5",
            "||known.example/ad.js\$script,redirect=noopjs:5\n||blocked.example^\nexample.test##.redirect-banner"
        ))!!
        try {
            assertEquals(ContentBlocker.ALLOW, blocker.decision("https://ima.example/ima3.js", "script"))
            assertEquals(ContentBlocker.ALLOW, blocker.decision("https://media.example/ad.mp4", "media"))
            assertEquals(ContentBlocker.BLOCK_REDIRECT, blocker.decision("https://known.example/ad.js", "script"))
            assertEquals(ContentBlocker.BLOCK, blocker.decision("https://blocked.example/x.js", "script"))
            // Cosmetic lines that merely contain the word are kept.
            assertTrue(blocker.pageFilters("https://example.test/").contains(".redirect-banner"))
        } finally { blocker.release() }
    }

    @Test fun replacementsEchoTheOriginOfCredentialedRequests() {
        val headers = AdblockSurrogates.replacementHeaders(mapOf("origin" to "https://stream.example"))
        assertEquals("https://stream.example", headers["Access-Control-Allow-Origin"])
        assertEquals("true", headers["Access-Control-Allow-Credentials"])
        assertEquals("*", AdblockSurrogates.replacementHeaders(emptyMap())["Access-Control-Allow-Origin"])
        assertEquals("*", AdblockSurrogates.replacementHeaders(mapOf("Origin" to "null"))["Access-Control-Allow-Origin"])
    }

    @Test fun serializedRulesRestoreAndAnswerFromManyThreads() {
        val compiled = engine.compile(rules)!!
        val file = File.createTempFile("adblock", ".dat")
        try {
            assertTrue(compiled.serialize(file))
            compiled.release()
            assertEquals("released rules allow everything", ContentBlocker.ALLOW,
                compiled.decision("https://ads.example/banner.js", "script"))
            val restored = engine.deserialize(file)!!
            val urls = (0 until 400).map { if (it % 2 == 0) "https://ads.example/$it.js" else "https://stream.example/$it.js" }
            val pool = Executors.newFixedThreadPool(4)
            try {
                val results = (0 until 4).map { pool.submit<List<Int>> { urls.map { restored.decision(it, "script") } } }
                    .map { it.get(10, TimeUnit.SECONDS) }
                results.forEach { list ->
                    list.forEachIndexed { i, r -> assertEquals(if (i % 2 == 0) ContentBlocker.BLOCK else ContentBlocker.ALLOW, r) }
                }
            } finally {
                pool.shutdownNow()
                restored.release()
            }
        } finally { file.delete() }
    }
}
