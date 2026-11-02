package com.reiniertutoriales.vireolumatv.activity.downloads

import androidx.annotation.StringRes
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Download

/** Where a download stands, read from the sizes stored with it. */
enum class DownloadState {
    RUNNING, SAVED, FAILED, CANCELLED;

    companion object {
        fun of(download: Download): DownloadState = when {
            download.size == Download.SIZE_FAILED -> FAILED
            download.size == Download.SIZE_CANCELLED -> CANCELLED
            download.size >= 0 && download.size == download.bytesReceived -> SAVED
            else -> RUNNING
        }
    }
}

/** What a long press offers for one download, in menu order. */
enum class DownloadAction(@StringRes val label: Int) {
    CANCEL(R.string.action_cancel),
    INSTALL(R.string.download_install),
    SHOW_FOLDER(R.string.download_open_folder),
    DELETE(R.string.action_delete);

    companion object {
        /** A running download can only be cancelled; an ended one can be shown or deleted, and a saved APK installed. */
        fun available(download: Download): List<DownloadAction> {
            val state = DownloadState.of(download)
            if (state == DownloadState.RUNNING) return listOf(CANCEL)
            val apk = state == DownloadState.SAVED && download.filename.endsWith(".apk", ignoreCase = true)
            return listOfNotNull(INSTALL.takeIf { apk }, SHOW_FOLDER, DELETE)
        }
    }
}
