package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import com.reiniertutoriales.vireolumatv.adblock.AdVerdict
import com.reiniertutoriales.vireolumatv.adblock.AdblockRequestClassifier
import com.reiniertutoriales.vireolumatv.adblock.AdblockSurrogates

/**
 * Ad blocking for requests a service worker makes. Once a worker controls a site, the page's own
 * requests go through it and never reach [BrowserWebViewClient]; WebView reports them here, on a
 * background thread and without a tab. Requests whose site is unknown (no web Referer) pass: the
 * party of the request cannot be decided. Blocked counts per tab do not include these requests.
 */
internal class ServiceWorkerFilter(context: Context, private val policy: () -> Policy?) : ServiceWorkerClientCompat() {
    /** The blocker's answers; the window installs one while it lives and removes it when destroyed. */
    interface Policy {
        /** Whether ads are blocked for the site at this address. Called off the main thread. */
        fun blocksAds(site: Uri): Boolean
        /** Whether the request is blocked, with its `$redirect` resource if the lists name one. */
        fun verdict(url: Uri, type: String?, site: Uri): AdVerdict
    }

    /**
     * The shown tab's blocker choice, published whole from the main thread so worker threads never
     * read the tab's mutable fields. [tabChoice] applies only to the shown tab's own [host].
     */
    data class ShownSite(val host: String?, val tabChoice: Boolean?) {
        fun blocks(site: Uri, byDefault: Boolean): Boolean {
            val same = host != null && host.equals(site.host, ignoreCase = true)
            return (if (same) tabChoice else null) ?: byDefault
        }

        companion object {
            val NONE = ShownSite(null, null)
        }
    }

    private val appContext = context.applicationContext

    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val rules = policy() ?: return null
        val url = request.url
        if (url.scheme != "https" && url.scheme != "http") return null
        val headers = request.requestHeaders.orEmpty()
        val site = AdblockRequestClassifier.serviceWorkerContext(headers) ?: return null
        if (!rules.blocksAds(site)) return null
        val type = AdblockRequestClassifier.classify(url, headers, mainFrame = false)
        val verdict = rules.verdict(url, type, site)
        if (!verdict.blocked) return null
        verdict.redirect?.let { BlockedResponses.dataUrl(it, headers) }?.let { return it }
        return AdblockSurrogates.responseFor(appContext, url, headers) ?: BlockedResponses.blocked()
    }

    companion object {
        private const val TAG = "ServiceWorkerFilter"

        /** Read on WebView's worker threads; null lets every request through. */
        @Volatile var policy: Policy? = null
        internal var installed = false
            private set

        /**
         * Registers the process's filter once, on the main thread, after a WebView was created (so
         * the provider and the private data directory are ready). False when the provider cannot
         * intercept service workers or registering fails; browsing goes on either way.
         */
        fun install(context: Context): Boolean {
            if (installed) return true
            return try {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
                    !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) return false
                ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(ServiceWorkerFilter(context) { policy })
                installed = true
                true
            } catch (error: Throwable) {
                Log.w(TAG, "Service worker filtering unavailable", error)
                false
            }
        }
    }
}
