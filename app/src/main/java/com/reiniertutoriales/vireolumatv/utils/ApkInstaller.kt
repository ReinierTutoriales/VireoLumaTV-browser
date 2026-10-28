package com.reiniertutoriales.vireolumatv.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Download
import java.io.File

/** Hands a downloaded APK to the system package installer. */
object ApkInstaller {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

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

    fun launch(context: Context, download: Download) {
        val install = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val apkUri = if (download.filepath.startsWith("content://")) {
                download.filepath.toUri()
            } else {
                FileProvider.getUriForFile(context, context.applicationContext.packageName + ".provider",
                    File(download.filepath))
            }
            install.setDataAndType(apkUri, APK_MIME_TYPE)
            context.startActivity(install)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            // The file is outside the FileProvider paths.
            Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }
}
