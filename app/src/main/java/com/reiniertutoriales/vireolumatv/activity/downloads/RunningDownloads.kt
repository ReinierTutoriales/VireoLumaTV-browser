package com.reiniertutoriales.vireolumatv.activity.downloads

import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.service.downloads.Transfer
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModel
import com.reiniertutoriales.vireolumatv.utils.observable.LiveList
import java.io.File

/** Downloads running in this process and the screens that follow them; used on the main thread. */
class RunningDownloads : ScreenModel() {
    val runningDownloads = LiveList<Transfer>()
    private val listeners = ArrayList<Listener>()

    interface Listener {
        fun onDownloadUpdated(job: Download)
        fun onDownloadError(job: Download, responseCode: Int, responseMessage: String)
        fun onIdle()
    }

    fun registerListener(listener: Listener) {
        listeners += listener
    }

    fun unregisterListener(listener: Listener) {
        listeners -= listener
    }

    /** Cancels the running task for [download]; its task removes the partial file. */
    fun cancelDownload(download: Download) {
        runningDownloads.firstOrNull { it.job.matches(download) }?.job?.cancelled = true
    }

    /** Removes a finished download's file and its history row. */
    suspend fun deleteItem(download: Download) {
        try {
            val removed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                VireoLumaTVApp.instance.contentResolver.delete(download.filepath.toUri(), null) > 0
            } else {
                File(download.filepath).delete()
            }
            if (!removed) Log.w(Transfer.TAG, "Downloaded file was already gone")
        } catch (error: Exception) {
            Log.w(Transfer.TAG, "Could not delete the downloaded file", error)
        }
        AppDatabase.db.downloadDao().delete(download)
    }

    fun reportError(task: Transfer, responseCode: Int, responseMessage: String) =
        listeners.toList().forEach { it.onDownloadError(task.job, responseCode, responseMessage) }

    fun reportProgress(task: Transfer) =
        listeners.toList().forEach { it.onDownloadUpdated(task.job) }

    fun onDownloadEnded(task: Transfer) {
        runningDownloads.remove(task)
        if (runningDownloads.isEmpty()) listeners.toList().forEach { it.onIdle() }
    }
}
