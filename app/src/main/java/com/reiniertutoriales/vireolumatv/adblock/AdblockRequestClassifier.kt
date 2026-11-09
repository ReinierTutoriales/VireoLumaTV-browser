package com.reiniertutoriales.vireolumatv.adblock

import android.net.Uri
import java.util.Locale

/** WebView exposes request headers, not the response MIME type. Prefer destination over Accept. */
internal object AdblockRequestClassifier {
    fun pageContext(url: Uri, currentPage: Uri, mainFrame: Boolean): Uri =
        if (mainFrame) url else currentPage

    /**
     * A service worker's request belongs to no tab: its site is the web Referer (the worker or the
     * page that started the fetch). Without one the site is unknown (null); guessing would decide
     * first- or third-party rules wrongly.
     */
    fun serviceWorkerContext(headers: Map<String, String>): Uri? {
        val referer = headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value
            ?.let(Uri::parse)
        return referer?.takeIf { (it.scheme == "https" || it.scheme == "http") && !it.host.isNullOrEmpty() }
    }

    fun classify(url: Uri, headers: Map<String, String>, mainFrame: Boolean): String {
        if (mainFrame) return "document"
        fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        // Explicit destination takes precedence; avoid parsing Accept on the common path.
        val destination = header("Sec-Fetch-Dest")
        when (destination?.lowercase(Locale.ROOT)) {
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
        // URL extensions are cheaper to inspect than splitting and normalizing Accept.
        val extension = url.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        when (extension) {
            "js", "mjs" -> return "script"
            "css" -> return "style"
            "png", "jpg", "jpeg", "webp", "svg", "gif", "bmp", "tiff", "ico", "avif" -> return "image"
            "woff", "woff2", "ttf", "otf", "eot" -> return "font"
            "mp4", "mov", "avi", "webm", "mp3", "m4a", "ogg", "wav", "m3u8", "mpd" -> return "media"
        }
        // Accept can mix HTML, images and wildcards; parse only on ambiguous requests.
        val accepted = header("Accept")?.split(',')?.map { it.substringBefore(';').trim().lowercase(Locale.ROOT) }
        if (destination.isNullOrBlank() && accepted != null && accepted.size > 1 &&
            accepted[0] == "text/html" && accepted[1] == "application/xhtml+xml") {
            return "subdocument"
        }
        val types = accepted?.filter { it != "*/*" }
        if (types.isNullOrEmpty()) return "unknown"
        if (types.all { it == "application/json" || it.endsWith("+json") }) return "xhr"
        if (types.all { it.startsWith("image/") }) return "image"
        if (types.all { it == "text/css" }) return "style"
        if (types.all { it == "application/javascript" || it == "text/javascript" }) return "script"
        if (types.all { it.startsWith("audio/") || it.startsWith("video/") }) return "media"
        if (types.all { it == "text/html" || it == "application/xhtml+xml" }) return "subdocument"
        return "unknown"
    }
}
