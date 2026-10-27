package com.reiniertutoriales.vireolumatv.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Download
import java.io.File

/** Hands a downloaded APK to the system package installer. */
object ApkInstaller {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

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
