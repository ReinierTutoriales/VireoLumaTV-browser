package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.http.SslError
import android.os.SystemClock
import android.util.Base64
import android.webkit.JavascriptInterface
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.DownloadUtils
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom


class AndroidJSInterface(private val webEngine: WebViewWebEngine) {
    companion object {
        // Covers the XHR + FileReader round trip for the largest accepted blob on a slow TV SoC.
        private const val BLOB_DOWNLOAD_TIMEOUT_MS = 30_000L
        private const val MAX_BLOB_DOWNLOAD_BYTES = 32L * 1024L * 1024L
        private val secureRandom = SecureRandom()
    }

    private data class PendingBlobDownload(
        val token: String,
        val url: String,
        val fileName: String?,
        val sourceUrl: android.net.Uri,
        val expiresAtMs: Long,
        var mimetype: String = "",
        var sizeAccepted: Boolean = false
    )

    private val blobDownloadLock = Any()
    private var pendingBlobDownload: PendingBlobDownload? = null

    @JavascriptInterface
    fun currentUrl(): String {
        if (!isInternalCertificateErrorPage()) return ""
        return (webEngine.getView() as? WebViewEx)?.lastSSLError?.url ?: ""
    }

    @JavascriptInterface
    fun reloadWithSslTrust() {
        val callback = webEngine.callback ?: return
        if (!isInternalCertificateErrorPage()) return
        val error = (webEngine.getView() as? WebViewEx)?.lastSSLError ?: return
        callback.getActivity().runOnUiThread {
            if (!isInternalCertificateErrorPage()) return@runOnUiThread
            val webview = webEngine.getView() as? WebViewEx ?: return@runOnUiThread
            if (webview.lastSSLError !== error) return@runOnUiThread
            webview.trustSsl = true
            webEngine.loadUrl(error.url)
        }
    }

    @JavascriptInterface
    fun getStringByName(name: String): String {
        val ctx = VireoLumaTVApp.instance
        //val resId = ctx.resources.getIdentifier(name, "string", ctx.packageName)
        //return ctx.getString(resId)
        when (name) {
            "connection_isnt_secure" -> return ctx.getString(R.string.connection_isnt_secure)
            "hostname" -> return ctx.getString(R.string.hostname)
            "err_desk" -> return ctx.getString(R.string.err_desk)
            "details" -> return ctx.getString(R.string.details)
            "back_to_safety" -> return ctx.getString(R.string.back_to_safety)
            "go_im_aware" -> return ctx.getString(R.string.go_im_aware)
            else -> return ""
        }
    }

    @JavascriptInterface
    fun startVoiceSearch() {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread { if (isHomePage()) callback.initiateVoiceSearch() }
    }

    @JavascriptInterface
    fun setSearchEngine(engine: String, customSearchEngineURL: String) {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread {
            if (isHomePage()) AppContext.provideConfig().searchEngineURL.value = customSearchEngineURL
        }
    }

    @JavascriptInterface
    fun onEditBookmark(index: Int) {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread { if (isHomePage()) callback.onEditHomePageBookmarkSelected(index) }
    }

    @JavascriptInterface
    fun onHomePageLoaded() {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread {
            if (!isHomePage()) return@runOnUiThread
            val cfg = AppContext.provideConfig()
            val jsArr = JSONArray()
            for (item in callback.getHomePageLinks()) {
                jsArr.put(item.toJsonObj())
            }
            var links = jsArr.toString()
            links = links.replace("'", "\\'")
            webEngine.evaluateJavascript("renderLinks('${cfg.homePageLinksMode.name}', $links)")
            webEngine.evaluateJavascript(
                "applySearchEngine(${JSONObject.quote(cfg.guessSearchEngineName())}, ${JSONObject.quote(cfg.searchEngineURL.value)})")
        }
    }

    @JavascriptInterface
    fun lastSSLError(getDetails: Boolean): String {
        if (!isInternalCertificateErrorPage()) return "unknown"
        val lastSSLError = (webEngine.getView() as? WebViewEx)?.lastSSLError ?: return "unknown"
        return if (getDetails) {
            lastSSLError.toString()
        } else {
            when (lastSSLError.primaryError) {
                SslError.SSL_EXPIRED -> VireoLumaTVApp.instance.getString(R.string.ssl_expired)
                SslError.SSL_IDMISMATCH -> VireoLumaTVApp.instance.getString(R.string.ssl_idmismatch)
                SslError.SSL_DATE_INVALID -> VireoLumaTVApp.instance.getString(R.string.ssl_date_invalid)
                SslError.SSL_INVALID -> VireoLumaTVApp.instance.getString(R.string.ssl_invalid)
                else -> "unknown"
            }
        }
    }

    /**
     * Opens a blob download session. Requires a fresh native user activation (remote OK/Enter,
     * gamepad A, touch or mouse click) that the page cannot synthesize; the activation is consumed,
     * so one press authorizes at most one download. Returns a single-use token generated here,
     * or "" when rejected.
     */
    @JavascriptInterface
    fun beginBlobDownload(url: String, fileName: String?): String {
        val sourceUrl = (webEngine.getView() as? WebViewEx)?.currentOriginalUrl ?: return ""
        if (!BridgePagePolicy.isNormalWebPage(sourceUrl)) return ""
        if (!url.startsWith("blob:", ignoreCase = true)) return ""

        val now = SystemClock.uptimeMillis()
        synchronized(blobDownloadLock) {
            val existing = pendingBlobDownload
            if (existing != null && existing.expiresAtMs > now) return ""
            if (!UserActivation.consume()) return ""
            val token = newBlobToken()
            pendingBlobDownload = PendingBlobDownload(
                token = token,
                url = url,
                fileName = fileName,
                sourceUrl = sourceUrl,
                expiresAtMs = now + BLOB_DOWNLOAD_TIMEOUT_MS
            )
            return token
        }
    }

    /** Called before FileReader so oversized blobs are dropped before being base64-encoded. */
    @JavascriptInterface
    fun acceptBlobSize(token: String, size: Long, mimetype: String?): Boolean {
        val now = SystemClock.uptimeMillis()
        synchronized(blobDownloadLock) {
            val pending = livePending(token, now) ?: return false
            if (size <= 0L || size > MAX_BLOB_DOWNLOAD_BYTES) {
                pendingBlobDownload = null
                return false
            }
            pending.mimetype = mimetype.orEmpty()
            pending.sizeAccepted = true
            return true
        }
    }

    @JavascriptInterface
    fun cancelBlobDownload(token: String) {
        synchronized(blobDownloadLock) {
            if (pendingBlobDownload?.token == token) pendingBlobDownload = null
        }
    }

    @JavascriptInterface
    fun takeBlobDownloadData(token: String, base64BlobData: String, url: String) {
        // The Java String already exists when this runs; the length check below prevents the
        // decode and disk write from multiplying that cost, it cannot undo the bridge allocation.
        val sourceUrl = (webEngine.getView() as? WebViewEx)?.currentOriginalUrl ?: return
        val now = SystemClock.uptimeMillis()
        val pending = synchronized(blobDownloadLock) {
            val pending = livePending(token, now) ?: return
            pendingBlobDownload = null
            if (!pending.sizeAccepted || pending.url != url || pending.sourceUrl != sourceUrl) return
            if (!isBlobDataUrlWithinLimit(base64BlobData)) return
            pending
        }

        if (!BridgePagePolicy.isNormalWebPage(sourceUrl)) return
        val callback = webEngine.callback ?: return
        val finalFileName = DownloadUtils.sanitizeFileName(pending.fileName)
            ?: DownloadUtils.guessFileName(url, null, pending.mimetype)
        callback.getActivity().runOnUiThread {
            if (!isNormalWebPage() || (webEngine.getView() as? WebViewEx)?.currentOriginalUrl != pending.sourceUrl) return@runOnUiThread
            callback.onDownloadRequested(url, "", finalFileName, "VireoLumaTV",
                pending.mimetype, Download.OperationAfterDownload.NOP, base64BlobData)
        }
    }

    /** Must be called while holding [blobDownloadLock]. Drops expired sessions. */
    private fun livePending(token: String, now: Long): PendingBlobDownload? {
        val pending = pendingBlobDownload ?: return null
        if (pending.expiresAtMs <= now) {
            pendingBlobDownload = null
            return null
        }
        return if (pending.token == token) pending else null
    }

    @JavascriptInterface
    fun markBookmarkRecommendationAsUseful(bookmarkOrder: Int) {
        if (!isHomePage()) return
        val callback = webEngine.callback ?: return
        callback.getActivity().runOnUiThread { if (isHomePage()) callback.markBookmarkRecommendationAsUseful(bookmarkOrder) }
    }

    private fun isHomePage(): Boolean {
        return (webEngine.getView() as? WebViewEx)?.currentOriginalUrl?.toString() == Config.HOME_PAGE_URL
    }

    private fun isNormalWebPage(): Boolean {
        return BridgePagePolicy.isNormalWebPage((webEngine.getView() as? WebViewEx)?.currentOriginalUrl)
    }

    private fun isInternalCertificateErrorPage(): Boolean {
        val view = webEngine.getView() as? WebViewEx ?: return false
        return BridgePagePolicy.isCertificatePage(view.currentOriginalUrl, view.certificateErrorPageUrl, view.lastSSLError != null)
    }

    private fun newBlobToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
    }

    private fun isBlobDataUrlWithinLimit(dataUrl: String): Boolean {
        if (!dataUrl.startsWith("data:", ignoreCase = true)) return false
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex <= 0 || commaIndex >= dataUrl.length - 1) return false
        val metadata = dataUrl.substring(0, commaIndex)
        if (!metadata.contains(";base64", ignoreCase = true)) return false
        val encodedLength = dataUrl.length - commaIndex - 1
        val maxEncodedLength = ((MAX_BLOB_DOWNLOAD_BYTES + 2L) / 3L) * 4L
        return encodedLength <= maxEncodedLength
    }
}
