package com.reiniertutoriales.vireolumatv.service.downloads

import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase

internal object DownloadHistory {
    fun insert(download: Download) {
        if (!download.incognito) download.id = AppDatabase.db.downloadDao().insert(download)
    }

    fun update(download: Download) {
        if (!download.incognito && download.id != 0L) AppDatabase.db.downloadDao().update(download)
    }
}
