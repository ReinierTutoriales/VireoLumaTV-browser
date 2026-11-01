package com.reiniertutoriales.vireolumatv.service.downloads

import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import java.io.File

/** Where a finished download lives: a MediaStore entry from Android 11, a plain file before it. */
object DownloadFiles {
    /** Erases the saved file, if it is still there, and then the download's record. */
    suspend fun erase(download: Download) {
        val gone = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                VireoLumaTVApp.instance.contentResolver.delete(download.filepath.toUri(), null) > 0
            } else {
                File(download.filepath).delete()
            }
        }
        gone.exceptionOrNull()?.let { Log.w(Transfer.TAG, "Saved file not erased", it) }
        if (gone.getOrNull() == false) Log.w(Transfer.TAG, "Saved file was already missing")
        AppDatabase.db.downloadDao().delete(download)
    }
}
