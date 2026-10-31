package com.reiniertutoriales.vireolumatv.service.downloads

import android.content.pm.ServiceInfo
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.downloads.RunningDownloads
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [35])
class DownloadServiceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun freeFileKeepsTheExtensionAndTreatsALeadingDotAsPartOfTheName() {
        val dir = folder.root
        assertEquals("a.txt", DownloadService.freeFile(dir, "a.txt").name)
        dir.resolve("a.txt").createNewFile()
        assertEquals("a_(1).txt", DownloadService.freeFile(dir, "a.txt").name)
        dir.resolve("a_(1).txt").createNewFile()
        assertEquals("a_(2).txt", DownloadService.freeFile(dir, "a.txt").name)
        dir.resolve(".config").createNewFile()
        assertEquals(".config_(1)", DownloadService.freeFile(dir, ".config").name)
        dir.resolve("README").createNewFile()
        assertEquals("README_(1)", DownloadService.freeFile(dir, "README").name)
    }

    @Test fun waitingDownloadsReserveDistinctLegacyFilesBeforeAnyWorkerWrites() {
        val first = DownloadService.reserveFile(folder.root, "same.zip")
        val second = DownloadService.reserveFile(folder.root, "same.zip")
        assertEquals("same.zip", first.name)
        assertEquals("same_(1).zip", second.name)
        assertTrue(first.exists())
        assertTrue(second.exists())
        first.writeText("first payload")
        val third = DownloadService.reserveFile(folder.root, "same.zip")
        assertEquals("same_(2).zip", third.name)
        assertEquals("first payload", first.readText())
    }

    @Test fun foregroundTimeoutCancelsDownloadsAndStopsTheService() {
        val controller = Robolectric.buildService(DownloadService::class.java).create()
        val service = controller.get()
        val model = ScreenModels.get(RunningDownloads::class, service)
        val info = Download().apply { incognito = true }
        model.runningDownloads.add(StreamTransfer(info, "x".byteInputStream(), service.transferListener))
        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertTrue("A running download must be cancelled", info.cancelled)
        assertTrue("The service must stop itself within the timeout", shadowOf(service).isStoppedBySelf)
        model.runningDownloads.clear()
        controller.destroy()
    }
    @Test fun saturatedWorkersRejectOverflowWithoutLeakingItsStreamOrReportingItStarted() {
        val controller = Robolectric.buildService(DownloadService::class.java).create()
        val service = controller.get()
        val executor = DownloadService::class.java.getDeclaredField("executor").apply { isAccessible = true }
            .get(service) as java.util.concurrent.ThreadPoolExecutor
        val active = java.util.concurrent.CountDownLatch(2)
        val release = java.util.concurrent.CountDownLatch(1)
        var rejectedClosed = false
        try {
            repeat(2) { executor.execute { active.countDown(); release.await() } }
            assertTrue(active.await(3, java.util.concurrent.TimeUnit.SECONDS))
            repeat(4) {
                assertTrue(service.startDownload(Download().apply {
                    incognito = true; filename = "$it.bin"; stream = byteArrayOf(1).inputStream()
                }))
            }
            val rejected = Download().apply {
                incognito = true
                stream = object : java.io.ByteArrayInputStream(byteArrayOf(1)) {
                    override fun close() { rejectedClosed = true; super.close() }
                }
            }
            assertFalse(service.startDownload(rejected))
            assertEquals(4, executor.queue.size)
            assertTrue(rejectedClosed)
            assertNull(rejected.stream)
            val model = ScreenModels.get(RunningDownloads::class, service)
            assertEquals(4, model.runningDownloads.size)
            assertFalse(model.runningDownloads.any { it.job === rejected })
        } finally {
            controller.destroy() // Marks accepted queued tasks cancelled before they can create output.
            release.countDown()
            assertTrue(executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS))
        }
    }

}
