package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceResponse
import com.reiniertutoriales.vireolumatv.adblock.AdblockSurrogates

/** Answers for blocked requests, shared by page and service worker interception. */
internal object BlockedResponses {
    fun blocked() = WebResourceResponse("text/plain", "utf-8", "".byteInputStream()).apply {
        setStatusCodeAndReasonPhrase(403, "Blocked")
    }

    /** A blocked request answered with the filter list's `data:` replacement resource. */
    fun dataUrl(dataUrl: String, requestHeaders: Map<String, String>?): WebResourceResponse? {
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
