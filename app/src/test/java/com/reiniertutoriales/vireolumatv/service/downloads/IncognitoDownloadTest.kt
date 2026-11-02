package com.reiniertutoriales.vireolumatv.service.downloads

import android.provider.MediaStore
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    @Before fun resetHistory() = runBlocking { withContext(Dispatchers.IO) { AppDatabase.db.clearAllTables() } }

    private fun download(privateMode: Boolean) = Download().apply {
        incognito = privateMode
        filename = "test.txt"
        filepath = File(VireoLumaTVApp.instance.cacheDir, "test.txt").absolutePath
        url = "https://example.com/private-file"
        referer = "https://example.com/private-page"
    }

    private val callback = object : Transfer.Callback {
        override fun onProgress(task: Transfer) {}
        override fun onError(task: Transfer, responseCode: Int, responseMessage: String) { DownloadRecords.save(task.job) }
        override fun onDone(task: Transfer) { DownloadRecords.save(task.job) }
    }

    @Test fun privateStreamDownloadsWriteFilesButLeaveNoHistory() = runBlocking {
        val stream = download(true)
        StreamTransfer(stream, "hello".byteInputStream(), callback).run()
        assertEquals("hello", File(stream.filepath).readText())
        assertEquals(0L, stream.id)
        assertTrue(AppDatabase.db.downloadDao().getAll().isEmpty())
    }

    @Test fun privateFailureAndCancellationLeaveNoHistory() = runBlocking {
        val broken = download(true)
        val input = object : InputStream() { override fun read(): Int = throw IOException("test failure") }
        StreamTransfer(broken, input, callback).run()
        assertEquals(Download.SIZE_FAILED, broken.size)
        val cancelled = download(true).apply { this.cancelled = true }
        StreamTransfer(cancelled, "hello".byteInputStream(), callback).run()
        assertEquals(Download.SIZE_CANCELLED, cancelled.size)
        val badUrl = download(true).apply { url = "not a URL" }
        FileTransfer(badUrl, null, callback).run()
        assertEquals(Download.SIZE_FAILED, badUrl.size)
        assertTrue(AppDatabase.db.downloadDao().getAll().isEmpty())
    }

    @Test fun ordinaryDownloadStillRecordsCompletionAndMediaStoreKeepsItsSource() = runBlocking {
        val ordinary = download(false)
        // Downloads run on DownloadService's pool; Room rejects the DAO write on the test's main thread.
        withContext(Dispatchers.IO) { StreamTransfer(ordinary, "hello".byteInputStream(), callback).run() }
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
