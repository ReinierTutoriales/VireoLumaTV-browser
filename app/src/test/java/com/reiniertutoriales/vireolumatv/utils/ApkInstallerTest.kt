package com.reiniertutoriales.vireolumatv.utils

import android.app.Application
import android.content.Intent
import com.reiniertutoriales.vireolumatv.model.Download
import org.junit.Assert.assertEquals
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
    fun fromAnActivityNewTaskIsNotAdded() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        ApkInstaller.launch(activity, download("content://com.example.provider/downloads/app.apk"))

        val started: Intent = shadowOf(activity).nextStartedActivity ?: error("No activity was started")
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
