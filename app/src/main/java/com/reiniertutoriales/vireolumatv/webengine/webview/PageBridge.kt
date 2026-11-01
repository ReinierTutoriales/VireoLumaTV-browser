package com.reiniertutoriales.vireolumatv.webengine.webview

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.webkit.JavascriptInterface
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.FileNames
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    private fun isFrameUrl(url: String?) =
        url != null && url.length <= MAX_URL && (url.startsWith("http://") || url.startsWith("https://"))

    private companion object {
        const val MAX_URL = 4096
        const val MAX_SELECTOR_INPUT = 64 * 1024
    }
}

/**
 * The filter bridge plus certificate trust, registered instead of [FilterBridge] only on WebView
 * providers without WebMessage listeners. There a call cannot be traced to its frame: trust is
 * honored only from the packaged warning page, which loads no frames, after a fresh physical press.
 * Blob downloads need a frame's identity, so these providers get none (generic_injects.js leaves
 * blob links to WebView).
 */
internal class LegacyPrivilegedBridge(engine: WebViewPageEngine) : FilterBridge(engine) {
    private val page get() = engine.view as? BrowserWebView

    /** The warning page's "proceed": trusts the shown certificate error once and loads the site. */
    @JavascriptInterface
    fun trustCertificateOnce() {
        // A WebMessage-capable provider must reject the untrusted all-frames JS interface.
        if (page?.usesWebMessageBridge == true) return
        engine.privileged.approveCertificate()
    }
}

/**
 * Certificate approval for both channels, and blob downloads for main-frame-checked WebMessages.
 * Blob steps run in order on one background thread, write each chunk straight to a [BlobSpool] and
 * answer on the main thread; a transfer completes only for the document key it began from.
 */
internal class PrivilegedPageActions(private val engine: WebViewPageEngine) {
    private val main = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private val blobs = BlobTransfers(SystemClock::uptimeMillis, ::newToken) {
        BlobSpool.open(File(VireoLumaTVApp.instance.cacheDir, SPOOL_DIR))
    }
    private val page get() = engine.view as? BrowserWebView

    fun approveCertificate() {
        val error = page?.shownCertificateError() ?: return
        // An embedded script must never approve a certificate error without a real TV
        // remote or touch activation. This check does not identify the calling frame.
        if (!UserActivation.consume()) return
        main.post {
            val view = page ?: return@post
            if (view.shownCertificateError() !== error) return@post
            view.trustedCertificate = CertificateTrust.Approval(error.url, error.certificate)
            engine.load(error.url)
        }
    }

    /**
     * Handles one download message of [document]; [reply] and [currentDocument] are called on the
     * main thread, the latter to drop a finished file whose page has meanwhile changed origin.
     */
    fun onDownloadMessage(request: DownloadMessage, document: String, currentDocument: () -> String?, reply: (String) -> Unit) {
        blobThread.execute {
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
                is DownloadMessage.Done -> { finish(request, document, currentDocument); null }
                is DownloadMessage.Cancel -> { blobs.cancel(request.token); null }
            }
            if (answer != null) main.post { reply(answer) }
        }
    }

    private fun finish(request: DownloadMessage.Done, document: String, currentDocument: () -> String?) {
        val completed = blobs.finish(request.token, request.url, document) ?: return
        val ticket = completed.ticket
        val name = FileNames.sanitizeFileName(ticket.fileName) ?: FileNames.guessFileName(ticket.url, null, ticket.mimeType)
        main.post {
            val window = engine.window
            if (window == null || currentDocument() != ticket.page) {
                try { completed.bytes.close() } catch (_: IOException) { }
                return@post
            }
            window.download(Download(ticket.url, name, null, Download.AfterDownload.NOP, ticket.mimeType,
                null, null, completed.bytes, ticket.size))
        }
    }

    private fun decode(base64: String): ByteArray? =
        try { Base64.decode(base64, Base64.DEFAULT) } catch (_: IllegalArgumentException) { null }

    private fun newToken(): String = ByteArray(24).also(random::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE) }

    private companion object {
        const val SPOOL_DIR = "blob-spool"
        /** One thread for every tab: blob steps are short, ordered, and only one transfer runs per tab. */
        val blobThread: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "VireoBlob").apply { isDaemon = true } }
    }
}
