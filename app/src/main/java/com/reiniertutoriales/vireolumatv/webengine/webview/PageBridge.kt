package com.reiniertutoriales.vireolumatv.webengine.webview

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.webkit.JavascriptInterface
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.FileNames
import java.security.SecureRandom

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
 * The filter bridge plus the privileged calls, registered instead of [FilterBridge] only on WebView
 * providers without WebMessage listeners. There a call cannot be traced to its frame, so the checks
 * rest on the shown page and a fresh physical press. Modern providers reach the same
 * [PrivilegedPageActions] from main-frame-checked messages and never expose these methods.
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

    /** Opens a blob download for a fresh press (which it consumes); returns its token or "". */
    @JavascriptInterface
    fun beginBlobDownload(url: String, fileName: String?): String =
        engine.privileged.beginBlob(url, fileName, webPage() ?: return "")

    /** Called before the page encodes the blob, so an oversized one is never encoded. */
    @JavascriptInterface
    fun acceptBlobSize(token: String, size: Long, mimeType: String?): Boolean =
        engine.privileged.acceptBlob(token, size, mimeType.orEmpty())

    @JavascriptInterface
    fun cancelBlobDownload(token: String) = engine.privileged.cancelBlob(token)

    /** The blob's bytes; the string already exists here, the size check only spares decoding and writing it. */
    @JavascriptInterface
    fun takeBlobDownloadData(token: String, base64BlobData: String, url: String) {
        engine.privileged.takeBlob(token, url, base64BlobData, webPage() ?: return, ::webPage)
    }

    private fun webPage(): String? = page?.currentOriginalUrl?.takeIf(BridgePagePolicy::isNormalWebPage)?.toString()
}

/**
 * Certificate approval and blob downloads, shared by main-frame-checked WebMessages and, on old
 * providers, [LegacyPrivilegedBridge]. A transfer completes only for the document key it began from.
 */
internal class PrivilegedPageActions(private val engine: WebViewPageEngine) {
    private val main = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private val blobs = BlobTransfers(SystemClock::uptimeMillis, ::newToken)
    private val page get() = engine.view as? BrowserWebView

    fun approveCertificate() {
        val error = page?.shownCertificateError() ?: return
        // An embedded script must never approve a certificate error without a real TV
        // remote or touch activation. This check does not identify the calling frame.
        if (!UserActivation.consume()) return
        main.post {
            val view = page ?: return@post
            if (view.shownCertificateError() !== error) return@post
            view.trustSsl = true
            engine.load(error.url)
        }
    }

    fun beginBlob(url: String, fileName: String?, document: String): String {
        if (!url.startsWith("blob:", ignoreCase = true)) return ""
        return blobs.begin(url, fileName, document, UserActivation::consume)
    }

    fun acceptBlob(token: String, size: Long, mimeType: String): Boolean = blobs.accept(token, size, mimeType)

    fun cancelBlob(token: String) = blobs.cancel(token)

    /** Queues the bytes when the main thread still shows the same [document] (read by [currentDocument]). */
    fun takeBlob(token: String, url: String, dataUrl: String, document: String, currentDocument: () -> String?) {
        val ticket = blobs.take(token, url, document, dataUrl) ?: return
        val name = FileNames.sanitizeFileName(ticket.fileName) ?: FileNames.guessFileName(url, null, ticket.mimeType)
        main.post {
            if (currentDocument() != ticket.page) return@post
            engine.window?.download(Download(url, name, null, Download.AfterDownload.NOP, ticket.mimeType,
                null, null, dataUrl, null))
        }
    }

    private fun newToken(): String = ByteArray(24).also(random::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE) }
}
