package com.reiniertutoriales.vireolumatv.webengine.webview

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.FileNames
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The read-only JavaScript bridge, registered as `VireoLumaTVApp` on every WebView. Any frame of
 * any page can call it, synchronously, so it only hands out filter data and records playback:
 * page_filters.js needs its answers at document start, before the page's own scripts run.
 * Calls arrive on the JavaBridge thread.
 */
internal open class FilterBridge(protected val engine: WebViewPageEngine) {
    /** Element hiding and scriptlets for a frame (see page_filters.js). */
    @JavascriptInterface
    fun pageFilters(url: String?): String = if (isFrameUrl(url)) engine.pageFilters(url!!) else ""

    /** Generic hiding selectors for the classes and ids found in a frame, each list space-separated. */
    @JavascriptInterface
    fun hiddenSelectors(url: String?, classes: String?, ids: String?): String {
        val found = classes.orEmpty().length + ids.orEmpty().length
        if (!isFrameUrl(url) || found > MAX_SELECTOR_INPUT) return "[]"
        return engine.hiddenSelectors(url!!, classes.orEmpty(), ids.orEmpty())
    }

    /** A video or audio element started playing in some frame (see page_filters.js). */
    @JavascriptInterface
    fun mediaStarted() {
        engine.tab.mediaStarted = true
    }

    private fun isFrameUrl(url: String?) = FilterBridgeUrlPolicy.isWebFrame(url)

    private companion object {
        const val MAX_SELECTOR_INPUT = 64 * 1024
    }
}

/** Reject malformed, hostless and non-web URLs before they reach the native filter engine. */
internal object FilterBridgeUrlPolicy {
    private const val MAX_URL = 4096

    fun isWebFrame(url: String?): Boolean {
        if (url.isNullOrBlank() || url.length > MAX_URL) return false
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        return uri.isHierarchical &&
            (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
    }
}

/**
 * Certificate approval and blob downloads through main-frame-checked WebMessages.
 * Blob steps run in order on one background thread, write each chunk straight to a [BlobSpool] and
 * answer on the main thread; a transfer completes only for the document key it began from.
 */
internal class PrivilegedPageActions(private val engine: WebViewPageEngine) {
    private val main = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private val generation = AtomicLong(0)
    private val messagePending = AtomicBoolean(false)

    /** Invalidate queued messages and release the spool when its owning WebView dies. */
    fun invalidateTransfers() {
        generation.incrementAndGet()
        blobs.cancelAll()
    }
    private val blobs = BlobTransfers(SystemClock::uptimeMillis, ::newToken) {
        BlobSpool.open(File(VireoLumaTVApp.instance.cacheDir, SPOOL_DIR))
    }
    private val page get() = engine.view as? BrowserWebView

    fun approveCertificate() {
        val originView = page ?: return
        val error = originView.shownCertificateError() ?: return
        // An embedded script must never approve a certificate error without a real TV
        // remote or touch activation. This check does not identify the calling frame.
        if (!UserActivation.consume()) return
        main.post {
            val view = page ?: return@post
            if (view !== originView || view.shownCertificateError() !== error) return@post
            view.trustedCertificate = CertificateTrust.Approval(error.url, error.certificate)
            engine.load(error.url)
        }
    }

    /**
     * Handles one download message of [document]; [reply] and [currentDocument] are called on the
     * main thread, the latter to drop a finished file whose page has meanwhile changed origin.
     */
    fun onDownloadMessage(request: DownloadMessage, document: String, currentDocument: () -> String?, reply: (String) -> Unit) {
        // The script waits for each acknowledgement. Ignore unsolicited overlap before retaining
        // its chunk or reply; the permit covers both the worker and the main-thread delivery.
        if (!messagePending.compareAndSet(false, true)) return
        if (!blobSlots.tryAcquire()) { messagePending.set(false); return }
        val acceptedGeneration = generation.get()
        try {
            blobThread.execute {
                var deliveryPending = false
                try {
                    if (generation.get() != acceptedGeneration) return@execute
                    val answer = when (request) {
                        is DownloadMessage.Begin -> DownloadMessage.beginReply(request.url,
                            if (request.url.startsWith("blob:", ignoreCase = true))
                                blobs.begin(request.url, request.fileName, document, UserActivation::consume)
                            else "")
                        is DownloadMessage.Size ->
                            DownloadMessage.sizeReply(request.token, blobs.accept(request.token, request.bytes, request.mimeType))
                        is DownloadMessage.Chunk -> DownloadMessage.chunkReply(request.token, decode(request.base64)
                            ?.let { blobs.append(request.token, request.offset, it) }
                            ?: (-1L).also { blobs.cancel(request.token) })
                        is DownloadMessage.Done -> {
                            deliveryPending = finish(request, document, currentDocument, acceptedGeneration)
                            null
                        }
                        is DownloadMessage.Cancel -> { blobs.cancel(request.token); null }
                    }
                    // Teardown may race a chunk already executing on this worker.
                    if (generation.get() != acceptedGeneration) {
                        blobs.cancelAll()
                        return@execute
                    }
                    if (answer != null) deliveryPending = main.post {
                        try {
                            if (generation.get() == acceptedGeneration) reply(answer)
                        } finally { releaseMessage() }
                    }
                } finally { if (!deliveryPending) releaseMessage() }
            }
        } catch (_: RejectedExecutionException) {
            releaseMessage()
        }
    }

    private fun releaseMessage() {
        blobSlots.release()
        messagePending.set(false)
    }

    private fun finish(request: DownloadMessage.Done, document: String, currentDocument: () -> String?, acceptedGeneration: Long): Boolean {
        val completed = blobs.finish(request.token, request.url, document) ?: return false
        val ticket = completed.ticket
        val name = FileNames.sanitizeFileName(ticket.fileName) ?: FileNames.guessFileName(ticket.url, null, ticket.mimeType)
        val posted = main.post {
            try {
                val window = engine.window
                if (generation.get() != acceptedGeneration || window == null || currentDocument() != ticket.page) {
                    try { completed.bytes.close() } catch (_: IOException) { }
                    return@post
                }
                window.download(Download(ticket.url, name, null, Download.AfterDownload.NOP, ticket.mimeType,
                    null, null, completed.bytes, ticket.size))
            } finally { releaseMessage() }
        }
        if (!posted) try { completed.bytes.close() } catch (_: IOException) { }
        return posted
    }

    private fun decode(base64: String): ByteArray? =
        try { Base64.decode(base64, Base64.DEFAULT) } catch (_: IllegalArgumentException) { null }

    private fun newToken(): String = ByteArray(24).also(random::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE) }

    private companion object {
        const val SPOOL_DIR = "blob-spool"
        // At most eight retained base64 chunks (512 KiB decoded each), across all page instances.
        private val blobSlots = Semaphore(8)
        /** One thread for every tab: blob steps are short, ordered, and only one transfer runs per tab. */
        val blobThread: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "VireoBlob").apply { isDaemon = true } }
    }
}
