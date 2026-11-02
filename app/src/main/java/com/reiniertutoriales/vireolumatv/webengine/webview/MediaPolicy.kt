package com.reiniertutoriales.vireolumatv.webengine.webview

import android.annotation.SuppressLint
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
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

/**
 * What one codec's hardware decoders handle: the highest 16:9 tier (by height) decoded at 30 and
 * at 60 frames per second, 0 when not even the smallest tier is, and whether 10-bit (HDR) streams
 * are decoded. The best decoder counts, since the platform picks it for the stream.
 */
internal data class HardwareVideo(val maxHeight30: Int, val maxHeight60: Int, val tenBit: Boolean) {
    /** One decoder as the policy asks it; production wraps MediaCodecInfo.VideoCapabilities. */
    interface Probe {
        fun decodes(width: Int, height: Int, fps: Int): Boolean
        val tenBit: Boolean
    }

    companion object {
        /** Standard heights, highest first; widths follow 16:9. */
        val TIERS = intArrayOf(4320, 2160, 1440, 1080, 720, 480, 360)

        fun widthFor(height: Int) = (height * 16 + 8) / 9 and 1.inv()

        fun of(decoders: List<Probe>): HardwareVideo? {
            if (decoders.isEmpty()) return null
            fun best(fps: Int) = TIERS.firstOrNull { h -> decoders.any { it.decodes(widthFor(h), h, fps) } } ?: 0
            return HardwareVideo(best(30), best(60), decoders.any { it.tenBit })
        }
    }
}

/** Which video codecs and sizes pages may use, so playback stays on the TV's hardware decoders. */
internal object MediaPolicy {
    private const val TAG = "MediaPolicy"
    private const val MIME_VP8 = MediaFormat.MIMETYPE_VIDEO_VP8
    private const val MIME_VP9 = "video/x-vnd.on2.vp9"
    private const val MIME_AV1 = "video/av01"
    /** [Config.videoMaxHeight] value that lifts every limit except decoders the TV does not have. */
    const val NO_LIMIT = -1

    /** Codec family as media_policy.js names it, with its decoder MIME type. */
    private val FAMILIES = mapOf(
        "avc" to MediaFormat.MIMETYPE_VIDEO_AVC, "hevc" to MediaFormat.MIMETYPE_VIDEO_HEVC,
        "vp8" to MIME_VP8, "vp9" to MIME_VP9, "av1" to MIME_AV1)

    // Plain int constants, inlined at compile time; an older decoder simply never reports them.
    @SuppressLint("InlinedApi")
    private val TEN_BIT_PROFILES = mapOf(
        "hevc" to setOf(CodecProfileLevel.HEVCProfileMain10, CodecProfileLevel.HEVCProfileMain10HDR10,
            CodecProfileLevel.HEVCProfileMain10HDR10Plus),
        "vp9" to setOf(CodecProfileLevel.VP9Profile2, CodecProfileLevel.VP9Profile2HDR,
            CodecProfileLevel.VP9Profile2HDR10Plus, CodecProfileLevel.VP9Profile3, CodecProfileLevel.VP9Profile3HDR,
            CodecProfileLevel.VP9Profile3HDR10Plus),
        "av1" to setOf(CodecProfileLevel.AV1ProfileMain10, CodecProfileLevel.AV1ProfileMain10HDR10,
            CodecProfileLevel.AV1ProfileMain10HDR10Plus))

    // Enumerate once per process; retain each codec's actual hardware capability independently.
    private val hardwareCodecs: List<MediaCodecInfo>? by lazy {
        try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder && isHardware(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Can not query hardware decoders", e)
            null // Unknown: do not restrict what the page may use.
        }
    }

    private val hardwareDecoders: Set<String>? by lazy {
        hardwareCodecs?.flatMap { it.supportedTypes.toList() }?.map { it.lowercase(Locale.ROOT) }?.toSet()
    }

    /** What each codec family's hardware decoders handle; families without one are left out. */
    private val hardwareVideo: Map<String, HardwareVideo> by lazy {
        val codecs = hardwareCodecs ?: return@lazy emptyMap()
        FAMILIES.mapNotNull { (family, mime) ->
            val probes = codecs.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
                .mapNotNull { info -> runCatching { probe(info.getCapabilitiesForType(mime), family) }.getOrNull() }
            HardwareVideo.of(probes)?.let { family to it }
        }.toMap()
    }

    /** Off the main thread at start-up, so the first WebView does not wait for MediaCodecList. */
    fun prewarm() { hardwareVideo }

    /**
     * Android 10+ decoders list the sizes and rates they sustain (performance points, measured by
     * the vendor); older ones only state their limits, which areSizeAndRateSupported checks.
     */
    private fun probe(caps: MediaCodecInfo.CodecCapabilities, family: String): HardwareVideo.Probe? {
        val video = caps.videoCapabilities ?: return null
        val tenBit = TEN_BIT_PROFILES[family]?.let { profiles -> caps.profileLevels.any { it.profile in profiles } } ?: false
        val points = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) video.supportedPerformancePoints else null
        return object : HardwareVideo.Probe {
            override val tenBit = tenBit
            override fun decodes(width: Int, height: Int, fps: Int): Boolean {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !points.isNullOrEmpty()) {
                    val wanted = MediaCodecInfo.VideoCapabilities.PerformancePoint(width, height, fps)
                    return points.any { it.covers(wanted) }
                }
                // Decoders state sizes in their alignment (1080 is often stored as 1088).
                fun up(value: Int, step: Int) = (value + step - 1) / step * step
                return video.areSizeAndRateSupported(width, height, fps.toDouble()) ||
                    video.areSizeAndRateSupported(up(width, video.widthAlignment), up(height, video.heightAlignment), fps.toDouble())
            }
        }
    }

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

    /** Hardware limits the script applies; none with [NO_LIMIT]. */
    fun hardwareLimits(maxHeight: Int): Map<String, HardwareVideo> = if (maxHeight == NO_LIMIT) emptyMap() else hardwareVideo

    /** Script argument; null when the policy changes nothing. */
    fun configJson(blocked: List<String>, maxHeight: Int, hardware: Map<String, HardwareVideo> = emptyMap()): String? {
        if (blocked.isEmpty() && maxHeight <= 0 && hardware.isEmpty()) return null
        val hw = hardware.entries.joinToString(",") { (family, v) ->
            "\"$family\":{\"h30\":${v.maxHeight30},\"h60\":${v.maxHeight60},\"ten\":${v.tenBit}}"
        }
        return "{\"block\":[" + blocked.joinToString(",") { "\"$it\"" } + "],\"maxHeight\":" +
            maxHeight.coerceAtLeast(0) + (if (hardware.isEmpty()) "" else ",\"hw\":{$hw}") + "}"
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
        val json = MediaPolicy.configJson(MediaPolicy.blockedCodecs(), config.videoMaxHeight,
            MediaPolicy.hardwareLimits(config.videoMaxHeight))
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
