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
 * The browser's JavaScript bridge. Any page can call these methods, so each one checks what page
 * it is serving: filters for web frames only, certificate trust only from the warning page, blob
 * downloads only from a web page and only after a fresh press. Calls arrive on the JavaBridge thread.
 */
internal class PageBridge(private val engine: WebViewPageEngine) {
    private val main = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private val blobs = BlobTransfers(SystemClock::uptimeMillis, ::newToken)
    private val page get() = engine.view as? BrowserWebView
    private val webPage get() = page?.currentOriginalUrl?.takeIf(BridgePagePolicy::isNormalWebPage)

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

    /** The warning page's "proceed": trusts the shown certificate error once and loads the site. */
    @JavascriptInterface
    fun trustCertificateOnce() {
        val error = page?.shownCertificateError() ?: return
        main.post {
            val view = page ?: return@post
            if (view.shownCertificateError() !== error) return@post
            view.trustSsl = true
            engine.load(error.url)
        }
    }

    /** Opens a blob download for a fresh press (which it consumes); returns its token or "". */
    @JavascriptInterface
    fun beginBlobDownload(url: String, fileName: String?): String {
        val source = webPage ?: return ""
        if (!url.startsWith("blob:", ignoreCase = true)) return ""
        return blobs.begin(url, fileName, source.toString(), UserActivation::consume)
    }

    /** Called before the page encodes the blob, so an oversized one is never encoded. */
    @JavascriptInterface
    fun acceptBlobSize(token: String, size: Long, mimeType: String?): Boolean = blobs.accept(token, size, mimeType.orEmpty())

    @JavascriptInterface
    fun cancelBlobDownload(token: String) = blobs.cancel(token)

    /** The blob's bytes; the string already exists here, the size check only spares decoding and writing it. */
    @JavascriptInterface
    fun takeBlobDownloadData(token: String, base64BlobData: String, url: String) {
        val source = webPage?.toString() ?: return
        val ticket = blobs.take(token, url, source, base64BlobData) ?: return
        val name = FileNames.sanitizeFileName(ticket.fileName) ?: FileNames.guessFileName(url, null, ticket.mimeType)
        main.post {
            if (webPage?.toString() != ticket.page) return@post
            engine.window?.download(Download(url, name, null, Download.AfterDownload.NOP, ticket.mimeType,
                null, null, base64BlobData, null))
        }
    }

    private fun isFrameUrl(url: String?) =
        url != null && url.length <= MAX_URL && (url.startsWith("http://") || url.startsWith("https://"))

    private fun newToken(): String = ByteArray(24).also(random::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE) }

    private companion object {
        const val MAX_URL = 4096
        const val MAX_SELECTOR_INPUT = 64 * 1024
    }
}
