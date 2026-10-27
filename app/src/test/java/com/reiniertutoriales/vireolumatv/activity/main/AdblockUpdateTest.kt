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
        val model = AdblockModel(engine, autoLoad = false)
        val source = "http://127.0.0.1:${server.address.port}/"
        model.config.adBlockListURL.value = source
        model.config.adBlockListLastUpdate = 0
        model.config.adBlockListNextRetry = 0
        val cache = AdblockCache.fileFor(VireoLumaTVApp.instance.filesDir, engine, source)
        cache.writeText("old-ads.test")
        fun blocks(host: String) = model.isAd(Uri.parse("https://$host/banner"), "image", Uri.parse("https://page.test"))
        try {
            val job = model.loadAdBlockList(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Download must start", entered.await(5, TimeUnit.SECONDS))
            assertTrue("Cached blocker must be installed before the response arrives", blocks("old-ads.test"))
            release.countDown()
            finish(job)
            assertTrue(blocks("new-ads.test"))
            assertFalse(blocks("old-ads.test"))
            assertEquals(1, compiles)
            assertEquals(1, restores)
            val successTime = model.config.adBlockListLastUpdate
            failedDownload.set(true)
            finish(model.loadAdBlockList(true))
            assertTrue(blocks("new-ads.test"))
            assertEquals("Failed downloads should not recompile cached rules", 1, compiles)
            assertEquals("An existing native client should not be deserialized again", 1, restores)
            assertFalse(model.clientLoading.value)
            assertEquals(successTime, model.config.adBlockListLastUpdate)
            assertEquals(AdblockModel.UpdateResult.CACHED, model.updateResult.value)
            assertTrue(model.config.adBlockListNextRetry > System.currentTimeMillis())
        } finally {
            release.countDown()
            model.clear()
            server.stop(0)
            cache.delete()
            File(VireoLumaTVApp.instance.filesDir, "adblock_list_custom_${AdblockCache.sourceKey(source)}.txt").delete()
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
