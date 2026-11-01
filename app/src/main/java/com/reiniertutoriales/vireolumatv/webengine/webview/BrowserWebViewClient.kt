package com.reiniertutoriales.vireolumatv.webengine.webview

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.text.InputType
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
internal class BrowserWebViewClient(private val view: BrowserWebView) : WebViewClient() {
    // Capture on construction; request interception runs off the WebView UI thread.
    private val resourceContext = view.context.applicationContext
    private val window get() = view.window
    private val engine get() = view.engine

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.isForMainFrame) {
            // A popunder's redirect chain (ad network, then advertiser) arrives here.
            if (engine.onPopupNavigation(request.url)) return true
            // Links, scripts and redirects only: loadUrl() from the address bar never comes here.
            if (engine.isTabUnderAd(request.url)) return true
        }
        return window?.interceptNavigation(request.url.toString()) == true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // window.open(url) loads its first document without shouldOverrideUrlLoading.
        if (request.isForMainFrame && engine.onPopupNavigation(request.url)) return BlockedResponses.blocked()
        val page = this.view.currentOriginalUrl
        if (page != null && page.toString().startsWith(Config.HOME_PAGE_URL, ignoreCase = true)) {
            StartPageAssets.shouldInterceptRequest(request)?.let { return it }
            val address = request.url.toString()
            if (address.startsWith(Config.HOME_PAGE_URL)) {
                return homePageAsset(address) ?: super.shouldInterceptRequest(view, request)
            }
        }
        if (page == null || window?.blocksAds != true) return super.shouldInterceptRequest(view, request)
        val verdict = engine.adVerdict(request, page)
        if (!verdict.blocked) return super.shouldInterceptRequest(view, request)
        this.view.countBlockedAd()
        verdict.redirect?.let { BlockedResponses.dataUrl(it, request.requestHeaders) }?.let { return it }
        return AdblockSurrogates.responseFor(resourceContext, request.url, request.requestHeaders) ?: BlockedResponses.blocked()
    }

    /** The packaged home page is served from assets/pages/home under its own https address. */
    private fun homePageAsset(address: String): WebResourceResponse? {
        val relative = safeHomeAssetPath(address) ?: return null
        val path = "pages/home/$relative"
        return try {
            WebResourceResponse(URLConnection.guessContentTypeFromName(path) ?: "application/octet-stream", "UTF-8", resourceContext.assets.open(path))
        } catch (_: IOException) {
            Log.w(BrowserWebView.TAG, "Home page asset not found: $path")
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
        if (view.streamLogBudget.allow()) Log.w(BrowserWebView.LOG_TAG, "Stream $kind ${detail()} host=${url.host}")
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = engine.onRenderProcessGone()

    /**
     * A certificate error stops the load. Only the certificate the user accepted on the warning page,
     * for that same host and only once, may proceed. Errors of other hosts' resources are not shown
     * as a page warning.
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        Log.e(BrowserWebView.TAG, "Certificate error for ${error.url}")
        val owner = this.view
        val trusted = owner.trustedCertificate
        if (trusted != null && CertificateTrust.mayProceed(trusted, error)) {
            owner.trustedCertificate = null
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
        window?.onScale(newScale)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String, isReload: Boolean) {
        if (!isReload) window?.onVisited(url)
    }

    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        val context = this.view.context
        val user = EditText(context).apply {
            setHint(R.string.http_auth_user)
            isSingleLine = true
        }
        val password = EditText(context).apply {
            setHint(R.string.http_auth_password)
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
}

/** Reject traversal, encoded separators, queries and fragments before addressing local assets. */
internal fun safeHomeAssetPath(address: String): String? {
    val uri = runCatching { Uri.parse(address) }.getOrNull() ?: return null
    val base = Uri.parse(Config.HOME_PAGE_URL)
    if (!uri.scheme.equals(base.scheme, true) || !uri.host.equals(base.host, true) ||
        uri.port != base.port || uri.encodedQuery != null || uri.encodedFragment != null) return null
    val basePath = base.encodedPath.orEmpty()
    val path = uri.encodedPath.orEmpty()
    if (!path.startsWith(basePath)) return null
    val encoded = path.removePrefix(basePath)
    if (encoded.isEmpty() || encoded == "/") return "index.html"
    if (encoded.startsWith("/") || encoded.contains('%') || encoded.contains('\\') ||
        encoded.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
    return encoded
}

