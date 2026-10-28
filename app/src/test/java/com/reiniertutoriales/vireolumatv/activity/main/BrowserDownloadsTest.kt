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
}
