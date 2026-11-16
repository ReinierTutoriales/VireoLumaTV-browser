package com.reiniertutoriales.vireolumatv.activity.main.cast

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal interface CastSession {
    /** "cl" YouTube or "m" YouTube Music. */
    val theme: String
    fun send(messages: List<LoungeMessage>)
}

/** Plays cast videos; reports what it observes through [YouTubeCastReceiver.onPlayerObserved]. */
internal interface CastPlayer {
    fun play(video: CastVideo, positionSeconds: Double)
    fun pause()
    fun resume()
    fun stopPlayback()
    fun seek(positionSeconds: Double)
    fun setVolume(level: Int, muted: Boolean)
}

/**
 * Receiver logic: which session is active, which senders are connected, the cast queue and the
 * replies senders expect. Ported from yt-cast-receiver's `YouTubeApp`, `Playlist` and `Player`
 * state handling (MIT, Patrick Kan; see NOTICE.md). Runs on one thread (main).
 */
internal class YouTubeCastReceiver(private val player: CastPlayer) {

    private data class Sender(val id: String, val name: String, val theme: String)

    private var activeSession: CastSession? = null
    private val connectedSenders = ArrayList<Sender>()

    private var queueId: String? = null
    private var videoIds: List<String> = emptyList()
    private var current: CastVideo? = null

    var state = PlayerState(
        status = PlayerState.IDLE,
        position = 0.0,
        duration = 0.0,
        cpn = newCpn(),
        current = null,
        volumeLevel = 100,
        muted = false,
    )
        private set

    val activeTheme: String? get() = activeSession?.theme

    /** DIAL launch from a phone of [session]'s app: the next sender joins that session. */
    fun onDialLaunch(session: CastSession) {
        switchActiveSession(session)
    }

    fun onMessages(session: CastSession, messages: List<LoungeMessage>, initial: Boolean) {
        messages.forEach { handle(session, it, initial) }
    }

    /** What the page's video element shows now; sends senders the changes, as the reference player does. */
    fun onPlayerObserved(
        positionSeconds: Double,
        durationSeconds: Double,
        paused: Boolean,
        volumeLevel: Int,
        muted: Boolean,
        pageVideoId: String?,
    ) {
        val playing = current ?: return
        if (state.status == PlayerState.STOPPED || state.status == PlayerState.IDLE) return
        var video = playing
        if (pageVideoId != null && pageVideoId != playing.id) {
            // The page moved on by itself (end of track, its own next button).
            val index = videoIds.indexOf(pageVideoId)
            video = playing.copy(id = pageVideoId, index = index.takeIf { it >= 0 })
            current = video
        }
        val status = if (paused) PlayerState.PAUSED else PlayerState.PLAYING
        val expected = state.position + if (state.status == PlayerState.PLAYING) OBSERVE_INTERVAL_S else 0.0
        val jumped = kotlin.math.abs(positionSeconds - expected) > SEEK_TOLERANCE_S
        val next = state.copy(
            status = status,
            position = positionSeconds,
            duration = durationSeconds,
            current = video,
            volumeLevel = volumeLevel,
            muted = muted,
            cpn = if (video.id != playing.id) newCpn() else state.cpn,
        )
        val significant = next.status != state.status || next.current != state.current ||
            next.duration != state.duration || jumped ||
            next.volumeLevel != state.volumeLevel || next.muted != state.muted
        if (significant) setState(next, aid = null) else state = next
    }

    private fun handle(session: CastSession, message: LoungeMessage, initial: Boolean) {
        val aid = message.aid
        val payload = message.payloadObject
        val isActive = session === activeSession
        val replies = ArrayList<LoungeMessage>()
        when (message.name) {
            "getNowPlaying" -> replies += LoungeMessage.nowPlaying(aid, if (isActive) state else null)
            "loungeStatus" -> handleLoungeStatus(session, payload, aid, initial, replies)
            "setPlaylist", "updatePlaylist" -> {
                if (!isActive) return
                handlePlaylist(session, message.name, payload ?: JSONObject(), aid, replies)
            }
            "next" -> if (isActive) moveBy(1, aid)
            "previous" -> if (isActive) moveBy(-1, aid)
            "pause" -> if (isActive && current != null) {
                player.pause()
                setState(state.copy(status = PlayerState.PAUSED), aid)
            }
            "play" -> if (isActive && current != null) {
                player.resume()
                setState(state.copy(status = PlayerState.PLAYING), aid)
            }
            "stopVideo" -> if (isActive) stop(aid)
            "seekTo" -> if (isActive && current != null) {
                val position = payload?.optString("newTime")?.toDoubleOrNull() ?: return
                player.seek(position)
                setState(state.copy(position = position), aid)
            }
            "getVolume" -> replies += LoungeMessage.onVolumeChanged(aid, state.volumeLevel, state.muted)
            "setVolume" -> if (isActive) {
                val level = payload?.optString("volume")?.toIntOrNull() ?: return
                val muted = payload.optString("muted").trim().equals("true", ignoreCase = true)
                if (level != state.volumeLevel || muted != state.muted) {
                    player.setVolume(level, muted)
                    setState(state.copy(volumeLevel = level, muted = muted), aid)
                }
            }
            "setAutoplayMode" -> if (isActive) replies += LoungeMessage.onAutoplayModeChanged(aid)
        }
        if (replies.isNotEmpty()) session.send(replies)
    }

    private fun handleLoungeStatus(
        session: CastSession,
        payload: JSONObject?,
        aid: Int?,
        initial: Boolean,
        replies: MutableList<LoungeMessage>,
    ) {
        val senders = parseSenders(payload?.optString("devices"))
        if (initial) {
            // Phones already in the lounge when the screen (re)connects.
            if (senders.isNotEmpty()) {
                connectedSenders.clear()
                connectedSenders += senders
                activeSession = session
                replies += LoungeMessage.onAutoplayModeChanged(aid)
                replies += LoungeMessage.onHasPreviousNextChanged(aid, hasPrevious(), hasNext())
                replies += LoungeMessage.nowPlaying(aid, null)
            }
            return
        }
        val details = runCatching { JSONObject(payload?.optString("connectionEventDetails").orEmpty()) }.getOrNull()
        val deviceId = details?.optString("deviceId")?.takeIf { it.isNotEmpty() }
        var handled = false
        if (deviceId != null) {
            val connecting = senders.firstOrNull { it.id == deviceId }
            val disconnecting = if (connecting == null) connectedSenders.firstOrNull { it.id == deviceId } else null
            if (connecting != null) {
                switchActiveSession(session)
                if (connectedSenders.none { it.id == connecting.id }) {
                    connectedSenders += connecting
                    replies += LoungeMessage.onAutoplayModeChanged(aid)
                    replies += LoungeMessage.onHasPreviousNextChanged(aid, hasPrevious(), hasNext())
                    replies += LoungeMessage.nowPlaying(aid, state)
                    replies += LoungeMessage.onStateChange(aid, state)
                }
                handled = true
            }
            if (disconnecting != null) {
                // Playback continues in the tab: the phone leaving does not close what the TV shows.
                if (session === activeSession) connectedSenders.removeAll { it.id == disconnecting.id }
                handled = true
            }
        }
        if (!handled) {
            val active = session === activeSession
            replies += LoungeMessage.onHasPreviousNextChanged(aid, active && hasPrevious(), active && hasNext())
            replies += LoungeMessage.onAutoplayModeChanged(aid)
        }
    }

    private fun handlePlaylist(
        session: CastSession,
        name: String,
        data: JSONObject,
        aid: Int?,
        replies: MutableList<LoungeMessage>,
    ) {
        val lastId = data.optString("videoIds").split(',').lastOrNull()
        if (videoIds.lastOrNull() != lastId) replies += LoungeMessage.autoplayUpNext(aid, null)
        val before = current
        val navBefore = hasPrevious() to hasNext()
        val listId = data.optString("listId").takeIf { it.isNotEmpty() }
        val loneVideoId = data.optString("videoId").takeIf { it.isNotEmpty() }
        if (listId == null && name == "setPlaylist" && loneVideoId != null) {
            // The lounge can forward a YouTube Music setPlaylist with only videoId and currentTime;
            // the reference would ignore it, so it plays as a one-track queue here.
            queueId = null
            videoIds = listOf(loneVideoId)
            current = CastVideo(id = loneVideoId, theme = session.theme, index = 0)
        } else if (listId == null) {
            queueId = null
            videoIds = emptyList()
            current = null
        } else {
            queueId = listId
            videoIds = data.optString("videoIds").split(',').filter { it.isNotEmpty() }
            if (name == "setPlaylist") {
                val videoId = data.optString("videoId").takeIf { it.isNotEmpty() }
                val index = data.optString("currentIndex").toIntOrNull()
                current = if (videoId != null && index != null) {
                    CastVideo(
                        id = videoId,
                        theme = session.theme,
                        playlistId = listId,
                        index = index,
                        ctt = data.optString("ctt").takeIf { it.isNotEmpty() },
                        params = data.optString("params").takeIf { it.isNotEmpty() },
                    )
                } else {
                    null
                }
            } else {
                val playing = current
                val index = playing?.let { videoIds.indexOf(it.id) } ?: -1
                current = if (playing != null && index >= 0) {
                    playing.copy(
                        playlistId = listId,
                        index = index,
                        params = data.optString("params").takeIf { it.isNotEmpty() },
                    )
                } else {
                    null
                }
            }
        }
        val after = current
        when {
            name == "setPlaylist" && (before?.id != after?.id || before?.index != after?.index) -> {
                if (state.status != PlayerState.STOPPED) stop(aid)
                if (after != null) play(after, data.optString("currentTime").toDoubleOrNull() ?: 0.0, aid)
            }
            name == "updatePlaylist" && after == null -> stop(aid)
            else -> {
                replies += LoungeMessage.nowPlaying(aid, state.copy(current = after))
                if (navBefore != (hasPrevious() to hasNext())) {
                    replies += LoungeMessage.onHasPreviousNextChanged(aid, hasPrevious(), hasNext())
                }
            }
        }
    }

    private fun play(video: CastVideo, position: Double, aid: Int?) {
        setState(state.copy(status = PlayerState.LOADING, current = video, position = position, cpn = newCpn()), aid)
        player.play(video, position)
        setState(state.copy(status = PlayerState.PLAYING), aid)
    }

    private fun stop(aid: Int?) {
        player.stopPlayback()
        setState(state.copy(status = PlayerState.STOPPED, position = 0.0), aid)
    }

    private fun moveBy(step: Int, aid: Int?) {
        val playing = current ?: return
        val index = playing.index ?: videoIds.indexOf(playing.id)
        val target = index + step
        val id = videoIds.getOrNull(target)
        if (index < 0 || id == null) {
            stop(aid)
            return
        }
        val video = playing.copy(id = id, index = target)
        current = video
        play(video, 0.0, aid)
    }

    private fun switchActiveSession(session: CastSession) {
        val previous = activeSession
        if (previous === session) return
        activeSession = session
        if (previous != null) {
            // Only one sender app at a time, as in the reference.
            connectedSenders.clear()
            previous.send(listOf(LoungeMessage.loungeScreenDisconnected()))
        }
    }

    /** Port of the reference's player state event: tells senders what changed. */
    private fun setState(next: PlayerState, aid: Int?) {
        val previous = state
        state = next.copy(current = next.current ?: current)
        val session = activeSession ?: return
        if (connectedSenders.isEmpty()) return
        val current = state
        val statusChanged = previous.status != current.status
        val positionChanged = previous.position != current.position
        val volumeChanged = previous.volumeLevel != current.volumeLevel || previous.muted != current.muted
        val nowPlayingChanged = previous.current?.id != current.current?.id ||
            previous.current?.index != current.current?.index
        val messages = ArrayList<LoungeMessage>()
        if (statusChanged || positionChanged) messages += LoungeMessage.onStateChange(aid, current)
        if (nowPlayingChanged || (statusChanged && previous.status != PlayerState.PLAYING)) {
            messages += LoungeMessage.nowPlaying(aid, current)
            messages += LoungeMessage.onHasPreviousNextChanged(aid, hasPrevious(), hasNext())
        }
        if (volumeChanged) messages += LoungeMessage.onVolumeChanged(aid, current.volumeLevel, current.muted)
        if (messages.isNotEmpty()) session.send(messages)
    }

    private fun hasPrevious(): Boolean = (currentIndex() ?: 0) > 0

    private fun hasNext(): Boolean = currentIndex()?.let { it < videoIds.size - 1 } ?: false

    private fun currentIndex(): Int? = current?.let { it.index ?: videoIds.indexOf(it.id).takeIf { i -> i >= 0 } }

    private fun parseSenders(devices: String?): List<Sender> {
        val array = runCatching { JSONArray(devices.orEmpty()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val device = array.optJSONObject(i) ?: return@mapNotNull null
            if (device.optString("type") != "REMOTE_CONTROL") return@mapNotNull null
            val id = device.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = device.optString("name").ifEmpty { device.optString("clientName") }.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val theme = device.optString("theme").takeIf { it == THEME_YOUTUBE || it == THEME_MUSIC }
                ?: return@mapNotNull null
            Sender(id, name, theme)
        }
    }

    internal companion object {
        const val THEME_YOUTUBE = "cl"
        const val THEME_MUSIC = "m"
        const val OBSERVE_INTERVAL_S = 2.0
        private const val SEEK_TOLERANCE_S = 3.0

        private fun newCpn(): String = UUID.randomUUID().toString().replace("-", "").take(16)
    }
}
