package com.reiniertutoriales.vireolumatv.utils

import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream

/** File names and payloads for downloads that come from web pages and servers. */
object DownloadUtils {
    private const val GENERIC_NAME = "downloadfile"
    private const val OCTET_STREAM = "application/octet-stream"

    /**
     * The name to save a download under: the Content-Disposition name (RFC 6266, including the
     * encoded `filename*` form of RFC 5987), else the last segment of [url] without its query, else
     * [GENERIC_NAME]. A name without an extension gets one from [mimeType]; an extension that
     * contradicts a specific [mimeType] is replaced by that type's extension.
     */
    fun guessFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        val name = contentDisposition?.let(::dispositionName)?.substringAfterLast('/')
            ?: nameFromUrl(url)
            ?: GENERIC_NAME
        val firstDot = name.indexOf('.')
        if (firstDot < 0) return name + extensionFor(mimeType)
        val stem = name.substring(0, firstDot)
        val extension = name.substring(firstDot)
        return stem + (replacementExtension(name, mimeType) ?: extension)
    }

    private fun nameFromUrl(url: String): String? {
        val path = Uri.decode(url).let { decoded ->
            val query = decoded.indexOf('?')
            if (query > 0) decoded.substring(0, query) else decoded
        }
        if (path.endsWith('/')) return null
        val slash = path.lastIndexOf('/')
        return if (slash >= 0) path.substring(slash + 1) else null
    }

    /** ".ext" for [mimeType]; ".html" or ".txt" for text without a known extension; ".bin" otherwise. */
    private fun extensionFor(mimeType: String?): String {
        mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }?.let { return ".$it" }
        return when {
            mimeType == null || !mimeType.startsWith("text/", ignoreCase = true) -> ".bin"
            mimeType.equals("text/html", ignoreCase = true) -> ".html"
            else -> ".txt"
        }
    }

    /** The extension [mimeType] calls for when the name's last extension names another type. */
    private fun replacementExtension(name: String, mimeType: String?): String? {
        if (mimeType == null || mimeType == OCTET_STREAM) return null
        val types = MimeTypeMap.getSingleton()
        val named = types.getMimeTypeFromExtension(name.substringAfterLast('.')) ?: return null
        if (named.equals(mimeType, ignoreCase = true)) return null
        return types.getExtensionFromMimeType(mimeType)?.let { ".$it" }
    }

    /**
     * The file name in an "attachment" or "inline" Content-Disposition value, or null. The encoded
     * `filename*` parameter wins over `filename` wherever it appears; other parameters are ignored.
     */
    private fun dispositionName(value: String): String? {
        val parts = splitParameters(value)
        val type = parts.firstOrNull()?.trim()?.lowercase() ?: return null
        if (type != "attachment" && type != "inline") return null
        var plain: String? = null
        var encoded: String? = null
        for (part in parts.drop(1)) {
            val equals = part.indexOf('=')
            if (equals < 0) continue
            val key = part.substring(0, equals).trim().lowercase()
            val raw = part.substring(equals + 1).trim()
            when (key) {
                "filename" -> plain = unquote(raw)
                "filename*" -> encoded = decodeExtValue(raw)
            }
        }
        return encoded ?: plain
    }

    /** Splits at ';' outside double quotes. */
    private fun splitParameters(value: String): List<String> {
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var escaped = false
        for (c in value) {
            when {
                escaped -> escaped = false
                quoted && c == '\\' -> escaped = true
                c == '"' -> quoted = !quoted
                c == ';' && !quoted -> {
                    parts += current.toString()
                    current.clear()
                    continue
                }
            }
            current.append(c)
        }
        parts += current.toString()
        return parts
    }

    /** A quoted-string without its quotes and backslash escapes; a token as it is. */
    private fun unquote(raw: String): String {
        if (raw.length < 2 || raw.first() != '"' || raw.last() != '"') return raw
        val text = StringBuilder()
        var escaped = false
        for (c in raw.substring(1, raw.length - 1)) {
            if (!escaped && c == '\\') escaped = true else {
                text.append(c)
                escaped = false
            }
        }
        return text.toString()
    }

    /** RFC 5987 ext-value "charset'language'%XX…" for UTF-8 or ISO-8859-1; null otherwise. */
    private fun decodeExtValue(raw: String): String? {
        val fields = raw.split('\'', limit = 3)
        if (fields.size != 3) return null
        val charset = when (fields[0].trim().lowercase()) {
            "utf-8" -> Charsets.UTF_8
            "iso-8859-1" -> Charsets.ISO_8859_1
            else -> return null
        }
        val bytes = ByteArrayOutputStream()
        val text = fields[2]
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val hex = if (c == '%' && i + 2 <= text.lastIndex) text.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes.write(hex)
                i += 3
                continue
            }
            if (c.code < 128 && !c.isWhitespace()) bytes.write(c.code)
            i++
        }
        return String(bytes.toByteArray(), charset)
    }

    private val UNSAFE_FILE_NAME_CHARS = Regex("[\\x00-\\x1F\\x7F:*?\"<>|]")

    /**
     * Makes a file name from a page or server safe as one path segment: drops any directory part,
     * replaces control and reserved characters and leading dots. Null when nothing usable is left.
     */
    fun sanitizeFileName(name: String?): String? {
        if (name == null) return null
        return name.substringAfterLast('/').substringAfterLast('\\')
            .replace(UNSAFE_FILE_NAME_CHARS, "_")
            .trim().trimStart('.').trim()
            .ifEmpty { null }
    }

    /**
     * The base64 payload of a data URL from FileReader.readAsDataURL(). Its media type is not
     * used: browsers write "application/octet-stream" when Blob.type is empty. Input without
     * ";base64," is returned unchanged.
     */
    fun dataUrlBase64Payload(dataUrl: String): String = dataUrl.substringAfter(";base64,", dataUrl)
}
