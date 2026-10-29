package com.reiniertutoriales.vireolumatv.service.downloads

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.downloads.RunningDownloads
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.ApkInstaller
import com.reiniertutoriales.vireolumatv.utils.FileNames
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Runs downloads on two worker threads while the browser is bound to it, as a dataSync foreground
 * service with one ongoing notification for all of them. Results reach [RunningDownloads] on the
 * main thread; progress is passed on at most every [PROGRESS_INTERVAL_MS].
 */
open class DownloadService : Service() {
    private lateinit var model: RunningDownloads
    private lateinit var notifications: NotificationManager
    private val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(4))
    private val main = Handler(Looper.getMainLooper())
    private val notification by lazy {
        NotificationCompat.Builder(this, VireoLumaTVApp.DOWNLOAD_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }

    internal val transferListener = object : Transfer.Callback {
        @Volatile private var lastProgress = 0L

        override fun onProgress(task: Transfer) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastProgress < PROGRESS_INTERVAL_MS) return
            lastProgress = now
            main.post {
                model.reportProgress(task)
                if (model.runningDownloads.isNotEmpty()) notifications.notify(DOWNLOAD_NOTIFICATION, buildNotification())
            }
        }

        override fun onError(task: Transfer, responseCode: Int, responseMessage: String) {
            DownloadHistory.update(task.job)
            main.post {
                model.reportError(task, responseCode, responseMessage)
                onTransferEnded(task)
            }
        }

        override fun onDone(task: Transfer) {
            DownloadHistory.update(task.job)
            main.post {
                model.reportProgress(task)
                onTransferEnded(task)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        model = ScreenModels.get(RunningDownloads::class, this)
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onDestroy() {
        cancelAll()
        executor.shutdown()
        ScreenModels.discard(model, this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onBind(intent: Intent): IBinder = Binder(this)

    /**
     * Android 15+ ends a dataSync service after six hours in the background and requires stopSelf()
     * within seconds. The downloads are cancelled so their partial files are removed, not orphaned.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground time limit reached; cancelling ${model.runningDownloads.size} downloads")
        cancelAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun startDownload(download: Download): Boolean {
        if (model.runningDownloads.size >= MAX_OUTSTANDING) return reject(download)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !reserveLegacyFile(download)) {
            download.discardPayload()
            return false
        }
        download.time = System.currentTimeMillis()
        val task = when {
            download.base64BlobData != null -> BlobTransfer(download, download.base64BlobData!!, transferListener)
            download.stream != null -> StreamTransfer(download, download.stream!!, transferListener)
            else -> FileTransfer(download, download.userAgent, transferListener)
        }
        model.runningDownloads.add(task)
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
            model.runningDownloads.remove(task)
            return reject(download)
        }
        startService(Intent(this, javaClass))
        startForeground(DOWNLOAD_NOTIFICATION, buildNotification())
        return true
    }

    private fun reject(download: Download): Boolean {
        download.cancelled = true
        download.discardPayload()
        Toast.makeText(this, R.string.download_queue_full, Toast.LENGTH_SHORT).show()
        return false
    }

    /** Before Android 11 the file goes straight into the public Downloads folder, under a free name. */
    private fun reserveLegacyFile(download: Download): Boolean {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            Toast.makeText(this, R.string.download_storage_unavailable, Toast.LENGTH_SHORT).show()
            return false
        }
        @Suppress("DEPRECATION") // The public folder is the documented location before scoped storage.
        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!folder.isDirectory && !folder.mkdirs()) {
            Toast.makeText(this, R.string.download_folder_error, Toast.LENGTH_SHORT).show()
            return false
        }
        // The name becomes part of a path: it must never leave the Downloads folder.
        val file = freeFile(folder, FileNames.sanitizeFileName(download.filename) ?: "downloadfile")
        download.filename = file.name
        download.filepath = file.absolutePath
        return true
    }

    private fun cancelAll() = model.runningDownloads.forEach { it.job.cancelled = true }

    private fun onTransferEnded(task: Transfer) {
        val info = task.job
        if (info.size >= 0 && !info.cancelled &&
            info.afterDownload == Download.AfterDownload.INSTALL && ApkInstaller.canInstall(this)) {
            ApkInstaller.launch(this, info)
        }
        model.onDownloadEnded(task)
        if (model.runningDownloads.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val infos = model.runningDownloads.map { it.job }
        val received = infos.sumOf { it.bytesReceived }
        val sizeKnown = infos.isNotEmpty() && infos.all { it.size > 0 }
        val total = if (sizeKnown) infos.sumOf { it.size } else 0L
        return notification
            .setContentTitle(infos.joinToString(", ") { it.filename })
            .setContentText(progressText(received, total))
            .setProgress(100, if (total > 0) (received * 100 / total).toInt() else 0, total == 0L)
            .build()
    }

    /** "12 MB / 40 MB", or only the received size while some total is unknown. */
    private fun progressText(received: Long, total: Long): String {
        val done = Formatter.formatShortFileSize(this, received)
        return if (total > 0) getString(R.string.download_progress_size, done, Formatter.formatShortFileSize(this, total)) else done
    }

    class Binder(val service: DownloadService) : android.os.Binder()

    companion object {
        // Two active transfers plus four waiting tasks; this is an admission bound, not a RAM budget.
        internal const val MAX_OUTSTANDING = 6
        const val TAG = "DownloadService"
        const val DOWNLOAD_NOTIFICATION = 101101
        private const val PROGRESS_INTERVAL_MS = 100L

        fun intent(context: Context, incognito: Boolean): Intent = Intent(context,
            if (incognito) IncognitoDownloadService::class.java else DownloadService::class.java)

        /** [name] in [folder], or "name_(n).ext" with the first free n; a leading dot is not an extension. */
        internal fun freeFile(folder: File, name: String): File {
            val dot = name.lastIndexOf('.').takeIf { it > 0 }
            val stem = if (dot == null) name else name.substring(0, dot)
            val extension = if (dot == null) "" else name.substring(dot)
            var file = File(folder, name)
            var n = 0
            while (file.exists()) file = File(folder, "${stem}_(${++n})$extension")
            return file
        }
    }
}
