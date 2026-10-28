package com.reiniertutoriales.vireolumatv.webengine.webview

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.adblock.AdblockSurrogates
import java.io.IOException
import java.net.URLConnection

/**
 * Navigation and loading of one page: popup and tab-under guards, the packaged home page,
 * ad blocking, page lifecycle, certificate errors and HTTP authentication.
 */
internal class BrowserWebViewClient(private val view: WebViewEx) : WebViewClient() {
    private val window get() = view.window
    private val engine get() = view.engine

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.isForMainFrame) {
            // A popunder's redirect chain (ad network, then advertiser) arrives here.
            if (engine.onPopupNavigation(request.url)) return true
            // Links, scripts and redirects only: loadUrl() from the address bar never comes here.
            if (engine.isTabUnderAd(request.url)) return true
        }
        return window?.shouldOverrideUrlLoading(request.url.toString()) == true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // window.open(url) loads its first document without shouldOverrideUrlLoading.
        if (request.isForMainFrame && engine.onPopupNavigation(request.url)) return blocked()
        val page = this.view.currentOriginalUrl
        if (page != null && page.toString().startsWith(Config.HOME_PAGE_URL, ignoreCase = true)) {
            HomePageHelper.shouldInterceptRequest(request)?.let { return it }
            val address = request.url.toString()
            if (address.startsWith(Config.HOME_PAGE_URL)) {
                return homePageAsset(view, address) ?: super.shouldInterceptRequest(view, request)
            }
        }
        if (page == null || window?.isAdBlockingEnabled() != true || !engine.isAd(request, page)) {
            return super.shouldInterceptRequest(view, request)
        }
        this.view.countBlockedAd()
        engine.adRedirect(request, page)?.let { dataUrlResponse(it, request.requestHeaders) }?.let { return it }
        return AdblockSurrogates.responseFor(view.context, request.url, request.requestHeaders) ?: blocked()
    }

    /** The packaged home page is served from assets/pages/home under its own https address. */
    private fun homePageAsset(view: WebView, address: String): WebResourceResponse? {
        val relative = address.substring(Config.HOME_PAGE_URL.length).takeUnless { it.isEmpty() || it == "/" } ?: "index.html"
        val path = "pages/home/$relative"
        return try {
            WebResourceResponse(URLConnection.guessContentTypeFromName(path) ?: "application/octet-stream", "UTF-8", view.context.assets.open(path))
        } catch (_: IOException) {
            Log.w(WebViewEx.TAG, "Home page asset not found: $path")
            null
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        this.view.onDocumentStarted(url)
        engine.onPageStarted(url)
        this.view.onDocumentStartReported(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        window?.onPageFinished(url)
        engine.onPopupPageFinished(url)
        this.view.onDocumentFinished(url)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        logStream(request.url) { "transport=${error.errorCode}" }
        super.onReceivedError(view, request, error)
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        logStream(request.url) { "http=${response.statusCode}" }
        super.onReceivedHttpError(view, request, response)
    }

    /** Video stream failures are the ones worth a log line, within a budget. */
    private inline fun logStream(url: Uri, detail: () -> String) {
        val kind = StreamDiagnostics.kind(url) ?: return
        if (view.streamLogBudget.allow()) Log.w(WebViewEx.WEB_VIEW_TAG, "Stream $kind ${detail()} host=${url.host}")
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = engine.onRenderProcessGone()

    /**
     * A certificate error stops the load. Only the certificate the user accepted on the warning page,
     * and only once, may proceed. Errors of other hosts' resources are not shown as a page warning.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        Log.e(WebViewEx.TAG, "Certificate error for ${error.url}")
        val owner = this.view
        if (owner.trustSsl && CertificateTrust.isSameCertificate(owner.lastSSLError?.certificate, error.certificate)) {
            owner.trustSsl = false
            owner.lastSSLError = null
            handler.proceed()
            return
        }
        handler.cancel()
        val page = owner.currentOriginalUrl ?: return
        if (error.url?.toUri()?.host == page.host) owner.showCertificateErrorPage(error)
    }

    override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
        super.onScaleChanged(view, oldScale, newScale)
        window?.onScaleChanged(oldScale, newScale)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String, isReload: Boolean) {
        if (!isReload) window?.onVisited(url)
    }

    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        val context = this.view.context
        val user = EditText(context).apply {
            setHint(com.reiniertutoriales.vireolumatv.common.R.string.username)
            isSingleLine = true
        }
        val password = EditText(context).apply {
            setHint(com.reiniertutoriales.vireolumatv.common.R.string.password)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.http_auth_title)
            .setCancelable(false)
            .setView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(user)
                addView(password)
            })
            .setPositiveButton(android.R.string.ok) { _, _ -> handler?.proceed(user.text.toString(), password.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> handler?.cancel() }
            .show()
    }

    private fun blocked() = WebResourceResponse("text/plain", "utf-8", "".byteInputStream()).apply {
        setStatusCodeAndReasonPhrase(403, "Blocked")
    }

    /** A blocked request answered with the filter list's `data:` replacement resource. */
    private fun dataUrlResponse(dataUrl: String, requestHeaders: Map<String, String>?): WebResourceResponse? {
        val comma = dataUrl.indexOf(',')
        if (!dataUrl.startsWith("data:") || comma < 0) return null
        val header = dataUrl.substring(5, comma)
        val body = dataUrl.substring(comma + 1)
        val bytes = try {
            if (header.endsWith(";base64")) Base64.decode(body, Base64.DEFAULT) else Uri.decode(body).toByteArray()
        } catch (_: IllegalArgumentException) {
            return null
        }
        val mime = header.substringBefore(';').ifEmpty { "text/plain" }
        return WebResourceResponse(mime, "utf-8", 200, "OK", AdblockSurrogates.replacementHeaders(requestHeaders), bytes.inputStream())
    }
}
