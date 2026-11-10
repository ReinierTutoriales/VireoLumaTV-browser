package com.reiniertutoriales.vireolumatv.webengine.webview

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import android.view.KeyEvent
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class PrivilegedPageActionsTest {
    private val worker: ExecutorService
        get() = PrivilegedPageActions::class.java.getDeclaredField("blobThread").let {
            it.isAccessible = true
            it.get(null) as ExecutorService
        }

    @Test fun aPageCannotQueueMoreDownloadMessagesWhileItsFirstMessageIsPending() {
        val actions = PrivilegedPageActions(WebViewPageEngine(BrowserTab()))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocker = worker.submit { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        var replies = 0
        try {
            assertTrue("The blob worker must be occupied before the page floods it", entered.await(5, TimeUnit.SECONDS))
            repeat(64) {
                actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "page", { "page" }) { replies++ }
            }
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
            worker.submit {}.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            assertEquals("Only the first outstanding message from this page may be admitted", 1, replies)
        } finally {
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
            worker.submit {}.get(5, TimeUnit.SECONDS)
            actions.invalidateTransfers()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test fun manyPagesShareABoundedDownloadAdmissionBudget() {
        val pages = List(64) { PrivilegedPageActions(WebViewPageEngine(BrowserTab())) }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocker = worker.submit { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        var replies = 0
        try {
            assertTrue("The worker must be occupied before different pages submit", entered.await(5, TimeUnit.SECONDS))
            pages.forEach { actions ->
                actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "page", { "page" }) { replies++ }
            }
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
            worker.submit {}.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            // Eight messages bound retained chunks to eight base64-encoded CHUNK_BYTES payloads,
            // while leaving room for the live WebView and its popup opener.
            assertTrue("A process-wide budget admits at most eight messages, not one per retained page: $replies", replies <= 8)
            assertTrue("The budget must still admit ordinary download messages", replies > 0)
        } finally {
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
            worker.submit {}.get(5, TimeUnit.SECONDS)
            pages.forEach { it.invalidateTransfers() }
            ShadowLooper.idleMainLooper()
        }
    }

    @Test fun aPageCannotMoveItsUnboundedBacklogFromTheWorkerToMainThreadReplies() {
        val actions = PrivilegedPageActions(WebViewPageEngine(BrowserTab()))
        var replies = 0
        try {
            repeat(64) {
                actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "page", { "page" }) { replies++ }
                // Finish the worker step, but deliberately leave its reply queued on the main looper.
                worker.submit {}.get(5, TimeUnit.SECONDS)
            }
            ShadowLooper.idleMainLooper()
            assertEquals("Admission remains occupied until its main-thread reply completes", 1, replies)
        } finally {
            actions.invalidateTransfers()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test fun deliveringAReplyAdmitsTheNextProtocolStep() {
        val actions = PrivilegedPageActions(WebViewPageEngine(BrowserTab()))
        var replies = 0
        repeat(16) {
            actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "page", { "page" }) { replies++ }
            worker.submit {}.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
        }
        assertEquals("Both page and global permits are reusable after acknowledgement", 16, replies)
    }

    @Test fun invalidationDropsAPendingReplyAndReturnsItsAdmissionPermit() {
        val actions = PrivilegedPageActions(WebViewPageEngine(BrowserTab()))
        var replies = 0
        repeat(16) {
            actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "page", { "page" }) { replies++ }
            worker.submit {}.get(5, TimeUnit.SECONDS)
            actions.invalidateTransfers()
            ShadowLooper.idleMainLooper()
        }
        assertEquals(0, replies)
        actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "new-page", { "new-page" }) { replies++ }
        worker.submit {}.get(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()
        assertEquals("Invalidated deliveries must not consume the admission budget forever", 1, replies)
    }

    @Test fun invalidationBeforeTheWorkerStartsAlsoReturnsItsAdmissionPermit() {
        val actions = PrivilegedPageActions(WebViewPageEngine(BrowserTab()))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocker = worker.submit { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        var replies = 0
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "page", { "page" }) { replies++ }
            actions.invalidateTransfers()
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
            worker.submit {}.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            assertEquals(0, replies)
            actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "new-page", { "new-page" }) { replies++ }
            worker.submit {}.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            assertEquals("The generation check must release its skipped worker step", 1, replies)
        } finally {
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
            worker.submit {}.get(5, TimeUnit.SECONDS)
            actions.invalidateTransfers()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test fun aReplyExceptionStillReturnsItsAdmissionPermit() {
        val actions = PrivilegedPageActions(WebViewPageEngine(BrowserTab()))
        repeat(16) {
            actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "page", { "page" }) {
                throw IllegalStateException("Reply channel is closed")
            }
            worker.submit {}.get(5, TimeUnit.SECONDS)
            assertThrows(IllegalStateException::class.java) { ShadowLooper.idleMainLooper() }
        }
        var replies = 0
        actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "page", { "page" }) { replies++ }
        worker.submit {}.get(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()
        assertEquals("A failed reply must not leave the page or process saturated", 1, replies)
    }

    @Test fun stepsWithoutRepliesAlsoReturnTheirAdmissionPermit() {
        val actions = PrivilegedPageActions(WebViewPageEngine(BrowserTab()))
        repeat(16) {
            actions.onDownloadMessage(DownloadMessage.Cancel("unknown"), "page", { "page" }) { error("Cancel has no reply") }
            worker.submit {}.get(5, TimeUnit.SECONDS)
            actions.onDownloadMessage(DownloadMessage.Done("unknown", "blob:https://site.test/id"), "page", { "page" }) {
                error("Done has no reply")
            }
            worker.submit {}.get(5, TimeUnit.SECONDS)
        }
        var replies = 0
        actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "page", { "page" }) { replies++ }
        worker.submit {}.get(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()
        assertEquals(1, replies)
    }

    private fun captureDownloads(engine: WebViewPageEngine, downloads: MutableList<Download>) {
        engine.window = Proxy.newProxyInstance(PageWindow::class.java.classLoader,
            arrayOf(PageWindow::class.java)) { _, method, arguments ->
            if (method.name == "download") downloads += arguments!![0] as Download
            null
        } as PageWindow
    }

    private fun queueCompletedTransfer(actions: PrivilegedPageActions, currentDocument: () -> String?): File {
        val document = "original-document"
        val url = "blob:https://site.test/id"
        val spoolDirectory = File(VireoLumaTVApp.instance.cacheDir, "blob-spool")
        val previousFiles = spoolDirectory.listFiles().orEmpty().toSet()
        fun acknowledged(message: DownloadMessage): String {
            val answers = mutableListOf<String>()
            actions.onDownloadMessage(message, document, currentDocument) { answers += it }
            worker.submit {}.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            assertEquals("Every accepted protocol step gets one acknowledgement", 1, answers.size)
            return answers.single()
        }
        UserActivation.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        UserActivation.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        val begin = acknowledged(DownloadMessage.Begin(url, "one-byte.bin")).split('\n')
        assertEquals(listOf("begin", url), begin.take(2))
        val token = begin.last()
        assertTrue("A real activation must open the transfer", token.isNotEmpty())
        assertEquals(DownloadMessage.sizeReply(token, true),
            acknowledged(DownloadMessage.Size(token, 1, "application/octet-stream")))
        assertEquals(DownloadMessage.chunkReply(token, 1),
            acknowledged(DownloadMessage.Chunk(token, 0, "AQ==")))
        val spool = (spoolDirectory.listFiles().orEmpty().toSet() - previousFiles).single()
        assertTrue("The accepted chunk is backed by a file", spool.isFile)
        actions.onDownloadMessage(DownloadMessage.Done(token, url), document, currentDocument) {
            error("Done has no acknowledgement")
        }
        worker.submit {}.get(5, TimeUnit.SECONDS)
        assertTrue("Completed payload must stay alive until main-thread delivery", spool.isFile)
        return spool
    }

    private fun assertAdmissionReusable(actions: PrivilegedPageActions) {
        var replies = 0
        actions.onDownloadMessage(DownloadMessage.Size("unknown", 1, "text/plain"), "new-document", { "new-document" }) {
            replies++
        }
        worker.submit {}.get(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()
        assertEquals("Completed delivery releases the page and process admission permits", 1, replies)
    }

    @Test fun invalidatingACompletedTransferBeforeDeliveryClosesItsPayload() {
        val engine = WebViewPageEngine(BrowserTab())
        val actions = engine.privileged
        val downloads = mutableListOf<Download>()
        captureDownloads(engine, downloads)
        var document = "original-document"
        try {
            val spool = queueCompletedTransfer(actions) { document }
            assertTrue("The main-thread delivery is still pending", downloads.isEmpty())
            document = "replacement-document"
            actions.invalidateTransfers()
            assertTrue("The completed stream is owned by its queued delivery", spool.isFile)
            ShadowLooper.idleMainLooper()
            assertTrue("The replacement document must not receive the old download", downloads.isEmpty())
            assertFalse("Discarding the completed stream deletes its spool", spool.exists())
            assertAdmissionReusable(actions)
        } finally {
            actions.invalidateTransfers()
            ShadowLooper.idleMainLooper()
            downloads.forEach { it.discardPayload() }
            engine.window = null
        }
    }

    @Test fun aCompletedTransferIsDeliveredOnceAndItsConsumerClosesThePayload() {
        val engine = WebViewPageEngine(BrowserTab())
        val actions = engine.privileged
        val downloads = mutableListOf<Download>()
        captureDownloads(engine, downloads)
        try {
            val spool = queueCompletedTransfer(actions) { "original-document" }
            assertTrue("The worker cannot call the window before main-thread delivery", downloads.isEmpty())
            ShadowLooper.idleMainLooper()
            assertEquals(1, downloads.size)
            val download = downloads.single()
            assertEquals("blob:https://site.test/id", download.url)
            assertEquals("one-byte.bin", download.filename)
            assertEquals(1L, download.size)
            assertEquals("application/octet-stream", download.mimeType)
            assertTrue("The receiving consumer owns the live payload", spool.isFile)
            download.stream!!.use { assertArrayEquals(byteArrayOf(1), it.readBytes()) }
            download.stream = null
            assertFalse("Closing the consumed payload deletes its spool", spool.exists())
            ShadowLooper.idleMainLooper()
            assertEquals("The completed download cannot be delivered twice", 1, downloads.size)
            assertAdmissionReusable(actions)
        } finally {
            actions.invalidateTransfers()
            ShadowLooper.idleMainLooper()
            downloads.forEach { it.discardPayload() }
            engine.window = null
        }
    }

}
