package com.reiniertutoriales.vireolumatv.utils

import android.app.Application
import android.content.Intent
import com.reiniertutoriales.vireolumatv.model.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ApkInstallerTest {
    private fun download(path: String) = Download().apply { filepath = path; filename = "app.apk" }

    @Test
    fun contentUriIsPassedAsIsWithInstallerTypeAndReadPermission() {
        val app = RuntimeEnvironment.getApplication()
        val uri = "content://com.example.provider/downloads/app.apk"

        ApkInstaller.launch(app, download(uri))

        val started: Intent = shadowOf(app).nextStartedActivity ?: error("No activity was started")
        assertEquals(Intent.ACTION_INSTALL_PACKAGE, started.action)
        assertEquals(uri, started.data.toString())
        assertEquals("application/vnd.android.package-archive", started.type)
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue("Starting from a non-Activity context needs NEW_TASK",
            started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun verifiedUpdateDirectoryIsSharedThroughTheProviderAuthority() {
        val app = RuntimeEnvironment.getApplication()
        val apk = java.io.File(app.cacheDir, "updates/app.apk").apply { parentFile!!.mkdirs(); writeText("apk") }

        ApkInstaller.launch(app, download(apk.absolutePath))

        val started: Intent = shadowOf(app).nextStartedActivity ?: error("No activity was started")
        assertEquals("content", started.data?.scheme)
        assertEquals(app.packageName + ".provider", started.data?.authority)
    }

    @Test
    fun fileOutsideTheProviderPathsDoesNotCrashOrStartInstaller() {
        val app = RuntimeEnvironment.getApplication()

        ApkInstaller.launch(app, download("/data/not-shared/app.apk"))

        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun unrelatedCacheAndExternalFilesCannotBeSharedWithTheInstaller() {
        val app = RuntimeEnvironment.getApplication()
        for (file in listOf(java.io.File(app.cacheDir, "favicons/private.png"),
            java.io.File(android.os.Environment.getExternalStorageDirectory(), "Documents/private.txt"))) {
            file.parentFile!!.mkdirs()
            file.writeText("private")
            ApkInstaller.launch(app, download(file.absolutePath))
            assertNull(shadowOf(app).nextStartedActivity)
        }
    }

    @Test
    fun legacyPublicDownloadCanStillBeInstalled() {
        val app = RuntimeEnvironment.getApplication()
        // Robolectric keeps separate default roots for these two Environment APIs.
        org.robolectric.shadows.ShadowEnvironment.setExternalStoragePublicDirectory(
            android.os.Environment.getExternalStorageDirectory().toPath())
        val file = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "legacy.apk").apply {
            parentFile!!.mkdirs(); writeText("apk")
        }
        ApkInstaller.launch(app, download(file.absolutePath))
        assertEquals("content", shadowOf(app).nextStartedActivity?.data?.scheme)
    }

    @Test
    fun fromAnActivityNewTaskIsNotAdded() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        ApkInstaller.launch(activity, download("content://com.example.provider/downloads/app.apk"))

        val started: Intent = shadowOf(activity).nextStartedActivity ?: error("No activity was started")
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
