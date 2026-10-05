package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri
import java.util.Locale

/** Small metadata-only diagnostics: never store a stream URL, query token or response body. */
internal object StreamDiagnostics {
    fun kind(url: Uri): String? = when (url.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
        "m3u8" -> "hls-playlist"
        "mpd" -> "dash-manifest"
        "ts", "m4s", "aac", "mp4", "webm", "m4a" -> "media"
        else -> null
    }
}

/** Called on WebView's UI thread; bounded work and no per-event history. */
internal class StreamLogBudget(private val clock: () -> Long) {
    private var windowStart = clock()
    private var emitted = 0
    fun allow(): Boolean {
        val now = clock()
        if (now - windowStart >= 10_000L || now < windowStart) {
            windowStart = now
            emitted = 0
        }
        if (emitted >= 8) return false
        emitted++
        return true
    }
}
