package com.reiniertutoriales.vireolumatv.service.downloads

import android.content.ComponentName
import android.os.Build
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.downloads.TransferBoard
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.webengine.webview.BlobSpool
import com.reiniertutoriales.vireolumatv.webengine.webview.BlobTransfers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class DownloadResourceTest {
    private val callback = object : Transfer.Callback {
        override fun onProgress(task: Transfer) {}
        override fun onError(task: Transfer, responseCode: Int, responseMessage: String) {}
        override fun onDone(task: Transfer) {}
    }

    @Test fun cancellingOnePrivateDownloadDoesNotCancelAnotherWithTheSameZeroId() {
        val first = Download().apply { incognito = true }
        val second = Download().apply { incognito = true }
        val model = TransferBoard()
        model.transfers.add(StreamTransfer(first, "one".byteInputStream(), callback))
        model.transfers.add(StreamTransfer(second, "two".byteInputStream(), callback))
        model.cancel(second)
        assertFalse(first.cancelled)
        assertTrue(second.cancelled)
    }

    @Test fun normalHistoryCopyCanStillCancelItsActiveTaskByNonzeroId() {
        val live = Download().apply { id = 42 }
        val historyRow = Download().apply { id = 42 }
        val model = TransferBoard()
        model.transfers.add(StreamTransfer(live, "data".byteInputStream(), callback))
        model.cancel(historyRow)
        assertTrue(live.cancelled)
        assertFalse(Download().matches(Download()))
    }

    @Test fun cancelledSpooledBlobWritesNothingAndDeletesItsSpool() {
        val path = File(VireoLumaTVApp.instance.cacheDir, "cancelled-blob.txt")
        path.delete()
        val spoolDir = File(VireoLumaTVApp.instance.cacheDir, "test-spool").apply { deleteRecursively() }
        val spool = BlobSpool.open(spoolDir).apply { write("hello".toByteArray()) }
        val download = Download().apply {
            incognito = true
            cancelled = true
            filepath = path.absolutePath
            stream = spool.finish()
        }
        StreamTransfer(download, download.stream!!, callback).run()
        assertEquals(Download.SIZE_CANCELLED, download.size)
        assertFalse(path.exists())
        assertNull(download.stream)
        assertEquals(0, spoolDir.listFiles()!!.size)
    }

    @Test fun spooledBlobIsWrittenWholeWithItsExactSizeAndTheSpoolIsDeleted() {
        val bytes = ByteArray(3 * BlobTransfers.CHUNK_BYTES / 2 + 1) { (it % 251).toByte() }
        val path = File(VireoLumaTVApp.instance.cacheDir, "streamed-blob.bin")
        val spoolDir = File(VireoLumaTVApp.instance.cacheDir, "test-spool").apply { deleteRecursively() }
        val spool = BlobSpool.open(spoolDir)
        bytes.asList().chunked(BlobTransfers.CHUNK_BYTES).forEach { spool.write(it.toByteArray()) }
        val download = Download().apply {
            incognito = true
            filepath = path.absolutePath
            stream = spool.finish()
            size = bytes.size.toLong()
        }
        StreamTransfer(download, download.stream!!, callback).run()
        assertArrayEquals(bytes, path.readBytes())
        assertEquals(bytes.size.toLong(), download.bytesReceived)
        assertEquals(download.bytesReceived, download.size)
        assertEquals(0, spoolDir.listFiles()!!.size)
    }

    @Test fun privateLocalBinderServiceIsDeclaredInThePrivateProcess() {
        val context = VireoLumaTVApp.instance
        val privateIntent = DownloadService.intent(context, true)
        val normalIntent = DownloadService.intent(context, false)
        assertEquals(IncognitoDownloadService::class.java.name, privateIntent.component!!.className)
        assertEquals(DownloadService::class.java.name, normalIntent.component!!.className)
        val service = context.packageManager.getServiceInfo(privateIntent.component!!, 0)
        assertEquals(context.packageName + ":incognito", service.processName)
        assertFalse(service.exported)
    }
    @Test fun failedHistoryInsertStillClosesInputDeletesReservationAndReportsFailure() {
        val db = com.reiniertutoriales.vireolumatv.singleton.AppDatabase.db
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER reject_test_insert BEFORE INSERT ON downloads BEGIN SELECT RAISE(ABORT, 'history unavailable'); END")
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val reserved = File(VireoLumaTVApp.instance.cacheDir, "history-failure.bin").apply { createNewFile() }
        var closed = false
        var errors = 0
        val stream = object : java.io.ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { closed = true; super.close() }
        }
        val info = Download().apply { filepath = reserved.absolutePath; this.stream = stream }
        val listener = object : Transfer.Callback {
            override fun onProgress(task: Transfer) { fail("A failed history insert cannot start writing") }
            override fun onDone(task: Transfer) { fail("A failed history insert cannot complete") }
            override fun onError(task: Transfer, responseCode: Int, responseMessage: String) {
                errors++
                assertTrue(responseMessage.contains("history unavailable"))
            }
        }
        try {
            executor.submit { StreamTransfer(info, stream, listener).run() }.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(1, errors)
            assertEquals(Download.SIZE_FAILED, info.size)
            assertTrue(closed)
            assertNull(info.stream)
            assertFalse(reserved.exists())
        } finally {
            sql.execSQL("DROP TRIGGER reject_test_insert")
            executor.shutdownNow()
            reserved.delete()
        }
    }

    @Test fun failedHistoryUpdateDoesNotAbortTransferCompletion() {
        val db = com.reiniertutoriales.vireolumatv.singleton.AppDatabase.db
        val sql = db.openHelper.writableDatabase
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val info = Download().apply { filename = "metadata-test.bin" }
        try {
            executor.submit { DownloadRecords.open(info) }.get(5, java.util.concurrent.TimeUnit.SECONDS)
            sql.execSQL("CREATE TRIGGER reject_test_update BEFORE UPDATE ON downloads BEGIN SELECT RAISE(ABORT, 'history unavailable'); END")
            info.size = 42
            executor.submit { DownloadRecords.save(info) }.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(42L, info.size)
        } finally {
            sql.execSQL("DROP TRIGGER IF EXISTS reject_test_update")
            executor.submit { kotlinx.coroutines.runBlocking { db.downloadDao().delete(info) } }.get(5, java.util.concurrent.TimeUnit.SECONDS)
            executor.shutdownNow()
        }
    }

}
