package com.reiniertutoriales.vireolumatv.webengine.webview

import android.media.MediaCodecInfo
import android.media.MediaCodecList
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
    private const val MIME_VP9 = "video/x-vnd.on2.vp9"
    private const val MIME_AV1 = "video/av01"

    // MediaCodecList enumeration is done once per process.
    private val hardwareVp9 by lazy { hasHardwareDecoder(MIME_VP9) }
    private val hardwareAv1 by lazy { hasHardwareDecoder(MIME_AV1) }

    /** Off the main thread at start-up, so the first WebView does not wait for MediaCodecList. */
    fun prewarm() {
        hardwareVp9
        hardwareAv1
    }

    fun blockedCodecs(mode: Config.VideoCodecPolicy): List<String> =
        blockedCodecs(mode, { hardwareVp9 }, { hardwareAv1 })

    /** VP9 also covers VP8: both are decoded in software by WebView when no hardware exists. */
    internal fun blockedCodecs(
        mode: Config.VideoCodecPolicy,
        hasVp9: () -> Boolean,
        hasAv1: () -> Boolean
    ): List<String> = when (mode) {
        Config.VideoCodecPolicy.OFF -> emptyList()
        Config.VideoCodecPolicy.FORCE_H264 -> listOf("vp9", "av1")
        Config.VideoCodecPolicy.AUTO -> buildList {
            if (!hasVp9()) add("vp9")
            if (!hasAv1()) add("av1")
        }
    }

    /** Script argument; null when the policy changes nothing. */
    fun configJson(blocked: List<String>, maxHeight: Int): String? {
        if (blocked.isEmpty() && maxHeight <= 0) return null
        return "{\"block\":[" + blocked.joinToString(",") { "\"$it\"" } + "],\"maxHeight\":" +
            maxHeight.coerceAtLeast(0) + "}"
    }

    private fun hasHardwareDecoder(mime: String): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                isHardware(info)
        }.also { Log.i(TAG, "Hardware decoder for $mime: $it") }
    } catch (e: Exception) {
        Log.w(TAG, "Can not query decoders for $mime", e)
        true // Unknown: do not restrict what the page may use.
    }

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
            MediaPolicy.blockedCodecs(config.videoCodecPolicy), config.videoMaxHeight)
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
