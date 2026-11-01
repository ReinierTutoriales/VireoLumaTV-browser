package com.reiniertutoriales.vireolumatv.service.downloads

import android.util.Log
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase

/**
 * The stored list of downloads. Private downloads never reach it. Called from transfer threads;
 * a failing write is logged and ignored, because the transfer must still close its output and
 * report its result.
 */
internal object DownloadRecords {
    fun open(download: Download) {
        if (download.incognito) return
        download.id = AppDatabase.db.downloadDao().insert(download)
    }

    fun save(download: Download) {
        if (download.incognito || download.id == 0L) return
        runCatching { AppDatabase.db.downloadDao().update(download) }
            .onFailure { Log.w(Transfer.TAG, "Download record not saved", it) }
    }
}
