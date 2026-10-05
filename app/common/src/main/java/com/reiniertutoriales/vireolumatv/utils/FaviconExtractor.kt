package com.reiniertutoriales.vireolumatv.utils

import android.util.JsonReader
import android.util.JsonToken
import android.webkit.MimeTypeMap
import java.io.BufferedReader
import java.io.Reader
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLConnection
import java.util.regex.Pattern


class FaviconExtractor {
    companion object {
        const val DEFAULT_ICON_SRC = "/favicon.ico"
        const val DEFAULT_ICON_TYPE = "image/x-icon"
        const val DEFAULT_ICON_SIZE = 16
        const val DEFAULT_ICON_SIZE_STRING = "${DEFAULT_ICON_SIZE}x$DEFAULT_ICON_SIZE"
        private const val CONNECTION_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
        const val MAX_DOCUMENT_CHARS = 256 * 1024
        private const val MAX_ICON_CANDIDATES = 32
    }

    private val headerClosingTagsPattern: Pattern = Pattern.compile("<\\s*(?:body|/\\s*head)(?:\\s+|>)")
    private val tagsPattern: Pattern = Pattern.compile("<(?!!)(?!/)\\s*([a-zA-Z\\d]+)((?s:.)*?)>")
    private val attributePattern: Pattern = Pattern.compile("(\\S+)=\\s*['\"]?([^>\"'\\s]+)['\"]?")

    class IconInfo (
        var src: String,
        var type: String? = null,
        var rel: String? = null,
        sizes: String? = null,
        baseURL: URL? = null
    ) {
        var width: Int = 0
        var height: Int = 0
        init {
            if (sizes != null) {
                try {
                    val sizesArr = sizes.split("x")
                    width = Integer.parseUnsignedInt(sizesArr[0])
                    height = Integer.parseUnsignedInt(sizesArr[1])
                } catch (e: Exception) {
                    //it is ok, just leave default values of w and h
                }
            }
            if (baseURL != null) {
                src = URL(baseURL, src).toString()
            }
            if (type == null) {
                val extension = MimeTypeMap.getFileExtensionFromUrl(src)
                if (extension != null) {
                    type = guessIconType(extension)
                }
            }
        }

        private fun guessIconType(iconExtension: String): String? {
            return if (iconExtension.equals("png", true)) {
                "image/png"
            } else if (iconExtension.equals("jpg", true)) {
                "image/jpeg"
            } else if (iconExtension.equals("ico", true)) {
                DEFAULT_ICON_TYPE
            } else if (iconExtension.equals("gif", true)) {
                "image/gif"
            } else if (iconExtension.equals("svg", true)) {
                "image/svg+xml"
            } else null
        }
    }

    /**
     * Downloads HTML by provided @param url, traverses all <link> tags and collects all found
     * icon as @return List<IconInfo>
     * Will also collect icons from web manifest
     *
     * @throws java.io.IOException
     */
    fun extractFavIconsFromURL(url: URL): ArrayList<IconInfo> {
        val (result, manifestHref) = readDocument(url) { extractFavIconsFromHTML(url, it) }
        // A manifest is unnecessary when HTML already supplied a usable bitmap icon.
        if (manifestHref != null && result.none { isFetchableBitmap(it) }) {
            val manifestURL = URL(url, manifestHref)
            try {
                val manifestIcons = readDocument(manifestURL) { extractFavIconsFromWebManifest(manifestURL, it) }
                result.addAll(manifestIcons.take(MAX_ICON_CANDIDATES - result.size))
            } catch (e: Exception) {
                //shit happens, but I don't think it's too important here
                e.printStackTrace()
            }
        }
        if (result.none { isFetchableBitmap(it) }) {
            result.add(
                IconInfo(
                "/favicon.ico",
                DEFAULT_ICON_TYPE,
                null,
                DEFAULT_ICON_SIZE_STRING,
                url
            )
            )
        }
        return result
    }

    fun extractFavIconsFromWebManifest(manifestURL: URL?, manifest: Reader): ArrayList<IconInfo> {
        val iconInfos = ArrayList<IconInfo>()
        val jsonReader = JsonReader(BoundedReader(manifest, MAX_DOCUMENT_CHARS))
        jsonReader.isLenient = true
        try {
            jsonReader.use {
                if (jsonReader.peek() != JsonToken.BEGIN_OBJECT) {
                    jsonReader.skipValue()
                    return iconInfos
                }
                jsonReader.beginObject()
                while (jsonReader.hasNext()) {
                    val name = nextNameOrSkip(jsonReader) ?: continue
                    if (name == "icons") {
                        readManifestIcons(jsonReader, manifestURL, iconInfos)
                        return@use
                    } else {
                        jsonReader.skipValue()
                    }
                }
                jsonReader.endObject()
            }
        } catch (e: Exception) {
            // Web manifests are third-party input. Ignore malformed manifests and keep the HTML/default favicon fallback.
            e.printStackTrace()
        }
        return iconInfos
    }

    private fun readManifestIcons(
        jsonReader: JsonReader,
        manifestURL: URL?,
        iconInfos: ArrayList<IconInfo>
    ) {
        if (jsonReader.peek() != JsonToken.BEGIN_ARRAY) {
            jsonReader.skipValue()
            return
        }
        jsonReader.beginArray()
        while (jsonReader.hasNext()) {
            if (jsonReader.peek() != JsonToken.BEGIN_OBJECT) {
                jsonReader.skipValue()
                continue
            }
            jsonReader.beginObject()
            var src: String? = null
            var type: String? = null
            var sizes: String? = null
            while (jsonReader.hasNext()) {
                when (nextNameOrSkip(jsonReader)) {
                    "src" -> src = nextStringOrSkip(jsonReader)
                    "sizes" -> sizes = nextStringOrSkip(jsonReader)
                    "type" -> type = nextStringOrSkip(jsonReader)
                    null -> Unit
                    else -> jsonReader.skipValue()
                }
            }
            jsonReader.endObject()
            if (src != null && iconInfos.size < MAX_ICON_CANDIDATES) {
                iconInfos.add(IconInfo(src, type, null, sizes, manifestURL))
            }
        }
        jsonReader.endArray()
    }

    private fun nextNameOrSkip(jsonReader: JsonReader): String? {
        if (jsonReader.peek() != JsonToken.NAME) {
            jsonReader.skipValue()
            return null
        }
        return jsonReader.nextName()
    }

    private fun nextStringOrSkip(jsonReader: JsonReader): String? {
        return when (jsonReader.peek()) {
            JsonToken.STRING,
            JsonToken.NUMBER -> jsonReader.nextString()
            JsonToken.BOOLEAN -> jsonReader.nextBoolean().toString()
            JsonToken.NULL -> {
                jsonReader.nextNull()
                null
            }
            else -> {
                jsonReader.skipValue()
                null
            }
        }
    }

    /**
     * @return @kotlin.Pair of list of IconInfo and href of web manifest (if found any)
     *
     * @throws java.io.IOException
     */
    fun extractFavIconsFromHTML(baseURL: URL?, html: BufferedReader): Pair<ArrayList<IconInfo>, String?> {
        val iconInfos = ArrayList<IconInfo>()
        var manifestHref: String? = null

        val headerPartOfHTML = StringBuilder()
        val buffer = CharArray(4096)
        try {
            while (headerPartOfHTML.length < MAX_DOCUMENT_CHARS) {
                val count = html.read(buffer, 0, minOf(buffer.size, MAX_DOCUMENT_CHARS - headerPartOfHTML.length))
                if (count == -1) break
                headerPartOfHTML.append(buffer, 0, count)
                if (headerClosingTagsPattern.matcher(headerPartOfHTML).find()) break
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(iconInfos, null)
        }

        val matcher = tagsPattern.matcher(headerPartOfHTML.toString())
        while (matcher.find()) {
            val tagName = matcher.group(1) ?: continue
            //println("tag name: $tagName")
            when (tagName) {
                "link" -> {
                    val attributes = matcher.group(2) ?: continue
                    //println("     rest of the tag: $attributes")
                    val attributeMatcher = attributePattern.matcher(attributes)
                    var rel: String? = null
                    var href: String? = null
                    var sizes: String? = null
                    var type: String? = null
                    while (attributeMatcher.find()) {
                        val attributeName = attributeMatcher.group(1) ?: continue
                        val attributeValue = attributeMatcher.group(2) ?: continue
                        //println("         attribute name: $attributeName    value: $attributeValue")
                        when (attributeName) {
                            "rel" -> rel = attributeValue
                            "href" -> href = attributeValue
                            "sizes" -> sizes = attributeValue
                            "type" -> type = attributeValue
                        }
                    }
                    if (href == null || rel == null) {
                        continue
                    }
                    if (rel.equals("icon", true) ||
                        rel.equals("apple-touch-icon", true) ||
                        rel.equals("shortcut icon", true) ||
                        rel.equals("shortcut", true) ||
                        rel.equals("fluid-icon", true)) {
                        if (sizes == null && rel.equals("apple-touch-icon", true)) {
                            sizes = "180x180"
                        }
                        if (iconInfos.size < MAX_ICON_CANDIDATES) iconInfos.add(IconInfo(href, type, rel, sizes, baseURL))
                    } else if (rel.equals("manifest", true)) {
                        manifestHref = href
                    }
                }
                "body" -> {
                    break
                }
            }
        }


        return Pair(iconInfos, manifestHref)
    }

    private fun isFetchableBitmap(icon: IconInfo): Boolean =
        !icon.type.equals("image/svg+xml", true) &&
            runCatching { URL(icon.src).protocol in listOf("http", "https") }.getOrDefault(false)

    private fun <T> readDocument(url: URL, read: (BufferedReader) -> T): T {
        val connection = url.openTimedConnection()
        try {
            return connection.getInputStream().bufferedReader().use(read)
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }

    private fun URL.openTimedConnection(): URLConnection {
        return openConnection().apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
    }
}