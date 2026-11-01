package com.reiniertutoriales.vireolumatv.service.downloads

import android.content.ContentValues
import android.os.Build
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.provider.MediaStore
import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.FileNames
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

const val CONNECT_ATTEMPTS = 5

/** Redirects followed before a download is reported as broken. */
internal const val MAX_REDIRECTS = 5

/**
 * One download running on the service's pool. Every task ends exactly once: the output is published
 * (complete) or removed (cancelled or failed) first, then [Callback.onDone] or [Callback.onError]
 * reports the final [Download.size]: the byte count, [Download.SIZE_CANCELLED] or [Download.SIZE_FAILED].
 */
sealed class Transfer(var job: Download, private val callback: Callback) : Runnable {

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
        val outcome = try {
            DownloadHistory.insert(job)
            if (job.cancelled) Outcome.Cancelled else transfer()
        } catch (error: Exception) {
            if (job.cancelled) Outcome.Cancelled else Outcome.Failed(0, error.toString())
        } finally {
            release()
        }
        when (outcome) {
            Outcome.Complete -> job.size = job.bytesReceived
            Outcome.Cancelled -> {
                job.bytesReceived = 0
                job.size = Download.SIZE_CANCELLED
            }
            is Outcome.Failed -> job.size = Download.SIZE_FAILED
        }
        finishOutput(job, keep = outcome == Outcome.Complete)
        if (outcome is Outcome.Failed) callback.onError(this, outcome.code, outcome.message)
        else callback.onDone(this)
    }

    /** Copies [input] into a new download output, reporting progress; false if cancelled meanwhile. */
    protected fun save(input: InputStream): Boolean = openOutput(job).use { copy(input, it) }

    /**
     * Appends [input] to [output], reporting progress; false if cancelled meanwhile. A failed read
     * is a [NetworkFailure]; a failed write (a full disk) is not, and is never retried.
     */
    protected fun copy(input: InputStream, output: OutputStream): Boolean {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = network { input.read(buffer) }
            if (count == -1) return true
            if (job.cancelled) return false
            output.write(buffer, 0, count)
            job.bytesReceived += count
            callback.onProgress(this)
        }
    }

    /** The connection failed or was cut; the bytes written so far stay valid. */
    protected class NetworkFailure(cause: IOException) : IOException(cause)

    protected inline fun <T> network(block: () -> T): T = try {
        block()
    } catch (error: NetworkFailure) {
        throw error
    } catch (error: IOException) {
        throw NetworkFailure(error)
    }

    companion object {
        const val TAG = "Transfer"
    }
}

/**
 * Downloads [Download.url] with the page's cookies, referer and user agent. A connection that fails
 * or is cut is retried with growing delays, up to [CONNECT_ATTEMPTS] times in a row without progress.
 * The download goes on from the bytes already written when the server confirms the same file
 * (RFC 9110 Range with If-Range on a strong ETag or Last-Modified); otherwise it starts over.
 */
class FileTransfer internal constructor(
    job: Download,
    private val userAgent: String?,
    callback: Callback,
    private val retryDelay: (attempt: Int, retryAfter: String?) -> Long?,
) : Transfer(job, callback) {
    constructor(job: Download, userAgent: String?, callback: Callback) :
        this(job, userAgent, callback, { attempt, retryAfter -> DownloadRetryPolicy.delayMillis(attempt, retryAfter) })

    /** Open across attempts, so a resumed response appends to the same output. */
    private var output: FileOutputStream? = null
    /** The validator of the response being written, sent as If-Range when resuming. */
    private var validator: String? = null

    override fun transfer(): Outcome {
        var url = URL(job.url)
        var redirects = 0
        var retries = 0
        while (true) {
            if (job.cancelled) return Outcome.Cancelled
            val offset = job.bytesReceived
            val resume = offset > 0 && validator != null
            var connection: HttpURLConnection? = null
            try {
                connection = network { connect(url, if (resume) offset else 0) }
                val code = network { connection.responseCode }
                when {
                    code == HttpURLConnection.HTTP_OK -> return receive(connection, startOver = true)
                    code == HttpURLConnection.HTTP_PARTIAL && resume && rangeStart(connection) == offset ->
                        return receive(connection, startOver = false)
                    code in REDIRECTS -> {
                        // Followed here so that an http → https redirect works too; other schemes are refused.
                        val next = connection.getHeaderField("Location")?.let { URL(url, it) }
                        if (next == null || next.protocol !in WEB_SCHEMES || ++redirects > MAX_REDIRECTS) {
                            return Outcome.Failed(code, connection.responseMessage.orEmpty())
                        }
                        url = next
                    }
                    code == HttpURLConnection.HTTP_UNAVAILABLE || code == HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> {
                        val delay = retryDelay(++retries, connection.getHeaderField("Retry-After"))
                        if (retries >= CONNECT_ATTEMPTS || delay == null) {
                            return Outcome.Failed(code, connection.responseMessage.orEmpty())
                        }
                        connection.disconnect()
                        // No connection is held while waiting; cancellation is checked every 250 ms.
                        if (!waitUnlessCancelled(delay)) return Outcome.Cancelled
                    }
                    else -> return Outcome.Failed(code, connection.responseMessage.orEmpty())
                }
            } catch (failure: NetworkFailure) {
                if (job.cancelled) return Outcome.Cancelled
                val cause = failure.cause ?: failure
                if (!isTransient(cause)) return Outcome.Failed(0, cause.toString())
                // Bytes received since the last attempt prove the connection works: count again from zero.
                if (job.bytesReceived > offset) retries = 0
                val delay = retryDelay(++retries, null)
                if (retries >= CONNECT_ATTEMPTS || delay == null) return Outcome.Failed(0, cause.toString())
                Log.i(TAG, "Download interrupted at ${job.bytesReceived} bytes, attempt $retries: $cause")
                connection?.disconnect()
                connection = null
                if (!waitUnlessCancelled(delay)) return Outcome.Cancelled
            } finally {
                connection?.let {
                    runCatching { it.errorStream?.close() }
                    it.disconnect()
                }
            }
        }
    }

    private fun connect(url: URL, from: Long): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 20_000
        readTimeout = 10_000
        useCaches = false
        // Byte offsets for resuming refer to the file as sent: no transparent decompression.
        setRequestProperty("Accept-Encoding", "identity")
        userAgent?.let { setRequestProperty("User-Agent", it) }
        job.mimeType?.takeIf { it != "application/octet-stream" }?.let { setRequestProperty("Accept", it) }
        job.referer?.let { setRequestProperty("Referer", it) }
        CookieManager.getInstance().getCookie(url.toString())?.let { setRequestProperty("Cookie", it) }
        if (from > 0) {
            setRequestProperty("Range", "bytes=$from-")
            validator?.let { setRequestProperty("If-Range", it) }
        }
        connect()
    }

    /** Writes the body: a whole response replaces what was written, a partial one appends to it. */
    private fun receive(connection: HttpURLConnection, startOver: Boolean): Outcome {
        if (startOver) {
            job.size = connection.contentLengthLong
            validator = strongValidator(connection)
            val written = output
            if (written == null) {
                val disposition = connection.getHeaderField("Content-Disposition")
                // Before Android 11 the service already reserved a unique file for the original name.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && disposition != null) {
                    job.filename = FileNames.sanitizeFileName(FileNames.guessFileName(
                        job.url, disposition, connection.getHeaderField("Content-Type"))) ?: "downloadfile"
                }
            } else {
                written.channel.truncate(0)
                written.channel.position(0)
            }
            job.bytesReceived = 0
        } else {
            rangeTotal(connection)?.let { job.size = it }
        }
        val out = output ?: openOutput(job).also { output = it }
        val complete = network { connection.inputStream }.use { copy(it, out) }
        if (!complete) return Outcome.Cancelled
        // A connection closed early ends the stream without an error.
        if (job.size > 0 && job.bytesReceived < job.size) {
            throw NetworkFailure(EOFException("Got ${job.bytesReceived} of ${job.size} bytes"))
        }
        return Outcome.Complete
    }

    override fun release() {
        try {
            output?.close()
        } catch (_: IOException) {
        }
        output = null
    }

    private fun waitUnlessCancelled(millis: Long): Boolean {
        val deadline = System.nanoTime() + millis * 1_000_000L
        while (!job.cancelled) {
            val remaining = (deadline - System.nanoTime()) / 1_000_000L
            if (remaining <= 0) return true
            Thread.sleep(minOf(250L, remaining))
        }
        return false
    }

    internal companion object {
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        val WEB_SCHEMES = setOf("http", "https")
        private val CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)

        /** A strong ETag, else Last-Modified: the validators If-Range accepts (RFC 9110 13.1.5). */
        fun strongValidator(connection: HttpURLConnection): String? =
            connection.getHeaderField("ETag")?.trim()?.takeIf { it.startsWith("\"") }
                ?: connection.getHeaderField("Last-Modified")?.trim()?.takeIf { it.isNotEmpty() }

        private fun rangeStart(connection: HttpURLConnection): Long? =
            CONTENT_RANGE.matchEntire(connection.getHeaderField("Content-Range")?.trim().orEmpty())
                ?.groupValues?.get(1)?.toLongOrNull()

        private fun rangeTotal(connection: HttpURLConnection): Long? =
            CONTENT_RANGE.matchEntire(connection.getHeaderField("Content-Range")?.trim().orEmpty())
                ?.groupValues?.get(3)?.toLongOrNull()

        /** Lost or slow connections are retried; certificate and protocol errors are not. */
        fun isTransient(error: Throwable): Boolean {
            if (error is ProtocolException || error is SSLPeerUnverifiedException) return false
            return generateSequence(error) { it.cause }.none { it is CertificateException }
        }
    }
}

/** Saves a stream the page already opened, such as a data URL or an intercepted response. */
class StreamTransfer(job: Download, private val stream: InputStream, callback: Callback) :
    Transfer(job, callback) {

    override fun transfer(): Outcome = if (save(stream)) Outcome.Complete else Outcome.Cancelled

    override fun release() {
        try {
            stream.close()
        } catch (_: IOException) {
        }
        job.stream = null
    }
}

// Callers run only on Android 11+ (MediaStore.Downloads columns are API 29+).
@RequiresApi(Build.VERSION_CODES.R)
internal fun buildDownloadDetails(job: Download) = ContentValues().apply {
    if (job.filename.isNotEmpty()) put(MediaStore.Downloads.DISPLAY_NAME, job.filename)
    if (!job.incognito) {
        put(MediaStore.Downloads.DOWNLOAD_URI, job.url)
        put(MediaStore.Downloads.REFERER_URI, job.referer)
    }
    put(MediaStore.Downloads.IS_PENDING, 1)
}

/** Android 11+: a pending MediaStore entry whose URI becomes [Download.filepath]; earlier: the reserved file. */
private fun openOutput(job: Download): FileOutputStream {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return FileOutputStream(job.filepath)
    val resolver = VireoLumaTVApp.instance.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        buildDownloadDetails(job)) ?: throw IOException("Could not create the download entry")
    job.filepath = uri.toString()
    DownloadHistory.update(job)
    val descriptor = resolver.openFileDescriptor(uri, "w", null) ?: throw IOException("Could not open the download output")
    return AutoCloseOutputStream(descriptor)
}

/** Publishes a complete output, or deletes an incomplete one; a task that never opened one has nothing to do. */
private fun finishOutput(job: Download, keep: Boolean) {
    val path = job.filepath
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
