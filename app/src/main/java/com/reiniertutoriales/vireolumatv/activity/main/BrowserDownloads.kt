package com.reiniertutoriales.vireolumatv.activity.main

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.service.downloads.DownloadService

/**
 * The downloads the browser window starts. A download waits in [pending] until the download
 * service is bound and, when the system needs it, storage (before Android 11) or notification
 * (Android 13+) permission has been answered; then the queue is handed to the service in order.
 */
internal class BrowserDownloads(
    private val activity: Activity,
    private val incognito: () -> Boolean,
    private val onStarted: (fileName: String) -> Unit,
) {
    @VisibleForTesting internal val pending = ArrayDeque<Download>()
    /** A permission dialog is open; new downloads only join the queue. */
    @VisibleForTesting internal var waitingForPermission = false
    private var service: DownloadService? = null
    private var bound = false
    private val main = Handler(Looper.getMainLooper())

    /** Queues [download]; callable from any thread (blob downloads come from the JavaScript bridge). */
    fun request(download: Download) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { request(download) }
            return
        }
        download.incognito = incognito()
        pending.addLast(download)
        if (waitingForPermission) return
        val needed = neededPermission()
        if (needed == null) {
            startPending()
        } else {
            waitingForPermission = true
            activity.requestPermissions(arrayOf(needed.first), needed.second)
        }
    }

    private fun neededPermission(): Pair<String, Int>? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !granted(Manifest.permission.WRITE_EXTERNAL_STORAGE) ->
            Manifest.permission.WRITE_EXTERNAL_STORAGE to REQUEST_STORAGE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS) ->
            Manifest.permission.POST_NOTIFICATIONS to REQUEST_NOTIFICATIONS
        else -> null
    }

    private fun granted(permission: String) = activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** True when [requestCode] was one of these permission requests. */
    fun onPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        when (requestCode) {
            // Notifications only show progress; downloading does not need them.
            REQUEST_NOTIFICATIONS -> {
                waitingForPermission = false
                startPending()
            }
            // An interrupted request (empty results) also releases the queue's wait.
            REQUEST_STORAGE -> {
                waitingForPermission = false
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startPending() else pending.clear()
            }
            else -> return false
        }
        return true
    }

    fun bind() {
        bound = activity.bindService(DownloadService.intent(activity, incognito()), connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        // unbindService() throws for a bind that did not succeed.
        if (bound) activity.unbindService(connection)
        bound = false
        service = null
    }

    private fun startPending() {
        val service = service ?: return
        while (pending.isNotEmpty()) {
            val download = pending.removeFirst()
            service.startDownload(download)
            onStarted(download.filename)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val local = binder as? DownloadService.Binder
            if (local == null) {
                // Right after a private-mode switch the service can still be the other process's: retry.
                Log.e(TAG, "Download service of another process; binding again")
                main.postDelayed({
                    if (!activity.isDestroyed) {
                        activity.bindService(DownloadService.intent(activity, incognito()), this, Context.BIND_AUTO_CREATE)
                    }
                }, REBIND_DELAY_MS)
                return
            }
            service = local.service
            startPending()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    companion object {
        private const val TAG = "BrowserDownloads"
        const val REQUEST_NOTIFICATIONS = 10003
        const val REQUEST_STORAGE = 10004
        private const val REBIND_DELAY_MS = 1000L
    }
}
