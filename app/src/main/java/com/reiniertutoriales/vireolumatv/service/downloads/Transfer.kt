package com.reiniertutoriales.vireolumatv.service.downloads

import android.content.ContentValues
import android.os.Build
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.provider.MediaStore
import android.util.Base64
import android.util.Base64InputStream
import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.FileNames
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

const val MAX_CONNECT_RETRIES = 5

/** Redirects followed before a download is reported as broken. */
internal const val MAX_REDIRECTS = 5

/**
 * One download running on the service's pool. Every task ends exactly once: the output is published
 * (complete) or removed (cancelled or failed) first, then [Callback.onDone] or [Callback.onError]
 * reports the final [Download.size]: the byte count, [Download.CANCELLED_MARK] or [Download.BROKEN_MARK].
 */
sealed class Transfer(var downloadInfo: Download, private val callback: Callback) : Runnable {

    interface Callback {
        fun onProgress(task: Transfer)
        fun onError(task: Transfer, responseCode: Int, responseMessage: String)
        fun onDone(task: Transfer)
    }

    /** How a transfer ended; a failure carries the HTTP status (0 for local errors) and its message. */
    protected sealed interface Outcome {
        object Complete : Outcome
        object Cancelled : Outcome
        class Failed(val code: Int, val message: String) : Outcome
    }

    /** Writes the payload; returns [Outcome.Cancelled] as soon as the download is cancelled. */
    protected abstract fun transfer(): Outcome

    /** Drops what the task holds once it has ended, whatever the outcome. */
    protected open fun release() {}

    final override fun run() {
        DownloadHistory.insert(downloadInfo)
        val outcome = try {
            if (downloadInfo.cancelled) Outcome.Cancelled else transfer()
        } catch (error: Exception) {
            if (downloadInfo.cancelled) Outcome.Cancelled else Outcome.Failed(0, error.toString())
        } finally {
            release()
        }
        when (outcome) {
            Outcome.Complete -> downloadInfo.size = downloadInfo.bytesReceived
            Outcome.Cancelled -> {
                downloadInfo.bytesReceived = 0
                downloadInfo.size = Download.CANCELLED_MARK
            }
            is Outcome.Failed -> downloadInfo.size = Download.BROKEN_MARK
        }
        finishOutput(downloadInfo, keep = outcome == Outcome.Complete)
        if (outcome is Outcome.Failed) callback.onError(this, outcome.code, outcome.message)
        else callback.onDone(this)
    }

    /** Copies [input] into a new download output, reporting progress; false if cancelled meanwhile. */
    protected fun save(input: InputStream): Boolean {
        openOutput(downloadInfo).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = input.read(buffer)
            while (count != -1) {
                if (downloadInfo.cancelled) return false
                output.write(buffer, 0, count)
                downloadInfo.bytesReceived += count
                callback.onProgress(this)
                count = input.read(buffer)
            }
        }
        return true
    }

    companion object {
        const val TAG = "Transfer"
    }
}

/** Downloads [Download.url] with the page's cookies, referer and user agent. */
class FileTransfer(downloadInfo: Download, private val userAgent: String?, callback: Callback) :
    Transfer(downloadInfo, callback) {

    override fun transfer(): Outcome {
        var url = URL(downloadInfo.url)
        var redirects = 0
        var retries = 0
        while (true) {
            if (downloadInfo.cancelled) return Outcome.Cancelled
            val connection = connect(url)
            try {
                val code = connection.responseCode
                when {
                    code == HttpURLConnection.HTTP_OK -> return receive(connection)
                    code in REDIRECTS -> {
                        // Followed here so that an http → https redirect works too; other schemes are refused.
                        val next = connection.getHeaderField("Location")?.let { URL(url, it) }
                        if (next == null || next.protocol !in WEB_SCHEMES || ++redirects > MAX_REDIRECTS) {
                            return Outcome.Failed(code, connection.responseMessage.orEmpty())
                        }
                        url = next
                    }
                    code == HttpURLConnection.HTTP_UNAVAILABLE || code == HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> {
                        val delay = DownloadRetryPolicy.delayMillis(++retries, connection.getHeaderField("Retry-After"))
                        if (retries >= MAX_CONNECT_RETRIES || delay == null) {
                            return Outcome.Failed(code, connection.responseMessage.orEmpty())
                        }
                        connection.disconnect()
                        // No connection is held while waiting; cancellation is checked every 250 ms.
                        if (!waitUnlessCancelled(delay)) return Outcome.Cancelled
                    }
                    else -> return Outcome.Failed(code, connection.responseMessage.orEmpty())
                }
            } finally {
                connection.errorStream?.close()
                connection.disconnect()
            }
        }
    }

    private fun connect(url: URL): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 20_000
        readTimeout = 10_000
        useCaches = false
        userAgent?.let { setRequestProperty("User-Agent", it) }
        downloadInfo.mimeType?.takeIf { it != "application/octet-stream" }?.let { setRequestProperty("Accept", it) }
        downloadInfo.referer?.let { setRequestProperty("Referer", it) }
        CookieManager.getInstance().getCookie(url.toString())?.let { setRequestProperty("Cookie", it) }
        connect()
    }

    private fun receive(connection: HttpURLConnection): Outcome {
        downloadInfo.size = connection.contentLengthLong
        val disposition = connection.getHeaderField("Content-Disposition")
        // Before Android 11 the service already reserved a unique file for the original name.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && disposition != null) {
            downloadInfo.filename = FileNames.sanitizeFileName(FileNames.guessFileName(
                downloadInfo.url, disposition, connection.getHeaderField("Content-Type"))) ?: "downloadfile"
        }
        return connection.inputStream.use { if (save(it)) Outcome.Complete else Outcome.Cancelled }
    }

    private fun waitUnlessCancelled(millis: Long): Boolean {
        val deadline = System.nanoTime() + millis * 1_000_000L
        while (!downloadInfo.cancelled) {
            val remaining = (deadline - System.nanoTime()) / 1_000_000L
            if (remaining <= 0) return true
            Thread.sleep(minOf(250L, remaining))
        }
        return false
    }

    private companion object {
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        val WEB_SCHEMES = setOf("http", "https")
    }
}

/** Decodes a page blob handed over as a base64 data URL, without holding a second decoded copy. */
class BlobTransfer(downloadInfo: Download, blobBase64Data: String, callback: Callback) :
    Transfer(downloadInfo, callback) {
    private var encoded: String? = blobBase64Data

    override fun transfer(): Outcome =
        Base64InputStream(BlobPayloadInputStream(requireNotNull(encoded)), Base64.DEFAULT).use {
            if (save(it)) Outcome.Complete else Outcome.Cancelled
        }

    override fun release() {
        encoded = null
        downloadInfo.base64BlobData = null
    }
}

/** Saves a stream the page already opened, such as a data URL or an intercepted response. */
class StreamTransfer(downloadInfo: Download, private val stream: InputStream, callback: Callback) :
    Transfer(downloadInfo, callback) {

    override fun transfer(): Outcome = if (save(stream)) Outcome.Complete else Outcome.Cancelled

    override fun release() {
        try {
            stream.close()
        } catch (_: IOException) {
        }
        downloadInfo.stream = null
    }
}

// Callers run only on Android 11+ (MediaStore.Downloads columns are API 29+).
@RequiresApi(Build.VERSION_CODES.R)
internal fun buildDownloadDetails(downloadInfo: Download) = ContentValues().apply {
    if (downloadInfo.filename.isNotEmpty()) put(MediaStore.Downloads.DISPLAY_NAME, downloadInfo.filename)
    if (!downloadInfo.incognito) {
        put(MediaStore.Downloads.DOWNLOAD_URI, downloadInfo.url)
        put(MediaStore.Downloads.REFERER_URI, downloadInfo.referer)
    }
    put(MediaStore.Downloads.IS_PENDING, 1)
}

/** Android 11+: a pending MediaStore entry whose URI becomes [Download.filepath]; earlier: the reserved file. */
private fun openOutput(downloadInfo: Download): OutputStream {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return FileOutputStream(downloadInfo.filepath)
    val resolver = VireoLumaTVApp.instance.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        buildDownloadDetails(downloadInfo)) ?: throw IOException("Could not create the download entry")
    downloadInfo.filepath = uri.toString()
    DownloadHistory.update(downloadInfo)
    val descriptor = resolver.openFileDescriptor(uri, "w", null) ?: throw IOException("Could not open the download output")
    return AutoCloseOutputStream(descriptor)
}

/** Publishes a complete output, or deletes an incomplete one; a task that never opened one has nothing to do. */
private fun finishOutput(downloadInfo: Download, keep: Boolean) {
    val path = downloadInfo.filepath
    if (path.isEmpty()) return
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Before the output exists the path is empty on Android 11+, so this is always a MediaStore URI.
            val resolver = VireoLumaTVApp.instance.contentResolver
            if (keep) resolver.update(path.toUri(), ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            else if (resolver.delete(path.toUri(), null) < 1) Log.w(Transfer.TAG, "Incomplete download was already gone")
        } else if (!keep) {
            File(path).delete()
        }
    } catch (error: Exception) {
        Log.w(Transfer.TAG, "Could not finish the download output", error)
    }
}
