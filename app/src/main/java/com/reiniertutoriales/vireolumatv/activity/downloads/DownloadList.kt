package com.reiniertutoriales.vireolumatv.activity.downloads

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.withDayHeaders

/**
 * The downloads on screen, with a date header before each day, as Compose state. A progress
 * update changes the matching download in place and bumps [revision]; [rowCount] is the offset
 * of the next stored page.
 */
class DownloadList {
    private val realItems = ArrayList<Download>()

    /** Headers and downloads in display order. */
    var rows by mutableStateOf<List<Download>>(emptyList())
        private set

    /** Changes with every progress update; rows read it to redraw. */
    var revision by mutableIntStateOf(0)
        private set

    val rowCount: Long get() = realItems.size.toLong()

    fun addItems(items: List<Download>) {
        if (items.isEmpty()) return
        realItems.addAll(items)
        update()
    }

    fun addLiveItem(download: Download) {
        if (realItems.none { it.matches(download) }) addItems(listOf(download))
    }

    fun remove(download: Download) {
        realItems.remove(download)
        update()
    }

    /** Copies a progress or error update into the download shown for the same task. */
    fun updateDownload(info: Download) {
        val row = realItems.firstOrNull { it.matches(info) } ?: return
        row.filename = info.filename
        row.filepath = info.filepath
        row.size = info.size
        row.bytesReceived = info.bytesReceived
        normalizeSize(row)
        revision++
    }

    private fun update() {
        realItems.forEach(::normalizeSize)
        rows = withDayHeaders(realItems, { it.time }, Download::dayHeader)
    }

    private companion object {
        /** A wrong size from the server is ignored for future updates, as before. */
        fun normalizeSize(download: Download) {
            if (download.size > 0 && download.bytesReceived > download.size) download.size = 0L
        }
    }
}

/**
 * What a download row shows about its progress: the size text, whether it is an error, and the bar
 * ([fraction] in 0..1, or [indeterminate] when the total is unknown; neither once it ended).
 */
data class DownloadProgress(val text: String, val error: Boolean = false, val fraction: Float? = null,
                            val indeterminate: Boolean = false) {
    companion object {
        fun of(context: Context, download: Download): DownloadProgress {
            fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
            return when {
                download.size == Download.SIZE_CANCELLED -> DownloadProgress(context.getString(R.string.download_cancelled))
                download.size == Download.SIZE_FAILED -> DownloadProgress(context.getString(R.string.download_failed), error = true)
                download.size <= 0L -> DownloadProgress(size(download.bytesReceived), indeterminate = true)
                download.size == download.bytesReceived -> DownloadProgress(size(download.size))
                else -> DownloadProgress(
                    context.getString(R.string.download_progress_size, size(download.bytesReceived), size(download.size)),
                    fraction = download.bytesReceived.toFloat() / download.size,
                )
            }
        }
    }
}
