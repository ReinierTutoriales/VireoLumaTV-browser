package com.reiniertutoriales.vireolumatv.webengine.webview

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.reiniertutoriales.vireolumatv.singleton.FaviconsPool
import java.io.ByteArrayOutputStream

object HomePageHelper {
    fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        //check is scheme is favicon
        if (request.url.scheme == "favicon") {
            val host = request.url.host ?: return null
            // Never wait for external network work on WebView's request-interception thread.
            val favicon = FaviconsPool.peek(host)
            if (favicon != null) {
                val bytes = ByteArrayOutputStream()
                favicon.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                return WebResourceResponse("image/png",
                    "utf-8", bytes.toByteArray().inputStream())
            } else {
                return WebResourceResponse(null, null, 404, "Not Found", null, null)
            }
        }
        return null
    }
}
