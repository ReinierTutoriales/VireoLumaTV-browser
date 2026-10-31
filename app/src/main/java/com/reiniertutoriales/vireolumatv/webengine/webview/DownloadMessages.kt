package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri

/**
 * The `VireoDownload` WebMessage protocol of generic_injects.js: newline-separated fields, the
 * free-form one last. Requests: `begin` url name, `size` token bytes type, `chunk` token offset
 * base64, `done` token url, `cancel` token. Replies: `begin` url token (empty when refused),
 * `size` token 1|0, `chunk` token received (-1 when the transfer closed).
 */
internal sealed class DownloadMessage {
    data class Begin(val url: String, val fileName: String?) : DownloadMessage()
    data class Size(val token: String, val bytes: Long, val mimeType: String) : DownloadMessage()
    data class Cancel(val token: String) : DownloadMessage()
    /** [base64] is one chunk of at most [BlobTransfers.CHUNK_BYTES] decoded bytes. */
    class Chunk(val token: String, val offset: Long, val base64: String) : DownloadMessage()
    data class Done(val token: String, val url: String) : DownloadMessage()

    companion object {
        private const val MAX_FIELD = 4096
        private const val MAX_CHUNK_TEXT = (BlobTransfers.CHUNK_BYTES + 2) / 3 * 4

        fun parse(message: String?): DownloadMessage? {
            message ?: return null
            val kind = message.substringBefore('\n', "")
            return when (kind) {
                "begin" -> fields(message, 3)?.let { (_, url, name) -> Begin(url, name.ifEmpty { null }) }
                "size" -> fields(message, 4)?.let { (_, token, bytes, type) ->
                    bytes.toLongOrNull()?.let { Size(token, it, type) }
                }
                "chunk" -> chunk(message)
                "done" -> fields(message, 3)?.let { (_, token, url) -> Done(token, url).takeIf { url.isNotEmpty() } }
                "cancel" -> fields(message, 2)?.let { Cancel(it[1]) }
                else -> null
            }
        }

        fun beginReply(url: String, token: String) = "begin\n$url\n$token"
        fun sizeReply(token: String, accepted: Boolean) = "size\n$token\n${if (accepted) 1 else 0}"
        fun chunkReply(token: String, received: Long) = "chunk\n$token\n$received"

        /** Small messages only: every field bounded, the last one may hold newlines. */
        private fun fields(message: String, count: Int): List<String>? {
            if (message.length > MAX_FIELD * count) return null
            return message.split('\n', limit = count).takeIf { parts ->
                parts.size == count && parts.drop(1).all { it.length <= MAX_FIELD } && parts[1].isNotEmpty()
            }
        }

        /** The base64 field is bounded by one chunk and is the only long one. */
        private fun chunk(message: String): Chunk? {
            if (message.length > 2 * MAX_FIELD + MAX_CHUNK_TEXT) return null
            val parts = message.split('\n', limit = 4)
            if (parts.size != 4 || parts[1].isEmpty() || parts[1].length > MAX_FIELD || parts[3].isEmpty()) return null
            val offset = parts[2].toLongOrNull()?.takeIf { it >= 0 } ?: return null
            return Chunk(parts[1], offset, parts[3])
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
