package com.reiniertutoriales.vireolumatv.utils

import android.util.JsonReader
import android.util.JsonToken
import android.webkit.MimeTypeMap
import java.io.BufferedReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Finds a site's icon candidates: `<link>` icons in the page head, then the web app manifest's
 * icons when the page offers no usable bitmap, then "/favicon.ico". Pages and manifests are
 * third-party input, so reading is bounded by [MAX_DOCUMENT_CHARS] and [MAX_ICON_CANDIDATES].
 */
class FaviconExtractor {

    /** One candidate. [src] is absolute once a base URL is given; sizes "WxH" fill [width] and [height]. */
    class IconInfo(
        var src: String,
        var type: String? = null,
        var rel: String? = null,
        sizes: String? = null,
        baseURL: URL? = null,
    ) {
        var width = 0
        var height = 0

        init {
            sizes?.trim()?.substringBefore(' ')?.lowercase(Locale.ROOT)?.split('x')?.takeIf { it.size == 2 }?.let { (w, h) ->
                val parsedWidth = w.toIntOrNull()
                val parsedHeight = h.toIntOrNull()
                if (parsedWidth != null && parsedHeight != null && parsedWidth >= 0 && parsedHeight >= 0) {
                    width = parsedWidth
                    height = parsedHeight
                }
            }
            if (baseURL != null) src = URL(baseURL, src).toString()
            if (type == null) type = MimeTypeMap.getFileExtensionFromUrl(src)?.let { TYPES[it.lowercase(Locale.ROOT)] }
        }
    }

    /** Reads [url] and returns its candidates, ending with "/favicon.ico" when none is a usable bitmap. */
    fun extractFavIconsFromURL(url: URL): ArrayList<IconInfo> {
        val (icons, manifest) = read(url) { extractFavIconsFromHTML(url, it) }
        if (manifest != null && icons.none(::isFetchableBitmap)) {
            try {
                val manifestUrl = URL(url, manifest)
                icons += read(manifestUrl) { extractFavIconsFromWebManifest(manifestUrl, it) }
                    .take(MAX_ICON_CANDIDATES - icons.size)
            } catch (_: Exception) {
                // An unreachable or broken manifest leaves the page's own candidates and the default.
            }
        }
        if (icons.none(::isFetchableBitmap)) {
            icons += IconInfo(DEFAULT_ICON_SRC, DEFAULT_ICON_TYPE, null, DEFAULT_ICON_SIZE_STRING, url)
        }
        return icons
    }

    /** Icon links in the head of [html] and the manifest address, if the page names one. */
    fun extractFavIconsFromHTML(baseURL: URL?, html: BufferedReader): Pair<ArrayList<IconInfo>, String?> {
        val head = try {
            readHead(html)
        } catch (_: Exception) {
            return ArrayList<IconInfo>() to null
        }
        val icons = ArrayList<IconInfo>()
        var manifest: String? = null
        for (attributes in linkTags(head)) {
            val href = attributes["href"] ?: continue
            val rel = attributes["rel"] ?: continue
            val tokens = rel.lowercase(Locale.ROOT).split(WHITESPACE).toSet()
            when {
                tokens.any { it in ICON_RELS } -> if (icons.size < MAX_ICON_CANDIDATES) {
                    val appleTouch = tokens.any { it.startsWith("apple-touch-icon") }
                    val sizes = attributes["sizes"] ?: if (appleTouch) APPLE_TOUCH_SIZE else null
                    icons += IconInfo(href, attributes["type"], rel, sizes, baseURL)
                }
                "manifest" in tokens -> manifest = href
            }
        }
        return icons to manifest
    }

    /** The "icons" of a web app manifest; anything malformed ends the list without an error. */
    fun extractFavIconsFromWebManifest(manifestURL: URL?, manifest: Reader): ArrayList<IconInfo> {
        val icons = ArrayList<IconInfo>()
        try {
            JsonReader(BoundedReader(manifest, MAX_DOCUMENT_CHARS)).use { json ->
                json.isLenient = true
                if (json.peek() != JsonToken.BEGIN_OBJECT) return icons
                json.beginObject()
                while (json.hasNext()) {
                    if (json.nameOrSkip() == "icons") return icons.also { readIcons(json, manifestURL, it) }
                    else json.skipValue()
                }
            }
        } catch (_: Exception) {
            // Keep what was read before the manifest broke off.
        }
        return icons
    }

    private fun readIcons(json: JsonReader, base: URL?, icons: ArrayList<IconInfo>) {
        if (json.peek() != JsonToken.BEGIN_ARRAY) return json.skipValue()
        json.beginArray()
        while (json.hasNext()) {
            if (json.peek() != JsonToken.BEGIN_OBJECT) {
                json.skipValue()
                continue
            }
            json.beginObject()
            var src: String? = null
            var type: String? = null
            var sizes: String? = null
            while (json.hasNext()) {
                when (json.nameOrSkip()) {
                    null -> Unit
                    "src" -> src = json.textOrSkip()
                    "type" -> type = json.textOrSkip()
                    "sizes" -> sizes = json.textOrSkip()
                    else -> json.skipValue()
                }
            }
            json.endObject()
            if (src != null && icons.size < MAX_ICON_CANDIDATES) icons += IconInfo(src, type, null, sizes, base)
        }
        json.endArray()
    }

    private fun JsonReader.nameOrSkip(): String? =
        if (peek() == JsonToken.NAME) nextName() else null.also { skipValue() }

    private fun JsonReader.textOrSkip(): String? = when (peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        JsonToken.NULL -> null.also { nextNull() }
        else -> null.also { skipValue() }
    }

    /** Text up to the end of the head (or the start of the body), at most [MAX_DOCUMENT_CHARS]. */
    private fun readHead(html: BufferedReader): String {
        val text = StringBuilder()
        val buffer = CharArray(4096)
        while (text.length < MAX_DOCUMENT_CHARS) {
            val count = html.read(buffer, 0, minOf(buffer.size, MAX_DOCUMENT_CHARS - text.length))
            if (count == -1) break
            val searchFrom = maxOf(0, text.length - HEAD_END_LOOKBACK)
            text.append(buffer, 0, count)
            val end = headEnd(text, searchFrom)
            if (end >= 0) return text.substring(0, end)
        }
        return text.toString()
    }

    /** Where "</head" or "<body" starts at or after [from], or -1. */
    private fun headEnd(text: CharSequence, from: Int): Int {
        var i = text.indexOf('<', from)
        while (i >= 0) {
            var j = i + 1
            while (j < text.length && text[j].isWhitespace()) j++
            if (text.startsWithIgnoringCase("/head", j) || text.startsWithIgnoringCase("body", j)) return i
            i = text.indexOf('<', i + 1)
        }
        return -1
    }

    /** The attributes of every `<link …>` tag in [html], names in lower case. */
    private fun linkTags(html: String): Sequence<Map<String, String>> = sequence {
        var i = html.indexOf('<')
        while (i >= 0) {
            val nameStart = i + 1
            if (html.startsWithIgnoringCase("link", nameStart) && html.getOrNull(nameStart + 4)?.let { it.isWhitespace() || it == '>' || it == '/' } == true) {
                val (attributes, end) = attributes(html, nameStart + 4)
                yield(attributes)
                i = html.indexOf('<', end)
            } else {
                i = html.indexOf('<', nameStart)
            }
        }
    }

    /** Parses `name=value`, `name="value"`, `name='value'` and bare names up to '>'; returns them and the end. */
    private fun attributes(html: String, start: Int): Pair<Map<String, String>, Int> {
        val result = HashMap<String, String>()
        var i = start
        fun skipSpace() { while (i < html.length && html[i].isWhitespace()) i++ }
        while (i < html.length) {
            skipSpace()
            if (i >= html.length || html[i] == '>') break
            if (html[i] == '/') { i++; continue }
            val nameStart = i
            while (i < html.length && !html[i].isWhitespace() && html[i] != '=' && html[i] != '>') i++
            val name = html.substring(nameStart, i).lowercase(Locale.ROOT)
            skipSpace()
            if (i >= html.length || html[i] != '=') {
                if (name.isNotEmpty()) result.putIfAbsent(name, "")
                continue
            }
            i++
            skipSpace()
            val value = when (val quote = html.getOrNull(i)) {
                '"', '\'' -> {
                    val close = html.indexOf(quote, i + 1).let { if (it < 0) html.length else it }
                    html.substring(i + 1, close).also { i = minOf(html.length, close + 1) }
                }
                else -> {
                    val valueStart = i
                    while (i < html.length && !html[i].isWhitespace() && html[i] != '>') i++
                    html.substring(valueStart, i)
                }
            }
            if (name.isNotEmpty()) result.putIfAbsent(name, value.trim())
        }
        return result to minOf(i + 1, html.length)
    }

    private fun CharSequence.startsWithIgnoringCase(prefix: String, at: Int): Boolean =
        at + prefix.length <= length && regionMatches(at, prefix, 0, prefix.length, ignoreCase = true)

    private fun isFetchableBitmap(icon: IconInfo): Boolean = !icon.type.equals(SVG, ignoreCase = true) &&
        runCatching { URL(icon.src).protocol in WEB_SCHEMES }.getOrDefault(false)

    private fun <T> read(url: URL, parse: (BufferedReader) -> T): T {
        val connection = url.openConnection().apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        try {
            return connection.getInputStream().bufferedReader().use(parse)
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }

    companion object {
        const val DEFAULT_ICON_SRC = "/favicon.ico"
        const val DEFAULT_ICON_TYPE = "image/x-icon"
        const val DEFAULT_ICON_SIZE = 16
        const val DEFAULT_ICON_SIZE_STRING = "${DEFAULT_ICON_SIZE}x$DEFAULT_ICON_SIZE"
        const val MAX_DOCUMENT_CHARS = 256 * 1024
        private const val MAX_ICON_CANDIDATES = 32
        private const val CONNECTION_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val HEAD_END_LOOKBACK = 8
        private const val APPLE_TOUCH_SIZE = "180x180"
        private const val SVG = "image/svg+xml"
        private val WEB_SCHEMES = setOf("http", "https")
        private val WHITESPACE = Regex("\\s+")
        private val ICON_RELS = setOf("icon", "apple-touch-icon", "apple-touch-icon-precomposed", "fluid-icon")
        private val TYPES = mapOf("png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
            "ico" to DEFAULT_ICON_TYPE, "gif" to "image/gif", "svg" to SVG, "webp" to "image/webp")
    }
}
