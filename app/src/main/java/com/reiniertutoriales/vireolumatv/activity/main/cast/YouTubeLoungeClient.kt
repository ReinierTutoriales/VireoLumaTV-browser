package com.reiniertutoriales.vireolumatv.activity.main.cast

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * Clean-room YouTube Lounge (BrowserChannel) client for receiving Cast commands from the
 * YouTube and YouTube Music mobile apps and reporting playback progress back.
 */
internal class YouTubeLoungeClient(
    private val scope: CoroutineScope,
    private val store: SessionStore,
    private val screenName: String = "VireoLumaTV",
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val onCommand: (LoungeCommand) -> Unit,
) : Closeable {

    interface SessionStore {
        val deviceUuid: String
        var screenId: String
        var loungeToken: String
        var loungeTokenExpiryMs: Long
    }

    sealed class LoungeCommand {
        data class SetPlaylist(
            val videoId: String,
            val listId: String?,
            val currentTimeSeconds: Double,
            val isMusic: Boolean,
        ) : LoungeCommand()

        data object Play : LoungeCommand()
        data object Pause : LoungeCommand()
        data class SeekTo(val newTimeSeconds: Double) : LoungeCommand()
        data object Next : LoungeCommand()
        data object Previous : LoungeCommand()
        data object StopVideo : LoungeCommand()
        data class SetVolume(val volume: Int) : LoungeCommand()
        data object Mute : LoungeCommand()
        data object Unmute : LoungeCommand()
        data object GetNowPlaying : LoungeCommand()
    }

    data class PlaybackState(
        val currentTimeSeconds: Double,
        val durationSeconds: Double,
        val stateCode: Int, // 1 = playing, 2 = paused, 3 = buffering
        val volume: Int,
        val muted: Boolean,
    )

    private val ioDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val ridCounter = AtomicInteger(10_000)
    private var listenJob: Job? = null
    @Volatile private var closed = false
    @Volatile private var sid: String? = null
    @Volatile private var gsessionId: String? = null
    @Volatile private var lastAid: Int = 0
    @Volatile private var ofsCounter: Int = 0
    @Volatile private var activePollConnection: HttpURLConnection? = null
    @Volatile private var bindCount = 0
    @Volatile private var themeSwitchPending = false

    /** The lounge only lets in phones whose app matches this theme: "cl" YouTube, "m" YouTube Music. */
    @Volatile var theme: String = THEME_YOUTUBE
        private set

    val screenId: String?
        get() = store.screenId.takeIf { it.isNotBlank() }

    fun start() {
        if (closed || listenJob?.isActive == true) return
        listenJob = scope.launch(ioDispatcher) {
            var backoffMs = 1_500L
            while (isActive && !closed) {
                val ok = runCatching {
                    val token = ensureScreenSession() ?: return@runCatching false
                    backoffMs = 1_500L
                    bindAndPoll(store.screenId, token)
                    true
                }.onFailure { err ->
                    if (!closed && !themeSwitchPending) Log.d(TAG, "Lounge session interrupted", err)
                }.getOrDefault(false)
                if (!isActive || closed) break
                if (themeSwitchPending) {
                    themeSwitchPending = false
                    backoffMs = 1_500L
                    continue
                }
                delay(if (ok) 1_000L else backoffMs)
                if (!ok) backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun pairWithCode(pairingCode: String, music: Boolean) {
        if (closed || pairingCode.isBlank()) return
        scope.launch(ioDispatcher) {
            prepareForDialLaunch(pairingCode, music)
        }
    }

    /** Switches theme and registers [pairingCode] (if provided) before DIAL returns 201 Created. */
    suspend fun prepareForDialLaunch(pairingCode: String?, music: Boolean) {
        if (closed) return
        kotlinx.coroutines.withContext(ioDispatcher) {
            runCatching {
                switchTheme(if (music) THEME_MUSIC else THEME_YOUTUBE)
                if (pairingCode.isNullOrBlank()) return@runCatching
                val token = ensureScreenSession() ?: return@runCatching
                val body = formEncode(
                    "access_type" to "permanent",
                    "app" to "vireolumatv",
                    "device_id" to store.deviceUuid,
                    "lounge_token" to token,
                    "pairing_code" to pairingCode,
                    "screen_id" to store.screenId,
                    "screen_name" to screenName,
                )
                postForm("$LOUNGE_API/pairing/register_pairing_code", body)
            }.onFailure { err ->
                Log.w(TAG, "Failed to prepare DIAL launch", err)
            }
        }
    }

    fun sendNowPlaying(videoId: String?, listId: String?, currentTimeSeconds: Double, stateCode: Int) {
        if (closed) return
        val params = buildList {
            add("req0__sc" to "nowPlaying")
            if (!videoId.isNullOrBlank()) {
                add("req0_videoId" to videoId)
                add("req0_currentTime" to "%.3f".format(currentTimeSeconds))
                add("req0_state" to stateCode.toString())
            }
            if (!listId.isNullOrBlank()) add("req0_listId" to listId)
        }
        sendSessionEvent(*params.toTypedArray())
    }

    fun sendPlaybackState(state: PlaybackState) {
        sendSessionEvent(
            "req0__sc" to "onStateChange",
            "req0_currentTime" to "%.3f".format(state.currentTimeSeconds),
            "req0_duration" to "%.3f".format(state.durationSeconds),
            "req0_state" to state.stateCode.toString(),
            "req0_volume" to state.volume.coerceIn(0, 100).toString(),
            "req0_muted" to state.muted.toString(),
        )
    }

    /** Rebinds the screen with [wanted] before the phone joins, or the lounge rejects it (unmatchingTheme). */
    private suspend fun switchTheme(wanted: String) {
        if (theme == wanted && bindCount > 0) return
        val before = bindCount
        themeSwitchPending = true
        theme = wanted
        val conn = activePollConnection
        runCatching { conn?.disconnect() }
        if (conn == null) {
            listenJob?.cancel()
            listenJob = null
            start()
        }
        var waitedMs = 0L
        while (bindCount == before && waitedMs < THEME_SWITCH_TIMEOUT_MS && !closed) {
            delay(50)
            waitedMs += 50
        }
    }

    private fun sendSessionEvent(vararg reqParams: Pair<String, String>) {
        val currentSid = sid ?: return
        val currentGsid = gsessionId ?: return
        val token = store.loungeToken.takeIf { it.isNotBlank() } ?: return
        val currentScreenId = store.screenId.takeIf { it.isNotBlank() } ?: return
        if (closed) return

        scope.launch(ioDispatcher) {
            runCatching {
                val rid = ridCounter.incrementAndGet()
                val ofs = ofsCounter++
                val query = formEncode(
                    "CVER" to "1",
                    "RID" to rid.toString(),
                    "SID" to currentSid,
                    "VER" to "8",
                    "app" to "tv-html5",
                    "device" to "LOUNGE_SCREEN",
                    "gsessionid" to currentGsid,
                    "id" to store.deviceUuid,
                    "loungeIdToken" to token,
                    "mdx-version" to "3",
                    "name" to screenName,
                    "screen_id" to currentScreenId,
                    "theme" to theme,
                )
                val body = formEncode("count" to "1", "ofs" to ofs.toString(), *reqParams)
                postForm("$LOUNGE_API/bc/bind?$query", body)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        listenJob?.cancel()
        listenJob = null
        runCatching { activePollConnection?.disconnect() }
        activePollConnection = null
        sid = null
        gsessionId = null
    }

    private fun ensureScreenSession(): String? {
        if (store.screenId.isBlank()) {
            val generated = postForm("$LOUNGE_API/pairing/generate_screen_id", "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            store.screenId = generated
        }
        val now = System.currentTimeMillis()
        if (store.loungeToken.isNotBlank() && store.loungeTokenExpiryMs - 60_000L > now) {
            return store.loungeToken
        }
        val batchJson = postForm(
            "$LOUNGE_API/pairing/get_lounge_token_batch",
            formEncode("screen_ids" to store.screenId),
        ) ?: return null
        val parsed = runCatching { JSONObject(batchJson) }.getOrNull() ?: return null
        val screen = parsed.optJSONArray("screens")?.optJSONObject(0) ?: return null
        val token = screen.optString("loungeToken").takeIf { it.isNotBlank() } ?: return null
        val expiry = screen.optLong("expiration", now + 3_600_000L)
        store.loungeToken = token
        store.loungeTokenExpiryMs = expiry
        return token
    }

    private suspend fun bindAndPoll(screenId: String, loungeToken: String) {
        val rid = ridCounter.incrementAndGet()
        ofsCounter = 0
        lastAid = 0
        sid = null
        gsessionId = null
        val boundTheme = theme
        val initQuery = formEncode(
            "CVER" to "1",
            "RID" to rid.toString(),
            "VER" to "8",
            "app" to "tv-html5",
            "device" to "LOUNGE_SCREEN",
            "id" to store.deviceUuid,
            "loungeIdToken" to loungeToken,
            "mdx-version" to "3",
            "name" to screenName,
            "screen_id" to screenId,
            "t" to "1",
            "theme" to boundTheme,
        )
        val initResponse = postForm("$LOUNGE_API/bc/bind?$initQuery", formEncode("count" to "0")) ?: return
        processChunkText(initResponse)
        val currentSid = sid ?: return
        val currentGsid = gsessionId ?: return
        if (theme == boundTheme) {
            themeSwitchPending = false
            bindCount++
        }

        while (currentCoroutineContext().isActive && !closed && theme == boundTheme) {
            val pollQuery = formEncode(
                "AID" to lastAid.toString(),
                "CI" to "0",
                "CVER" to "1",
                "RID" to "rpc",
                "SID" to currentSid,
                "TYPE" to "xmlhttp",
                "VER" to "8",
                "app" to "tv-html5",
                "device" to "LOUNGE_SCREEN",
                "gsessionid" to currentGsid,
                "id" to store.deviceUuid,
                "loungeIdToken" to loungeToken,
                "mdx-version" to "3",
                "name" to screenName,
                "screen_id" to screenId,
                "theme" to boundTheme,
            )
            val conn = (URI.create("$LOUNGE_API/bc/bind?$pollQuery").toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 65_000
            }
            activePollConnection = conn
            try {
                if (closed || conn.responseCode !in 200..299) break
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    parseBrowserChannelStream(reader) { array ->
                        handleEnvelopeArray(array)
                    }
                }
            } finally {
                if (activePollConnection === conn) activePollConnection = null
                conn.disconnect()
            }
        }
    }

    private fun processChunkText(raw: String) {
        BufferedReader(raw.reader()).use { reader ->
            parseBrowserChannelStream(reader) { array ->
                handleEnvelopeArray(array)
            }
        }
    }

    private fun handleEnvelopeArray(envelope: JSONArray) {
        if (closed) return
        for (i in 0 until envelope.length()) {
            if (closed) return
            val item = envelope.optJSONArray(i) ?: continue
            val aid = item.optInt(0, -1)
            if (aid >= 0) lastAid = aid
            val payload = item.optJSONArray(1) ?: continue
            val cmdName = payload.optString(0)
            when (cmdName) {
                "c" -> sid = payload.optString(1).takeIf { it.isNotBlank() } ?: sid
                "S" -> gsessionId = payload.optString(1).takeIf { it.isNotBlank() } ?: gsessionId
                else -> {
                    val cmdData = payload.optJSONObject(1)
                    Log.d(TAG, "Lounge cmd=$cmdName keys=${cmdData?.keys()?.asSequence()?.toList()}")
                    val decoded = decodeLoungeCommand(cmdName, cmdData, musicScreen = theme == THEME_MUSIC)
                    if (decoded != null && !closed) {
                        scope.launch(mainDispatcher) {
                            if (!closed) onCommand(decoded)
                        }
                    }
                }
            }
        }
    }

    private fun postForm(url: String, body: String): String? {
        val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }
        return try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    internal companion object {
        private const val TAG = "YouTubeLoungeClient"
        private const val LOUNGE_API = "https://www.youtube.com/api/lounge"

        internal fun parseBrowserChannelStream(reader: BufferedReader, onEnvelope: (JSONArray) -> Unit) {
            while (true) {
                val lenLine = reader.readLine() ?: break
                val trimmed = lenLine.trim()
                if (trimmed.isEmpty()) continue
                val expectedChars = trimmed.toIntOrNull()
                if (expectedChars == null || expectedChars <= 0) {
                    if (trimmed.startsWith("[")) {
                        runCatching { JSONArray(trimmed) }.getOrNull()?.let(onEnvelope)
                    }
                    continue
                }
                val buf = CharArray(expectedChars)
                var read = 0
                while (read < expectedChars) {
                    val n = reader.read(buf, read, expectedChars - read)
                    if (n < 0) break
                    read += n
                }
                val jsonText = String(buf, 0, read)
                runCatching { JSONArray(jsonText) }.getOrNull()?.let(onEnvelope)
            }
        }

        internal const val THEME_YOUTUBE = "cl"
        internal const val THEME_MUSIC = "m"
        private const val THEME_SWITCH_TIMEOUT_MS = 5_000L

        internal fun decodeLoungeCommand(name: String, data: JSONObject?, musicScreen: Boolean = false): LoungeCommand? = when (name) {
            "setPlaylist" -> {
                val videoId = data?.optString("videoId")?.takeIf { it.isNotBlank() } ?: return null
                val listId = data.optString("listId").takeIf { it.isNotBlank() }
                val currentTime = data.optString("currentTime").toDoubleOrNull()
                    ?: data.optDouble("currentTime", 0.0).takeIf { !it.isNaN() }
                    ?: 0.0
                val isMusic = musicScreen ||
                    data.optString("theme").equals("music", ignoreCase = true) ||
                    data.optString("audioOnly").equals("true", ignoreCase = true) ||
                    data.optBoolean("audioOnly", false) ||
                    data.optString("clientName").contains("music", ignoreCase = true)
                LoungeCommand.SetPlaylist(videoId, listId, currentTime, isMusic)
            }
            "play" -> LoungeCommand.Play
            "pause" -> LoungeCommand.Pause
            "seekTo" -> {
                val newTime = data?.optString("newTime")?.toDoubleOrNull()
                    ?: data?.optDouble("newTime", 0.0)?.takeIf { !it.isNaN() }
                    ?: return null
                LoungeCommand.SeekTo(newTime)
            }
            "next" -> LoungeCommand.Next
            "previous" -> LoungeCommand.Previous
            "stopVideo" -> LoungeCommand.StopVideo
            "setVolume" -> {
                val vol = data?.optString("volume")?.toIntOrNull()
                    ?: data?.optInt("volume", -1)?.takeIf { it in 0..100 }
                    ?: return null
                val muted = data?.optString("muted").equals("true", ignoreCase = true) ||
                    data?.optBoolean("muted", false) == true
                if (muted) LoungeCommand.Mute else LoungeCommand.SetVolume(vol)
            }
            "mute" -> LoungeCommand.Mute
            "unmute" -> LoungeCommand.Unmute
            "getNowPlaying" -> LoungeCommand.GetNowPlaying
            else -> null
        }

        private fun formEncode(vararg pairs: Pair<String, String>): String =
            pairs.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
    }
}
