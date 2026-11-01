package com.reiniertutoriales.vireolumatv.utils

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ApkInstallerTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val installer: PackageInstaller get() = app.packageManager.packageInstaller
    private val now: (() -> Unit) -> Unit = { it() }

    @Before fun attachProviderToThisTestsStorage() {
        org.robolectric.shadows.ShadowEnvironment.setExternalStoragePublicDirectory(
            android.os.Environment.getExternalStorageDirectory().toPath())
        // Android attaches a fresh provider per process. Reset its cached path strategy for the
        // fresh Robolectric storage roots without changing the production paths or assertions.
        val info = requireNotNull(app.packageManager.resolveContentProvider(app.packageName + ".provider",
            android.content.pm.PackageManager.GET_META_DATA))
        androidx.core.content.FileProvider().attachInfo(app, info)
    }

    private fun download(path: String) = Download().apply { filepath = path; filename = "app.apk" }

    private fun sharedApk(name: String) = File(android.os.Environment.getExternalStorageDirectory(), "Download/$name")
        .apply { parentFile!!.mkdirs(); writeText("apk") }

    @Test fun aSharedDownloadIsCopiedIntoAnInstallSessionMarkedAsADownloadedFile() {
        ApkInstaller.launch(app, download(sharedApk("ok.apk").absolutePath), now)
        val session = installer.allSessions.single()
        assertEquals(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE, session.packageSource)
        assertNull("Nothing is started before the system asks for confirmation", shadowOf(app).nextStartedActivity)
    }

    @Test fun theSystemConfirmationIsShownWhenTheSessionAsksForIt() {
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")
        ApkInstaller.Result().onReceive(app, Intent()
            .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
            .putExtra(Intent.EXTRA_INTENT, confirm))
        val started = shadowOf(app).nextStartedActivity
        assertEquals(confirm.action, started.action)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun aFailedInstallIsReportedButACancelledOneIsNot() {
        ApkInstaller.Result().onReceive(app, Intent().putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_ABORTED))
        assertNull(ShadowToast.getLatestToast())
        ApkInstaller.Result().onReceive(app, Intent().putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_INVALID))
        assertEquals(app.getString(R.string.install_failed), ShadowToast.getTextOfLatestToast())
    }

    @Test fun theCopiedSessionIsCommittedAndSuccessIsSilent() {
        ApkInstaller.launch(app, download(sharedApk("done.apk").absolutePath), now)
        // Only a committed session has a status receiver for the system to answer.
        shadowOf(installer).setSessionSucceeds(installer.allSessions.single().sessionId)
        ApkInstaller.Result().onReceive(app, Intent().putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS))
        ShadowLooper.idleMainLooper()
        assertNull(ShadowToast.getLatestToast())
    }

    @Test fun filesTheProviderDoesNotShareAreNeverRead() {
        for (file in listOf(File(app.cacheDir, "favicons/private.png"), File("/data/not-shared/app.apk"),
            File(android.os.Environment.getExternalStorageDirectory(), "Documents/private.txt"))) {
            file.parentFile?.mkdirs()
            runCatching { file.writeText("private") }
            ApkInstaller.launch(app, download(file.absolutePath), now)
        }
        assertTrue("No session was opened", installer.allSessions.isEmpty())
        assertEquals(app.getString(R.string.install_open_failed), ShadowToast.getTextOfLatestToast())
    }

    @Test fun anUnreadableSourceAbandonsTheSessionAndSaysSo() {
        val uri = android.net.Uri.parse("content://com.example.missing/app.apk")
        shadowOf(app.contentResolver).registerInputStream(uri, object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("gone")
        })
        ApkInstaller.launch(app, download(uri.toString()), now)
        ShadowLooper.idleMainLooper()
        assertTrue("The half-made session is abandoned", installer.allSessions.isEmpty())
        assertEquals(app.getString(R.string.install_open_failed), ShadowToast.getTextOfLatestToast())
    }
}
