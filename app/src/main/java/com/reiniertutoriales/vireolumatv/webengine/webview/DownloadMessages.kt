package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri

/**
 * The `VireoDownload` WebMessage protocol of generic_injects.js: newline-separated fields, the
 * free-form one last. Requests: `begin` url name, `size` token bytes type, `cancel` token,
 * `data` token url dataUrl. Replies: `begin` url token (empty when refused), `size` token 1|0.
 */
internal sealed class DownloadMessage {
    data class Begin(val url: String, val fileName: String?) : DownloadMessage()
    data class Size(val token: String, val bytes: Long, val mimeType: String) : DownloadMessage()
    data class Cancel(val token: String) : DownloadMessage()
    class Data(val token: String, val url: String, val dataUrl: String) : DownloadMessage()

    companion object {
        private const val MAX_FIELD = 4096

        fun parse(message: String?): DownloadMessage? {
            message ?: return null
            val kind = message.substringBefore('\n', "")
            return when (kind) {
                "begin" -> fields(message, 3)?.let { (_, url, name) -> Begin(url, name.ifEmpty { null }) }
                "size" -> fields(message, 4)?.let { (_, token, bytes, type) ->
                    bytes.toLongOrNull()?.let { Size(token, it, type) }
                }
                "cancel" -> fields(message, 2)?.let { Cancel(it[1]) }
                "data" -> data(message)
                else -> null
            }
        }

        fun beginReply(url: String, token: String) = "begin\n$url\n$token"
        fun sizeReply(token: String, accepted: Boolean) = "size\n$token\n${if (accepted) 1 else 0}"

        /** Small messages only: every field bounded, the last one may hold newlines. */
        private fun fields(message: String, count: Int): List<String>? {
            if (message.length > MAX_FIELD * count) return null
            return message.split('\n', limit = count).takeIf { parts ->
                parts.size == count && parts.drop(1).all { it.length <= MAX_FIELD } && parts[1].isNotEmpty()
            }
        }

        /** The data URL is not split or copied twice: only the two short leading fields are cut off. */
        private fun data(message: String): Data? {
            val tokenEnd = message.indexOf('\n', 5)
            if (tokenEnd !in 6..5 + MAX_FIELD) return null
            val urlEnd = message.indexOf('\n', tokenEnd + 1)
            if (urlEnd - tokenEnd - 1 !in 1..MAX_FIELD) return null
            return Data(message.substring(5, tokenEnd), message.substring(tokenEnd + 1, urlEnd), message.substring(urlEnd + 1))
        }
    }
}

/**
 * The document key a download message may act for: the origin of the visible web page, only for
 * its main frame and only when [sourceOrigin] is that same origin. Subframes, other origins, the
 * home page and the certificate warning get null.
 */
internal fun downloadMessageDocument(isMainFrame: Boolean, sourceOrigin: Uri?, documentUrl: Uri?): String? {
    if (!isMainFrame) return null
    val document = webOrigin(documentUrl) ?: return null
    return document.takeIf { it == webOrigin(sourceOrigin) }
}

/** `scheme://host[:port]` of an http(s) web page, default ports omitted; null for anything else. */
internal fun webOrigin(uri: Uri?): String? {
    if (!BridgePagePolicy.isNormalWebPage(uri)) return null
    val scheme = uri!!.scheme!!.lowercase()
    val port = uri.port.takeUnless { it == -1 || it == if (scheme == "https") 443 else 80 }
    return "$scheme://${uri.host!!.lowercase()}" + (port?.let { ":$it" } ?: "")
}
