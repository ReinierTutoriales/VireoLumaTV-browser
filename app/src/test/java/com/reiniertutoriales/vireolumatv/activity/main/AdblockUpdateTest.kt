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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class AdblockUpdateTest {
    @Test fun expiredCacheBlocksWhileDownloadingAndFailedRefreshKeepsWorkingRules() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failedDownload = AtomicReference<String?>(null)
        val responseCode = AtomicInteger(200)
        var compiles = 0
        var restores = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            val text = failedDownload.get() ?: "||new-ads.test^"
            val bytes = text.toByteArray()
            exchange.sendResponseHeaders(responseCode.get(), bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        fun blocker(host: String) = object : ContentBlocker {
            override fun shouldBlock(url: Uri, type: String?, baseHost: String) = url.host == host
            override fun serialize(file: File): Boolean { file.writeText(host); return true }
        }
        val engine = object : ContentBlockerEngine {
            override val cacheFileName = "update-test.dat"
            override fun compile(filterText: String): ContentBlocker {
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
            assertSame("Concurrent automatic checks share the download", job, model.loadAdBlockList(false))
            assertSame("A manual request during an actual refresh shares the download", job, model.loadAdBlockList(true))
            release.countDown()
            finish(job)
            assertTrue(blocks("new-ads.test"))
            assertFalse(blocks("old-ads.test"))
            assertEquals(1, compiles)
            assertEquals(1, restores)
            val successTime = model.config.adBlockListLastUpdate
            for ((status, body) in listOf(200 to "<html>error</html>", 200 to "", 503 to "Unavailable")) {
                responseCode.set(status)
                failedDownload.set(body)
                finish(model.loadAdBlockList(true))
                assertTrue(blocks("new-ads.test"))
                assertEquals("Failed downloads should not recompile cached rules", 1, compiles)
                assertEquals("An existing native client should not be deserialized again", 1, restores)
                assertFalse(model.clientLoading.value)
                assertEquals(successTime, model.config.adBlockListLastUpdate)
                assertEquals(AdblockModel.UpdateResult.CACHED, model.updateResult.value)
                assertTrue(model.config.adBlockListNextRetry > System.currentTimeMillis())
            }
        } finally {
            release.countDown()
            model.clear()
            server.stop(0)
            cache.delete()
            File(VireoLumaTVApp.instance.filesDir, "adblock_list_custom_${AdblockCache.sourceKey(source)}.txt").delete()
        }
    }

    @Test fun manualRefreshDuringCacheRestoreIsNotLost() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            val bytes = "||fresh.test^".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val engine = object : ContentBlockerEngine {
            override val cacheFileName = "manual-refresh-test.dat"
            override fun compile(filterText: String) = blocker("fresh.test")
            override fun deserialize(file: File): ContentBlocker? {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return if (file.exists()) blocker(file.readText()) else null
            }
        }
        val model = AdblockModel(engine, autoLoad = false)
        val source = "http://127.0.0.1:${server.address.port}/"
        model.config.adBlockListURL.value = source
        model.config.adBlockListLastUpdate = System.currentTimeMillis()
        model.config.adBlockListNextRetry = 0
        val cache = AdblockCache.fileFor(VireoLumaTVApp.instance.filesDir, engine, source)
        cache.writeText("cached.test")
        try {
            val automatic = model.loadAdBlockList(false)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val manual = model.loadAdBlockList(true)
            shadowOf(Looper.getMainLooper()).idle()
            release.countDown()
            finish(automatic)
            finish(manual)
            assertEquals("The manual request must refresh after the cache-only load", 1, requests.get())
            assertTrue(model.isAd(Uri.parse("https://fresh.test/ad"), "image", Uri.parse("https://page.test")))
            assertEquals(AdblockModel.UpdateResult.UPDATED, model.updateResult.value)
        } finally {
            release.countDown()
            model.clear()
            server.stop(0)
            cache.delete()
            File(VireoLumaTVApp.instance.filesDir, "adblock_list_custom_${AdblockCache.sourceKey(source)}.txt").delete()
        }
    }

    @Test fun foregroundChecksHonorRetryDeadlineAndStopInBackground() {
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            val bytes = "||fresh.test^".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val engine = object : ContentBlockerEngine {
            override val cacheFileName = "foreground-refresh-test.dat"
            override fun compile(filterText: String) = blocker("fresh.test")
            override fun deserialize(file: File) = if (file.exists()) blocker(file.readText()) else null
        }
        val model = AdblockModel(engine, autoLoad = false)
        val source = "http://127.0.0.1:${server.address.port}/"
        model.config.adBlockListURL.value = source
        model.config.adBlockListLastUpdate = System.currentTimeMillis()
        model.config.adBlockListNextRetry = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
        val cache = AdblockCache.fileFor(VireoLumaTVApp.instance.filesDir, engine, source)
        cache.writeText("cached.test")
        val main = shadowOf(Looper.getMainLooper())
        try {
            val loop = model.startAutomaticUpdates()
            assertSame("Repeated lifecycle signals must not duplicate the timer", loop, model.startAutomaticUpdates())
            finish(model.loadAdBlockList(false))
            main.idleFor(java.time.Duration.ofMillis(AdblockModel.FOREGROUND_CHECK_INTERVAL_MS))
            awaitCondition { !model.clientLoading.value }
            assertEquals("A future retry must not download", 0, requests.get())
            model.config.adBlockListNextRetry = System.currentTimeMillis() - 1
            main.idleFor(java.time.Duration.ofMillis(AdblockModel.FOREGROUND_CHECK_INTERVAL_MS))
            awaitCondition { requests.get() == 1 && !model.clientLoading.value }
            assertEquals("A long foreground session must trigger a due retry", 1, requests.get())
            assertEquals(0L, model.config.adBlockListNextRetry)
            assertEquals(AdblockModel.UpdateResult.UPDATED, model.updateResult.value)
            model.stopAutomaticUpdates()
            assertTrue(loop.isCancelled)
            model.config.adBlockListNextRetry = System.currentTimeMillis() - 1
            main.idleFor(java.time.Duration.ofMillis(2 * AdblockModel.FOREGROUND_CHECK_INTERVAL_MS))
            assertEquals("Background time must not schedule downloads", 1, requests.get())
            model.startAutomaticUpdates()
            awaitCondition { requests.get() == 2 && !model.clientLoading.value }
            assertEquals("Returning to foreground must immediately check the due retry", 2, requests.get())
        } finally {
            model.clear()
            server.stop(0)
            cache.delete()
            File(VireoLumaTVApp.instance.filesDir, "adblock_list_custom_${AdblockCache.sourceKey(source)}.txt").delete()
        }
    }

    @Test fun changingSourceDuringDownloadInstallsOnlyTheCurrentSource() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/old") { exchange ->
            requests.incrementAndGet()
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            val bytes = "||old.test^".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/new") { exchange ->
            requests.incrementAndGet()
            val bytes = "||new.test^".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val engine = object : ContentBlockerEngine {
            override val cacheFileName = "source-change-test.dat"
            override fun compile(filterText: String) = blocker(if (filterText.contains("||new.test^")) "new.test" else "old.test")
            override fun deserialize(file: File) = if (file.exists()) blocker(file.readText()) else null
        }
        val model = AdblockModel(engine, autoLoad = false)
        val base = "http://127.0.0.1:${server.address.port}"
        model.config.adBlockListURL.value = "$base/old"
        model.config.adBlockListLastUpdate = 0
        model.config.adBlockListNextRetry = 0
        try {
            val job = model.loadAdBlockList(true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            model.config.adBlockListURL.value = "$base/new"
            model.loadAdBlockList(true)
            release.countDown()
            finish(job)
            assertEquals(2, requests.get())
            assertTrue(model.isAd(Uri.parse("https://new.test/ad"), "image", Uri.parse("https://page.test")))
            assertFalse(model.isAd(Uri.parse("https://old.test/ad"), "image", Uri.parse("https://page.test")))
            assertEquals(AdblockModel.UpdateResult.UPDATED, model.updateResult.value)
        } finally {
            release.countDown()
            model.clear()
            server.stop(0)
            for (source in listOf("$base/old", "$base/new")) {
                AdblockCache.fileFor(VireoLumaTVApp.instance.filesDir, engine, source).delete()
                File(VireoLumaTVApp.instance.filesDir, "adblock_list_custom_${AdblockCache.sourceKey(source)}.txt").delete()
            }
        }
    }

    private fun blocker(host: String) = object : ContentBlocker {
        override fun shouldBlock(url: Uri, type: String?, baseHost: String) = url.host == host
        override fun serialize(file: File): Boolean { file.writeText(host); return true }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Adblock condition timed out", condition())
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
