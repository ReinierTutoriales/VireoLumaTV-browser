package com.reiniertutoriales.vireolumatv.webengine.webview

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
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
}
