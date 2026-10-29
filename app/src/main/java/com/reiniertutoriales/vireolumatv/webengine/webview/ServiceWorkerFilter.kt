package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import com.reiniertutoriales.vireolumatv.adblock.AdblockRequestClassifier
import com.reiniertutoriales.vireolumatv.adblock.AdblockSurrogates

/**
 * Ad blocking for requests a service worker makes. Once a worker controls a site, the page's own
 * requests go through it and never reach [BrowserWebViewClient]; WebView reports them here, on a
 * background thread and without a tab. Blocked counts per tab do not include them.
 */
internal class ServiceWorkerFilter(context: Context, private val policy: () -> Policy?) : ServiceWorkerClientCompat() {
    /** The blocker's answers; the window installs one while it lives and removes it when destroyed. */
    interface Policy {
        /** Whether ads are blocked for the site at this address. */
        fun blocksAds(site: Uri): Boolean
        fun isAd(url: Uri, type: String?, site: Uri): Boolean
        fun redirect(url: Uri, type: String?, site: Uri): String?
    }

    private val appContext = context.applicationContext

    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val rules = policy() ?: return null
        val url = request.url
        if (url.scheme != "https" && url.scheme != "http") return null
        val headers = request.requestHeaders.orEmpty()
        val site = AdblockRequestClassifier.serviceWorkerContext(url, headers)
        if (!rules.blocksAds(site)) return null
        val type = AdblockRequestClassifier.classify(url, headers, mainFrame = false)
        if (!rules.isAd(url, type, site)) return null
        rules.redirect(url, type, site)?.let { BlockedResponses.dataUrl(it, headers) }?.let { return it }
        return AdblockSurrogates.responseFor(appContext, url, headers) ?: BlockedResponses.blocked()
    }

    companion object {
        /** Read on WebView's worker threads; null lets every request through. */
        @Volatile var policy: Policy? = null
        private var installed = false

        /**
         * Registers the process's filter once, on the main thread, after the WebView data directory
         * is set. False when the WebView provider cannot intercept service workers.
         */
        fun install(context: Context): Boolean {
            if (installed) return true
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) return false
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(ServiceWorkerFilter(context) { policy })
            installed = true
            return true
        }
    }
}
