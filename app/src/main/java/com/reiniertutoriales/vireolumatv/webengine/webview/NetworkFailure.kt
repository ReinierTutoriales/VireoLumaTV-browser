package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.WebViewClient

/**
 * The top-level address whose load failed for lack of network, so the page can load again by
 * itself once the network is back. Any new top-level load forgets it. Written from WebView's
 * request thread and the UI thread.
 */
internal class NetworkFailure {
    @Volatile private var failedUrl: String? = null

    fun requested() { failedUrl = null }

    /** A main-frame load of [url] ended with WebView error [code]. */
    fun failed(url: String, code: Int) {
        if (code in NETWORK_ERRORS) failedUrl = url
    }

    /** Whether the page on screen, at [shownUrl], is the failed one and should load again; asked once. */
    fun takeReload(shownUrl: String?): Boolean {
        val failed = failedUrl ?: return false
        failedUrl = null
        return failed == shownUrl
    }

    private companion object {
        // Name resolution, connection, timeout and transfer failures; never a blocked or refused load.
        val NETWORK_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO,
        )
    }
}
