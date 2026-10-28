package com.reiniertutoriales.vireolumatv.service.downloads

import android.content.ComponentName
import android.os.Build
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.downloads.RunningDownloads
import com.reiniertutoriales.vireolumatv.model.Download
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
        val model = RunningDownloads()
        model.activeDownloads.add(StreamTransfer(first, "one".byteInputStream(), callback))
        model.activeDownloads.add(StreamTransfer(second, "two".byteInputStream(), callback))
        model.cancelDownload(second)
        assertFalse(first.cancelled)
        assertTrue(second.cancelled)
    }

    @Test fun normalHistoryCopyCanStillCancelItsActiveTaskByNonzeroId() {
        val live = Download().apply { id = 42 }
        val historyRow = Download().apply { id = 42 }
        val model = RunningDownloads()
        model.activeDownloads.add(StreamTransfer(live, "data".byteInputStream(), callback))
        model.cancelDownload(historyRow)
        assertTrue(live.cancelled)
        assertFalse(Download().matches(Download()))
    }

    @Test fun cancelledBlobDoesNotWriteAFileAndReleasesItsBridgeString() {
        val path = File(VireoLumaTVApp.instance.cacheDir, "cancelled-blob.txt")
        path.delete()
        val download = Download().apply {
            incognito = true
            cancelled = true
            filepath = path.absolutePath
            base64BlobData = "data:text/plain;base64,aGVsbG8="
        }
        BlobTransfer(download, download.base64BlobData!!, callback).run()
        assertEquals(Download.CANCELLED_MARK, download.size)
        assertFalse(path.exists())
        assertNull(download.base64BlobData)
    }

    @Test fun blobDecodesAcrossBufferBoundariesAndReportsTheExactSize() {
        val bytes = ByteArray(40_001) { (it % 251).toByte() }
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val path = File(VireoLumaTVApp.instance.cacheDir, "streamed-blob.bin")
        val download = Download().apply {
            incognito = true
            filepath = path.absolutePath
            base64BlobData = "data:application/octet-stream;base64,$encoded"
        }
        BlobTransfer(download, download.base64BlobData!!, callback).run()
        assertArrayEquals(bytes, path.readBytes())
        assertEquals(bytes.size.toLong(), download.bytesReceived)
        assertEquals(download.bytesReceived, download.size)
        assertNull(download.base64BlobData)
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
}
