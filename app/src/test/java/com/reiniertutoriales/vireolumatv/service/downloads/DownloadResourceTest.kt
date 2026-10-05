package com.reiniertutoriales.vireolumatv.service.downloads

import android.content.ComponentName
import android.os.Build
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.downloads.ActiveDownloadsModel
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
    private val callback = object : DownloadTask.Callback {
        override fun onProgress(task: DownloadTask) {}
        override fun onError(task: DownloadTask, responseCode: Int, responseMessage: String) {}
        override fun onDone(task: DownloadTask) {}
    }

    @Test fun cancellingOnePrivateDownloadDoesNotCancelAnotherWithTheSameZeroId() {
        val first = Download().apply { incognito = true }
        val second = Download().apply { incognito = true }
        val model = ActiveDownloadsModel()
        model.activeDownloads.add(StreamDownloadTask(first, "one".byteInputStream(), callback))
        model.activeDownloads.add(StreamDownloadTask(second, "two".byteInputStream(), callback))
        model.cancelDownload(second)
        assertFalse(first.cancelled)
        assertTrue(second.cancelled)
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
        BlobDownloadTask(download, download.base64BlobData!!, callback).run()
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
        BlobDownloadTask(download, download.base64BlobData!!, callback).run()
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
