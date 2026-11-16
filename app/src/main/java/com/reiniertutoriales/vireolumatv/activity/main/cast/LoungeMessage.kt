package com.reiniertutoriales.vireolumatv.activity.main.cast

import org.json.JSONArray
import org.json.JSONObject

/**
 * A YouTube Lounge (BrowserChannel) message. Ported from yt-cast-receiver's `Message`
 * (MIT, Patrick Kan; see NOTICE.md).
 */
internal class LoungeMessage(
    val aid: Int?,
    val name: String,
    /** Incoming: JSONObject, JSONArray, String or null. Outgoing: string-convertible map values. */
    val payload: Any?,
) {
    val payloadObject: JSONObject? get() = payload as? JSONObject

    internal companion object {
        private val INCOMING = Regex("""\[(\d+),\["(.+?)"(?:,(.*?))?\]\]""")

        fun parseIncoming(data: String): List<LoungeMessage> {
            val text = data.replace("\r", "").replace("\n", "")
            return INCOMING.findAll(text).mapNotNull { m ->
                val aid = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val name = m.groupValues[2]
                val raw = m.groups[3]?.value
                val payload = raw?.let {
                    runCatching {
                        val arr = JSONArray("[$it]")
                        if (arr.length() == 1) arr.get(0) else arr
                    }.getOrNull()
                }
                LoungeMessage(aid, name, payload)
            }.toList()
        }

        fun nowPlaying(aid: Int?, state: PlayerState?): LoungeMessage {
            val payload = LinkedHashMap<String, Any?>()
            val current = state?.current
            if (state != null && current != null) {
                payload["currentTime"] = state.position
                payload["duration"] = state.duration
                payload["cpn"] = state.cpn
                payload["loadedTime"] = 0
                payload["videoId"] = current.id
                payload["state"] = state.status
                payload["seekableStartTime"] = 0
                payload["seekableEndTime"] = state.duration
                current.playlistId?.let { payload["listId"] = it }
                current.index?.let { payload["currentIndex"] = it }
                current.ctt?.let { payload["ctt"] = it }
                current.params?.let { payload["params"] = it }
            }
            if (state != null && state.status in LOADED_STATUSES) payload["loadedTime"] = state.duration
            return LoungeMessage(aid, "nowPlaying", payload)
        }

        fun onStateChange(aid: Int?, state: PlayerState): LoungeMessage {
            val payload = linkedMapOf<String, Any?>(
                "state" to state.status,
                "currentTime" to state.position,
                "duration" to state.duration,
                "loadedTime" to 0,
                "seekableStartTime" to 0,
                "seekableEndTime" to state.duration,
                "cpn" to state.cpn,
            )
            if (state.status in LOADED_STATUSES) payload["loadedTime"] = state.duration
            return LoungeMessage(aid, "onStateChange", payload)
        }

        fun onVolumeChanged(aid: Int?, level: Int, muted: Boolean) =
            LoungeMessage(aid, "onVolumeChanged", linkedMapOf<String, Any?>("volume" to level, "muted" to muted))

        /** No autoplay queue of our own: the page plays what YouTube decides. */
        fun onAutoplayModeChanged(aid: Int?) =
            LoungeMessage(aid, "onAutoplayModeChanged", linkedMapOf<String, Any?>("autoplayMode" to "UNSUPPORTED"))

        fun onHasPreviousNextChanged(aid: Int?, hasPrevious: Boolean, hasNext: Boolean) =
            LoungeMessage(
                aid,
                "onHasPreviousNextChanged",
                linkedMapOf<String, Any?>("hasPrevious" to hasPrevious, "hasNext" to hasNext),
            )

        fun autoplayUpNext(aid: Int?, videoId: String?) =
            LoungeMessage(aid, "autoplayUpNext", videoId?.let { linkedMapOf<String, Any?>("videoId" to it) })

        fun loungeScreenDisconnected() = LoungeMessage(null, "loungeScreenDisconnected", emptyMap<String, Any?>())

        private val LOADED_STATUSES = setOf(PlayerState.PLAYING, PlayerState.PAUSED, PlayerState.LOADING)
    }
}

/** A video in the cast queue; [theme] is the lounge theme of the app that sent it ("cl" or "m"). */
internal data class CastVideo(
    val id: String,
    val theme: String,
    val playlistId: String? = null,
    val index: Int? = null,
    val ctt: String? = null,
    val params: String? = null,
)

internal data class PlayerState(
    val status: Int,
    val position: Double,
    val duration: Double,
    val cpn: String,
    val current: CastVideo?,
    val volumeLevel: Int,
    val muted: Boolean,
) {
    companion object {
        const val IDLE = -1
        const val PLAYING = 1
        const val PAUSED = 2
        const val LOADING = 3
        const val STOPPED = 4
    }
}
