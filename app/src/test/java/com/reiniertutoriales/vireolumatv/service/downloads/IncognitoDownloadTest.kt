package com.reiniertutoriales.vireolumatv.service.downloads

import android.provider.MediaStore
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class IncognitoDownloadTest {
    @Before fun resetHistory() { AppDatabase.db.clearAllTables() }

    private fun download(privateMode: Boolean) = Download().apply {
        incognito = privateMode
        filename = "test.txt"
        filepath = File(VireoLumaTVApp.instance.cacheDir, "test.txt").absolutePath
        url = "https://example.com/private-file"
        referer = "https://example.com/private-page"
    }

    private val callback = object : DownloadTask.Callback {
        override fun onProgress(task: DownloadTask) {}
        override fun onError(task: DownloadTask, responseCode: Int, responseMessage: String) { DownloadHistory.update(task.downloadInfo) }
        override fun onDone(task: DownloadTask) { DownloadHistory.update(task.downloadInfo) }
    }

    @Test fun privateStreamAndBlobDownloadsWriteFilesButLeaveNoHistory() = runBlocking {
        val stream = download(true)
        StreamDownloadTask(stream, "hello".byteInputStream(), callback).run()
        assertEquals("hello", File(stream.filepath).readText())
        assertEquals(0L, stream.id)
        val blob = download(true)
        BlobDownloadTask(blob, "data:text/plain;base64,d29ybGQ=", callback).run()
        assertEquals("world", File(blob.filepath).readText())
        assertEquals(0L, blob.id)
        assertTrue(AppDatabase.db.downloadDao().getAll().isEmpty())
    }

    @Test fun privateFailureAndCancellationLeaveNoHistory() = runBlocking {
        val broken = download(true)
        val input = object : InputStream() { override fun read(): Int = throw IOException("test failure") }
        StreamDownloadTask(broken, input, callback).run()
        assertEquals(Download.BROKEN_MARK, broken.size)
        val cancelled = download(true).apply { this.cancelled = true }
        StreamDownloadTask(cancelled, "hello".byteInputStream(), callback).run()
        assertEquals(Download.CANCELLED_MARK, cancelled.size)
        val badUrl = download(true).apply { url = "not a URL" }
        FileDownloadTask(badUrl, null, callback).run()
        assertEquals(Download.BROKEN_MARK, badUrl.size)
        assertTrue(AppDatabase.db.downloadDao().getAll().isEmpty())
    }

    @Test fun ordinaryDownloadStillRecordsCompletionAndMediaStoreKeepsItsSource() = runBlocking {
        val ordinary = download(false)
        StreamDownloadTask(ordinary, "hello".byteInputStream(), callback).run()
        val row = AppDatabase.db.downloadDao().getAll().single()
        assertEquals(5L, row.bytesReceived)
        assertEquals(ordinary.url, row.url)
        assertTrue(ordinary.id > 0L)
        val details = buildDownloadDetails(ordinary)
        assertEquals(ordinary.url, details.getAsString(MediaStore.Downloads.DOWNLOAD_URI))
        assertEquals(ordinary.referer, details.getAsString(MediaStore.Downloads.REFERER_URI))
    }

    @Test fun privateMediaStoreDetailsRetainFilenameButOmitBrowsingMetadata() {
        val details = buildDownloadDetails(download(true))
        assertEquals("test.txt", details.getAsString(MediaStore.Downloads.DISPLAY_NAME))
        assertFalse(details.containsKey(MediaStore.Downloads.DOWNLOAD_URI))
        assertFalse(details.containsKey(MediaStore.Downloads.REFERER_URI))
    }
}
