package com.reiniertutoriales.vireolumatv.service.downloads

import android.util.Log
import kotlinx.coroutines.runBlocking
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase

/**
 * The stored list of downloads. Private downloads never reach it. Called from transfer threads;
 * a failing write is logged and ignored, because the transfer must still close its output and
 * report its result. These synchronous transfer-thread boundaries await Room's suspending
 * writes so IDs, output creation and completion retain their required order.
 */
internal object DownloadRecords {
    fun open(download: Download) {
        if (download.incognito) return
        download.id = runBlocking { AppDatabase.db.downloadDao().insert(download) }
    }

    fun save(download: Download) {
        if (download.incognito || download.id == 0L) return
        runCatching { runBlocking { AppDatabase.db.downloadDao().update(download) } }
            .onFailure { Log.w(Transfer.TAG, "Download record not saved", it) }
    }
}
