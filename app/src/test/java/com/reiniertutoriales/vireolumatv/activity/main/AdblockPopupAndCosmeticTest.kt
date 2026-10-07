package com.reiniertutoriales.vireolumatv.activity.main

import android.net.Uri
import android.os.Looper
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.adblock.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Popup and element hiding rules that the native engine ignores, plus unchanged-list updates. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class AdblockPopupAndCosmeticTest {
    /** Minimal engine: `||host^` blocks resources, `||host^$document` blocks documents (popups). */
    private class FakeEngine : ContentBlockerEngine {
        var compiles = 0
        override val cacheFileName = "popup-cosmetic-test.dat"
        override fun compile(filterText: String): ContentBlocker {
            compiles++
            val lines = filterText.lines().map { it.trim() }
            val documents = lines.filter { it.startsWith("||") && it.endsWith("^\$document") }
                .map { it.removePrefix("||").removeSuffix("^\$document") }.toSet()
            val resources = lines.filter { it.startsWith("||") && it.endsWith("^") }
                .map { it.removePrefix("||").removeSuffix("^") }.toSet()
            return object : ContentBlocker {
                override fun shouldBlock(url: Uri, type: String?, baseHost: String) =
                    if (type == "document") url.host in documents else url.host in resources
                override fun serialize(file: File): Boolean {
                    file.writeText((documents.map { "||$it^\$document" } + resources.map { "||$it^" }).joinToString("\n"))
                    return true
                }
            }
        }
        override fun deserialize(file: File): ContentBlocker? = if (file.exists()) compile(file.readText()).also { compiles-- } else null
    }

    @Test fun popupAndSiteHidingRulesWorkAndAnUnchangedListIsNotRecompiled() {
        val list = "||adserver.test^\n||popads.test^\$popup\nexample.test##.ad-box\n"
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            if (exchange.requestHeaders.getFirst("If-None-Match") == "\"same\"") {
                exchange.sendResponseHeaders(304, -1)
                exchange.close()
                return@createContext
            }
            exchange.responseHeaders.add("ETag", "\"same\"")
            val body = list.toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val engine = FakeEngine()
        val model = AdblockModel(engine, autoLoad = false)
        val source = "http://127.0.0.1:${server.address.port}/"
        model.config.adBlockListURL.value = source
        model.config.adBlockListLastUpdate = 0
        model.config.adBlockListNextRetry = 0
        val dir = VireoLumaTVApp.instance.filesDir
        val cache = AdblockCache.fileFor(dir, engine, source)
        try {
            finish(model.loadAdBlockList(true))
            assertEquals("Main list + popup client", 2, engine.compiles)
            val page = Uri.parse("https://site.test/video")
            assertTrue("\$popup rules are enforced", model.isPopupAd(Uri.parse("https://popads.test/go"), page))
            assertTrue("Third-party popups to ad servers are blocked",
                model.isPopupAd(Uri.parse("https://adserver.test/click"), page))
            assertFalse(model.isPopupAd(Uri.parse("https://accounts.site.test/login"), page))
            assertFalse(model.isPopupAd(Uri.parse("https://other.test/"), page))
            assertEquals(".ad-box{display:none!important}", model.cosmeticCss("www.example.test"))
            assertEquals("", model.cosmeticCss("site.test"))

            val compiled = engine.compiles
            val firstUpdate = model.config.adBlockListLastUpdate
            Thread.sleep(5)
            finish(model.loadAdBlockList(true))
            assertEquals(2, requests.get())
            assertEquals("HTTP 304 must not recompile", compiled, engine.compiles)
            assertEquals(AdblockModel.UpdateResult.UPDATED, model.updateResult.value)
            assertTrue("A 304 is a successful check", model.config.adBlockListLastUpdate > firstUpdate)
            assertEquals(0L, model.config.adBlockListNextRetry)

            // A new process restores popup and element hiding rules from their own caches.
            val restored = AdblockModel(engine, autoLoad = false)
            try {
                finish(restored.loadAdBlockList(false))
                assertEquals(2, requests.get())
                assertTrue(restored.isPopupAd(Uri.parse("https://popads.test/go"), page))
                assertEquals(".ad-box{display:none!important}", restored.cosmeticCss("example.test"))
            } finally { restored.clear() }
        } finally {
            model.clear()
            server.stop(0)
            dir.listFiles()?.filter { it.name.startsWith(cache.name) || it.name.contains(AdblockCache.sourceKey(source)) }
                ?.forEach { it.delete() }
        }
    }

    private fun finish(job: Job) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!job.isCompleted && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Adblock update timed out", job.isCompleted)
    }
}
