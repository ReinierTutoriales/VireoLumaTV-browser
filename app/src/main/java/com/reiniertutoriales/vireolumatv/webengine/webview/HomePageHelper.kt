package com.reiniertutoriales.vireolumatv.webengine.webview

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.reiniertutoriales.vireolumatv.singleton.Favicons
import java.io.ByteArrayOutputStream

/**
 * Serves home-page favicons from memory. Never starts a lookup on WebView's interception
 * thread: a missing icon answers 404 immediately.
 */
object HomePageHelper {
    private const val FAVICON_SCHEME = "favicon"

    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        if (request.url.scheme != FAVICON_SCHEME) return null
        val host = request.url.host ?: return null
        val favicon = Favicons.cached(host) ?: return notFound()
        val bytes = ByteArrayOutputStream().also { favicon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return WebResourceResponse("image/png", "utf-8", bytes.toByteArray().inputStream())
    }

    private fun notFound() = WebResourceResponse(null, null, 404, "Not Found", null, null)
}
