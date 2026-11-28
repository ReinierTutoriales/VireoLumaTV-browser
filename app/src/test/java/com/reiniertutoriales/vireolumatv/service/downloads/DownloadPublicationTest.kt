package com.reiniertutoriales.vireolumatv.service.downloads

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [30])
class DownloadPublicationTest {
    @Test fun publishesBeforeReportingSuccess() = assertPublication(1, null)

    @Test fun reportsFailureWhenNoRowWasPublished() = assertPublication(0, Download.SIZE_FAILED)

    @Test fun reportsFailureWhenPublicationThrows() = assertPublication(null, Download.SIZE_FAILED)

    private fun assertPublication(updatedRows: Int?, expectedFailure: Long?) {
        val file = File.createTempFile("download", ".txt", VireoLumaTVApp.instance.cacheDir)
        val events = mutableListOf<String>()
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri): String? = null
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?) = null
            override fun insert(uri: Uri, values: ContentValues?) =
                uri.buildUpon().appendPath("1").build()
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY)
            override fun update(uri: Uri, values: ContentValues?, selection: String?,
                selectionArgs: Array<out String>?): Int {
                assertEquals(0, values?.getAsInteger(MediaStore.Downloads.IS_PENDING))
                events += "publish"
                return updatedRows ?: throw SecurityException("Publication refused")
            }
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
                events += "delete"
                return 1
            }
        }
        provider.attachInfo(VireoLumaTVApp.instance, ProviderInfo().apply {
            authority = MediaStore.AUTHORITY
            exported = true
        })
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
        val job = Download().apply {
            incognito = true
            filename = "download.txt"
        }
        var errorMessage: String? = null
        val callback = object : Transfer.Callback {
            override fun onProgress(task: Transfer) {}
            override fun onError(task: Transfer, responseCode: Int, responseMessage: String) {
                errorMessage = responseMessage
                events += "error"
            }
            override fun onDone(task: Transfer) {
                events += "done"
            }
        }
        try {
            StreamTransfer(job, "hello".byteInputStream(), callback).run()
            assertEquals(errorMessage, expectedFailure ?: 5L, job.size)
            assertEquals(
                errorMessage,
                if (expectedFailure == null) listOf("publish", "done") else listOf("publish", "delete", "error"),
                events,
            )
        } finally {
            file.delete()
        }
    }
}
