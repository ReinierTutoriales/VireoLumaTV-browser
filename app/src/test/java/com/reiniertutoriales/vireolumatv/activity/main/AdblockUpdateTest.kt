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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class AdblockUpdateTest {
    @Test fun configuredListNamesReflectActualSources() {
        val model = AdBlocker(autoLoad = false)
        try {
            assertEquals(listOf("EasyList", "EasyPrivacy", "EasyList Spanish", "uBlock filters",
                "uBlock Quick fixes", "uBlock Unbreak", "uBlock Badware risks"),
                model.configuredListNames(com.reiniertutoriales.vireolumatv.Config.DEFAULT_ADBLOCK_LIST_URL))
            assertEquals(listOf("Custom"), model.configuredListNames("https://example.com/custom.txt"))
        } finally {
            model.clear()
        }
    }

    @Test fun expiredCacheBlocksWhileDownloadingAndFailedRefreshKeepsWorkingRules() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failedDownload = AtomicBoolean(false)
        var compiles = 0
        var restores = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            val text = if (failedDownload.get()) "<html>error</html>" else "||new-ads.test^"
            val bytes = text.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        fun blocker(host: String) = object : ContentBlocker {
            override fun shouldBlock(url: Uri, type: String?, baseHost: String) = url.host == host
            override fun serialize(file: File): Boolean { file.writeText(host); return true }
        }
        val engine = object : ContentBlockerEngine {
            override val cacheFileName = "update-test.dat"
            override fun compile(filterLists: List<String>): ContentBlocker {
                compiles++
                return blocker("new-ads.test")
            }
            override fun deserialize(file: File): ContentBlocker? {
                restores++
                return if (file.exists()) blocker(file.readText()) else null
            }
        }
        val model = AdBlocker(engine, autoLoad = false)
        val source = "http://127.0.0.1:${server.address.port}/"
        model.config.blockListUrl.value = source
        model.config.blockListUpdatedAt = 0
        model.config.blockListRetryAt = 0
        val cache = AdblockCache.fileFor(VireoLumaTVApp.instance.filesDir, engine, source)
        cache.writeText("old-ads.test")
        fun blocks(host: String) = model.isAd(Uri.parse("https://$host/banner"), "image", Uri.parse("https://page.test"))
        try {
            val job = model.loadBlockList(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Download must start", entered.await(5, TimeUnit.SECONDS))
            assertTrue("Cached blocker must be installed before the response arrives", blocks("old-ads.test"))
            release.countDown()
            finish(job)
            assertTrue(blocks("new-ads.test"))
            assertFalse(blocks("old-ads.test"))
            assertEquals(1, compiles)
            assertEquals(1, restores)
            assertTrue("A downloaded list is not reported as unavailable", model.unavailableLists.value.isEmpty())
            val successTime = model.config.blockListUpdatedAt
            failedDownload.set(true)
            finish(model.loadBlockList(true))
            assertTrue(blocks("new-ads.test"))
            assertEquals("Failed downloads should not recompile cached rules", 1, compiles)
            assertEquals("An existing native client should not be deserialized again", 1, restores)
            assertFalse(model.listLoading.value)
            assertEquals(successTime, model.config.blockListUpdatedAt)
            assertEquals(AdBlocker.UpdateResult.CACHED, model.updateResult.value)
            assertEquals(listOf("Custom"), model.unavailableLists.value)
            assertTrue(model.config.blockListRetryAt > System.currentTimeMillis())
        } finally {
            release.countDown()
            model.clear()
            server.stop(0)
            cache.delete()
            File(VireoLumaTVApp.instance.filesDir, "adblock_list_custom_${AdblockCache.sourceKey(source)}.txt").delete()
        }
    }

    @Test fun incompatibleV6NativeCacheRebuildsFromSavedTextAfterAHttpFailure() {
        assertTrue("The real native engine must be loaded", RustAdblock.isAvailable)
        val native = RustAdBlockEngine { AdblockResources.json(VireoLumaTVApp.instance) }
        var compiles = 0
        val engine = object : ContentBlockerEngine {
            override val cacheFileName = native.cacheFileName
            override fun compile(filterLists: List<String>): ContentBlocker? {
                compiles++
                return native.compile(filterLists)
            }
            override fun deserialize(file: File) = native.deserialize(file)
        }
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { response ->
            requests.incrementAndGet()
            response.sendResponseHeaders(503, -1)
            response.close()
        }
        server.start()
        val source = "http://127.0.0.1:${server.address.port}/list.txt"
        val cache = AdblockCache.fileFor(VireoLumaTVApp.instance.filesDir, engine, source)
        val savedText = File(VireoLumaTVApp.instance.filesDir,
            "adblock_list_custom_${AdblockCache.sourceKey(source)}.txt")
        val model = AdBlocker(engine, autoLoad = false)
        model.config.blockListUrl.value = source
        model.config.blockListUpdatedAt = System.currentTimeMillis()
        model.config.blockListRetryAt = 0
        cache.writeBytes(requireNotNull(javaClass.getResourceAsStream("/adblock/0.13.3.dat")).use { it.readBytes() })
        savedText.writeText("||old-cache.test^")
        try {
            assertEquals("Fixture was serialized by adblock 0.13.3", 6, cache.readBytes()[4].toInt())
            assertNull("The new engine must reject the old format", native.deserialize(cache))
            finish(model.loadBlockList(false))
            assertEquals(1, requests.get())
            assertEquals(1, compiles)
            assertTrue(model.isAd(Uri.parse("https://old-cache.test/banner.js"), "script", Uri.parse("https://page.test")))
            assertFalse(model.listLoading.value)
            assertEquals(AdBlocker.UpdateResult.CACHED, model.updateResult.value)
            assertEquals("||old-cache.test^", savedText.readText())
            assertEquals("The replacement cache uses the current format", 8, cache.readBytes()[4].toInt())
            val restored = requireNotNull(native.deserialize(cache))
            try {
                assertEquals(ContentBlocker.BLOCK, restored.check(Uri.parse("https://old-cache.test/banner.js"),
                    "script", Uri.parse("https://page.test")))
            } finally { restored.release() }
            model.clear()
            val nextSession = AdBlocker(engine, autoLoad = false)
            try {
                finish(nextSession.loadBlockList(false))
                assertEquals("The rewritten cache needs no second compilation", 1, compiles)
                assertEquals("The next session restores without another request", 1, requests.get())
                assertTrue(nextSession.isAd(Uri.parse("https://old-cache.test/banner.js"), "script", Uri.parse("https://page.test")))
            } finally { nextSession.clear() }
        } finally {
            model.clear()
            server.stop(0)
            cache.delete()
            savedText.delete()
        }
    }

    private fun finish(job: Job) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!job.isCompleted && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Adblock update timed out", job.isCompleted)
        assertFalse("Adblock update failed", job.isCancelled)
    }
}
