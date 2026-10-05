package com.reiniertutoriales.vireolumatv.adblock

import android.net.Uri
import java.util.Locale

/** WebView exposes request headers, not the response MIME type. Prefer destination over Accept. */
internal object AdblockRequestClassifier {
    fun classify(url: Uri, headers: Map<String, String>, mainFrame: Boolean): String {
        if (mainFrame) return "document"
        fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        when (header("Sec-Fetch-Dest")?.lowercase(Locale.ROOT)) {
            "document", "iframe", "frame" -> return "subdocument"
            "script", "worker", "sharedworker", "serviceworker" -> return "script"
            "style" -> return "style"
            "image" -> return "image"
            "font" -> return "font"
            "audio", "video", "track" -> return "media"
            "object", "embed" -> return "object"
            "empty" -> return "xhr"
        }
        if (header("X-Requested-With").equals("XMLHttpRequest", true)) return "xhr"
        val extension = url.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        when (extension) {
            "js", "mjs" -> return "script"
            "css" -> return "style"
            "png", "jpg", "jpeg", "webp", "svg", "gif", "bmp", "tiff", "ico", "avif" -> return "image"
            "woff", "woff2", "ttf", "otf", "eot" -> return "font"
            "mp4", "mov", "avi", "webm", "mp3", "m4a", "ogg", "wav", "m3u8", "mpd" -> return "media"
        }
        // Accept can contain HTML, XML, images and */* together: it is not a resource type.
        val accepted = header("Accept")?.split(',')?.map {
            it.substringBefore(';').trim().lowercase(Locale.ROOT)
        }?.filter { it != "*/*" }
        if (accepted.isNullOrEmpty()) return "unknown"
        if (accepted.all { it.startsWith("image/") }) return "image"
        if (accepted.all { it == "text/css" }) return "style"
        if (accepted.all { it == "application/javascript" || it == "text/javascript" }) return "script"
        if (accepted.all { it.startsWith("audio/") || it.startsWith("video/") }) return "media"
        if (accepted.all { it == "text/html" || it == "application/xhtml+xml" }) return "subdocument"
        return "unknown"
    }

    // Native filter.h at truefedex/ad-block tag 0.0.4 uses C++ octal bit flags.
    // The Java wrapper's OBJECT(10) is decimal and incorrectly combines object+image.
    fun filterOption(type: String?): Int = when (type) {
        "script" -> 1
        "image" -> 2
        "style" -> 4
        "object" -> 8
        "xhr" -> 16
        "subdocument" -> 64
        "document" -> 128
        "font" -> 1 shl 20
        "media" -> 1 shl 21
        else -> 0
    }
}
