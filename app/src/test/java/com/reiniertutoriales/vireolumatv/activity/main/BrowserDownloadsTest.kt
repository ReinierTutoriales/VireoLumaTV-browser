package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Activity
import android.content.pm.PackageManager
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class)
class BrowserDownloadsTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val downloads = BrowserDownloads(activity, { false }) {}
    private fun file(name: String) = Download().apply { url = "https://files.test/$name"; filename = name }

    @Config(sdk = [28])
    @Test fun storageRefusedDropsTheQueueAndLaterDownloadsAskAgain() {
        downloads.request(file("a.zip"))
        downloads.request(file("b.zip"))
        assertTrue(downloads.waitingForPermission)
        assertEquals(BrowserDownloads.REQUEST_STORAGE, shadowOf(activity).lastRequestedPermission.requestCode)
        assertTrue(downloads.onPermissionsResult(BrowserDownloads.REQUEST_STORAGE, intArrayOf(PackageManager.PERMISSION_DENIED)))
        assertFalse(downloads.waitingForPermission)
        assertTrue(downloads.pending.isEmpty())
        downloads.request(file("c.zip"))
        assertTrue("A refused permission is asked for again next time", downloads.waitingForPermission)
    }

    @Config(sdk = [33])
    @Test fun notificationAnswerReleasesTheQueueEvenWhenRefused() {
        shadowOf(activity.application).grantPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        downloads.request(file("a.zip"))
        assertTrue(downloads.waitingForPermission)
        downloads.onPermissionsResult(BrowserDownloads.REQUEST_NOTIFICATIONS, intArrayOf(PackageManager.PERMISSION_DENIED))
        assertFalse(downloads.waitingForPermission)
        assertEquals("Without a bound service the download keeps waiting in order", listOf("a.zip"), downloads.pending.map { it.filename })
        assertFalse("Other request codes are not the queue's", downloads.onPermissionsResult(1, intArrayOf()))
    }
    private class TrackedStream : java.io.ByteArrayInputStream(byteArrayOf(1)) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Config(sdk = [28])
    @Test fun deniedPermissionClosesQueuedStreams() {
        val stream = TrackedStream()
        val info = file("stream.zip").apply { this.stream = stream }
        downloads.request(info)
        downloads.onPermissionsResult(BrowserDownloads.REQUEST_STORAGE, intArrayOf(PackageManager.PERMISSION_DENIED))
        assertTrue(stream.closed)
        assertTrue(info.cancelled)
        assertEquals(null, info.stream)
    }

    @Config(sdk = [28])
    @Test fun serviceBindingCannotBypassAnOutstandingPermissionAnswer() {
        downloads.request(file("waiting.zip"))
        val controller = Robolectric.buildService(com.reiniertutoriales.vireolumatv.service.downloads.DownloadService::class.java).create()
        try {
            val connection = BrowserDownloads::class.java.getDeclaredField("connection")
                .apply { isAccessible = true }.get(downloads) as android.content.ServiceConnection
            connection.onServiceConnected(android.content.ComponentName(activity, controller.get().javaClass),
                com.reiniertutoriales.vireolumatv.service.downloads.DownloadService.Binder(controller.get()))
            assertTrue(downloads.waitingForPermission)
            assertEquals(1, downloads.pending.size)
            val model = com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels.get(
                com.reiniertutoriales.vireolumatv.activity.downloads.RunningDownloads::class, controller.get())
            assertTrue("No transfer may start before the permission answer", model.runningDownloads.isEmpty())
        } finally { downloads.unbind(); controller.destroy() }
    }

    @Config(sdk = [28])
    @Test fun pendingAdmissionIsBoundedAndAbandonedStreamsAreClosed() {
        val accepted = (1..com.reiniertutoriales.vireolumatv.service.downloads.DownloadService.MAX_OUTSTANDING).map {
            TrackedStream().also { stream -> downloads.request(file("$it.zip").apply { this.stream = stream }) }
        }
        val rejected = TrackedStream()
        downloads.request(file("overflow.zip").apply { stream = rejected })
        assertEquals(accepted.size, downloads.pending.size)
        assertTrue(rejected.closed)
        assertEquals(activity.getString(com.reiniertutoriales.vireolumatv.R.string.download_queue_full),
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
        downloads.unbind()
        assertTrue(downloads.pending.isEmpty())
        assertTrue(accepted.all { it.closed })
    }

}
