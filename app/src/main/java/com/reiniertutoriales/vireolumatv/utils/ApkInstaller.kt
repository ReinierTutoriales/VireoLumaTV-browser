package com.reiniertutoriales.vireolumatv.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Download
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Installs a downloaded APK through a [PackageInstaller] session, the documented replacement for
 * the deprecated ACTION_INSTALL_PACKAGE. The system still asks the user to confirm; the outcome
 * comes back to [Result], so a failed install is reported instead of passing unnoticed.
 */
object ApkInstaller {
    private const val TAG = "ApkInstaller"

    /** Whether the system lets this app hand APKs to the installer (per app from Android 8). */
    fun canInstall(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        @Suppress("DEPRECATION")
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1
    }

    /** The settings screen where the user allows installs: this app's page from Android 8, else Security. */
    fun permissionSettings(context: Context): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }

    /**
     * Copies [download] into a new install session on [worker] and commits it. Only a MediaStore
     * entry or a file the app's FileProvider shares (the Downloads folder, verified updates) is read:
     * any other path is refused, as before.
     */
    fun launch(context: Context, download: Download, worker: (() -> Unit) -> Unit = { thread(name = TAG) { it() } }) {
        val app = context.applicationContext
        val source = try {
            sourceOf(app, download)
        } catch (_: IllegalArgumentException) {
            return failed(app)
        }
        worker {
            try {
                commit(app, source)
            } catch (error: Exception) { // I/O, a revoked grant or an installer refusal: never crash the worker.
                Log.w(TAG, "Install session not committed", error)
                Handler(Looper.getMainLooper()).post { failed(app) }
            }
        }
    }

    private fun sourceOf(context: Context, download: Download): Uri =
        if (download.filepath.startsWith("content://")) download.filepath.toUri()
        else FileProvider.getUriForFile(context, context.packageName + ".provider", File(download.filepath))

    private fun commit(context: Context, source: Uri) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Marks the app as installed from a downloaded file, as Android 13+ expects for sideloads.
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                val input = context.contentResolver.openInputStream(source) ?: throw IOException("No APK at $source")
                input.use { apk ->
                    session.openWrite("base.apk", 0, -1).use { out ->
                        apk.copyTo(out)
                        session.fsync(out)
                    }
                }
                session.commit(Result.sender(context, id))
            }
        } catch (error: Exception) {
            installer.abandonSession(id)
            throw error
        }
    }

    private fun failed(context: Context) =
        Toast.makeText(context, R.string.install_open_failed, Toast.LENGTH_SHORT).show()

    /** Receives the session outcome: shows the system confirmation, or says why the install failed. */
    class Result : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!intent.hasExtra(PackageInstaller.EXTRA_STATUS)) return
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    try {
                        context.startActivity(confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return failed(context))
                    } catch (error: RuntimeException) { // No installer screen, or not allowed from here.
                        Log.w(TAG, "Install confirmation not shown", error)
                        failed(context)
                    }
                }
                PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Unit // Installed, or the user said no.
                else -> {
                    Log.w(TAG, "Install failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                    Toast.makeText(context, R.string.install_failed, Toast.LENGTH_LONG).show()
                }
            }
        }

        internal companion object {
            /** The system fills in the status extras, so the PendingIntent must be mutable; it is explicit. */
            fun sender(context: Context, session: Int) = PendingIntent.getBroadcast(context, session,
                Intent(context, Result::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            ).intentSender
        }
    }
}
