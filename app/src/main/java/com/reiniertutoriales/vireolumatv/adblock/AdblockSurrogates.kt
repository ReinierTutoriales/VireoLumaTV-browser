package com.reiniertutoriales.vireolumatv.adblock

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Inert replacements for a few blocked ad/analytics libraries. A 403 makes the script's onerror
 * fire and leaves its globals undefined, which is what "adblock detected" checks look for and what
 * breaks links waiting for analytics callbacks. Only used for requests the filters already block.
 */
object AdblockSurrogates {
    private val cache = ConcurrentHashMap<String, ByteArray>()

    internal fun assetFor(url: Uri): String? {
        val host = url.host?.lowercase(Locale.ROOT) ?: return null
        val path = url.path ?: return null
        return when {
            host == "pagead2.googlesyndication.com" && path.endsWith("/adsbygoogle.js") -> "adsbygoogle.js"
            (host == "securepubads.g.doubleclick.net" || host == "www.googletagservices.com") &&
                path == "/tag/js/gpt.js" -> "gpt.js"
            (host == "www.google-analytics.com" || host == "google-analytics.com" ||
                host == "ssl.google-analytics.com") && path == "/analytics.js" -> "analytics.js"
            host == "www.googletagmanager.com" && (path == "/gtm.js" || path == "/gtag/js") -> "gtm.js"
            else -> null
        }
    }

    /**
     * Headers of a replacement response. A credentialed CORS request (VAST/IMA ad tags use
     * withCredentials) rejects `Access-Control-Allow-Origin: *`, so the caller's origin is echoed.
     */
    fun replacementHeaders(requestHeaders: Map<String, String>?): Map<String, String> {
        val origin = requestHeaders?.entries?.firstOrNull { it.key.equals("Origin", true) }?.value
            ?.takeIf { it.isNotEmpty() && it != "null" }
            ?: return mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store")
        return mapOf("Access-Control-Allow-Origin" to origin, "Access-Control-Allow-Credentials" to "true",
            "Vary" to "Origin", "Cache-Control" to "no-store")
    }

    fun responseFor(context: Context, url: Uri, requestHeaders: Map<String, String>? = null): WebResourceResponse? {
        val asset = assetFor(url) ?: return null
        val bytes = cache.getOrPut(asset) {
            context.assets.open("surrogates/$asset").use { it.readBytes() }
        }
        return WebResourceResponse("application/javascript", "utf-8", 200, "OK",
            // A crossorigin script tag must still accept the replacement.
            replacementHeaders(requestHeaders),
            bytes.inputStream())
    }
}
