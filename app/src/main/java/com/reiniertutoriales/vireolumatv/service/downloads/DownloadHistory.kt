package com.reiniertutoriales.vireolumatv.service.downloads

import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase

internal object DownloadHistory {
    fun insert(download: Download) {
        if (!download.incognito) download.id = AppDatabase.db.downloadDao().insert(download)
    }

    fun update(download: Download) {
        if (!download.incognito && download.id != 0L) {
            try { AppDatabase.db.downloadDao().update(download) }
            catch (error: Exception) {
                // The transfer must still close its output and notify the UI if metadata cannot be saved.
                android.util.Log.w(Transfer.TAG, "Could not update download history", error)
            }
        }
    }
}
