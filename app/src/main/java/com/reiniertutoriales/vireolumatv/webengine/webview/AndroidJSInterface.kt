package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.http.SslError
import android.webkit.JavascriptInterface
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.DownloadUtils
import org.json.JSONArray
import org.json.JSONObject


class AndroidJSInterface(private val webEngine: WebViewWebEngine) {
    companion object {
        private const val BLOB_DOWNLOAD_TIMEOUT_MS = 10_000L
        private const val MAX_BLOB_DOWNLOAD_BYTES = 32L * 1024L * 1024L
        private val BLOB_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{32,128}$")
    }

    private data class PendingBlobDownload(
        val token: String,
        val url: String,
        val fileName: String?,
        val mimetype: String,
        val sourceUrl: android.net.Uri,
        val expiresAtMs: Long
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

    @JavascriptInterface
    fun beginBlobDownload(token: String, url: String, fileName: String?, mimetype: String?, size: Long): Boolean {
        val sourceUrl = (webEngine.getView() as? WebViewEx)?.currentOriginalUrl ?: return false
        if (!BridgePagePolicy.isNormalWebPage(sourceUrl)) return false
        if (!isValidBlobToken(token)) return false
        if (!url.startsWith("blob:", ignoreCase = true)) return false
        if (size <= 0L || size > MAX_BLOB_DOWNLOAD_BYTES) return false

        val now = System.currentTimeMillis()
        synchronized(blobDownloadLock) {
            val existing = pendingBlobDownload
            if (existing != null && existing.expiresAtMs > now) return false
            pendingBlobDownload = PendingBlobDownload(
                token = token,
                url = url,
                fileName = fileName,
                mimetype = mimetype.orEmpty(),
                sourceUrl = sourceUrl,
                expiresAtMs = now + BLOB_DOWNLOAD_TIMEOUT_MS
            )
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
    fun takeBlobDownloadData(token: String, base64BlobData: String, fileName: String?, url: String, mimetype: String) {
        // Blob data is extremely memory-expensive in this legacy bridge path. Only accept a single
        // short-lived, user-click initiated session registered by the injected downloader script.
        val sourceUrl = (webEngine.getView() as? WebViewEx)?.currentOriginalUrl ?: return
        val now = System.currentTimeMillis()
        val pending = synchronized(blobDownloadLock) {
            val pending = pendingBlobDownload ?: return
            if (pending.expiresAtMs <= now) {
                pendingBlobDownload = null
                return
            }
            if (pending.token != token || pending.url != url || pending.sourceUrl != sourceUrl) return
            if (!isBlobDataUrlWithinLimit(base64BlobData)) return
            pendingBlobDownload = null
            pending
        }

        if (!BridgePagePolicy.isNormalWebPage(sourceUrl)) return
        val callback = webEngine.callback ?: return
        val finalFileName = DownloadUtils.sanitizeFileName(fileName ?: pending.fileName)
            ?: DownloadUtils.guessFileName(url, null, pending.mimetype.ifEmpty { mimetype })
        callback.getActivity().runOnUiThread {
            if (!isNormalWebPage() || (webEngine.getView() as? WebViewEx)?.currentOriginalUrl != pending.sourceUrl) return@runOnUiThread
            callback.onDownloadRequested(url, "", finalFileName, "VireoLumaTV",
                pending.mimetype.ifEmpty { mimetype }, Download.OperationAfterDownload.NOP, base64BlobData)
        }
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

    private fun isValidBlobToken(token: String): Boolean {
        return BLOB_TOKEN_PATTERN.matches(token)
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
