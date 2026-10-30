package com.reiniertutoriales.vireolumatv.webengine.webview

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.reiniertutoriales.vireolumatv.Config
import java.util.Locale

/** Which video codecs pages may use, so playback stays on the TV's hardware decoders. */
internal object MediaPolicy {
    private const val TAG = "MediaPolicy"
    private const val MIME_VP8 = MediaFormat.MIMETYPE_VIDEO_VP8
    private const val MIME_VP9 = "video/x-vnd.on2.vp9"
    private const val MIME_AV1 = "video/av01"

    // Enumerate once per process; retain each codec's actual hardware capability independently.
    private val hardwareDecoders: Set<String>? by lazy {
        try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && isHardware(it) }
                .flatMap { it.supportedTypes.toList() }
                .map { it.lowercase(Locale.ROOT) }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Can not query hardware decoders", e)
            null // Unknown: do not restrict what the page may use.
        }
    }

    /** Off the main thread at start-up, so the first WebView does not wait for MediaCodecList. */
    fun prewarm() { hardwareDecoders }

    // The platform decoder list is stable during this process. Avoid rebuilding the
    // same list on every page navigation and media-policy refresh.
    private val detectedBlockedCodecs: List<String> by lazy {
        blockedCodecs(hasHardwareDecoder(MIME_VP8), hasHardwareDecoder(MIME_VP9), hasHardwareDecoder(MIME_AV1))
    }

    fun blockedCodecs(): List<String> = detectedBlockedCodecs

    internal fun blockedCodecs(hasVp8: Boolean, hasVp9: Boolean, hasAv1: Boolean): List<String> = buildList {
        if (!hasVp8) add("vp8")
        if (!hasVp9) add("vp9")
        if (!hasAv1) add("av1")
    }

    /** Script argument; null when the policy changes nothing. */
    fun configJson(blocked: List<String>, maxHeight: Int): String? {
        if (blocked.isEmpty() && maxHeight <= 0) return null
        return "{\"block\":[" + blocked.joinToString(",") { "\"$it\"" } + "],\"maxHeight\":" +
            maxHeight.coerceAtLeast(0) + "}"
    }

    private fun hasHardwareDecoder(mime: String): Boolean = hardwareDecoders?.contains(mime) ?: true

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated
        val name = info.name.lowercase(Locale.ROOT)
        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.") ||
            name.startsWith("omx.ffmpeg.") || name.contains(".sw.") || name.endsWith(".sw"))
    }
}

/** Registers media_policy.js at document start in every frame and keeps it in sync with settings. */
internal class MediaPolicyController(private val view: WebView, private val config: Config) {
    private var registration: ScriptHandler? = null
    private var appliedJson: String? = null
    private var source: String? = null

    /** Cheap when nothing changed; call before each navigation so settings apply to the next page. */
    fun refresh() {
        val json = MediaPolicy.configJson(
            MediaPolicy.blockedCodecs(), config.videoMaxHeight)
        if (json == appliedJson) return
        registration?.remove()
        registration = null
        appliedJson = json
        if (json == null || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        registration = WebViewCompat.addDocumentStartJavaScript(view, script(json), setOf("*"))
    }

    /** Providers without document-start scripts: best effort once the page commits. */
    fun onPageStarted() {
        val json = appliedJson ?: return
        if (registration == null) view.evaluateJavascript(script(json), null)
    }

    fun destroy() {
        registration?.remove()
        registration = null
    }

    private fun script(json: String): String {
        val template = source ?: view.context.assets.open("media_policy.js").bufferedReader()
            .use { it.readText() }.also { source = it }
        return template.replace("__VIREO_MEDIA_POLICY__", json)
    }
}
