package com.reiniertutoriales.vireolumatv.activity.main.cast

import android.webkit.WebView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.cast.YouTubeLoungeClient.LoungeCommand
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.util.UUID

/**
 * Coordinates local DIAL discovery, YouTube Lounge remote commands, and WebView playback on
 * `youtube.com` / `music.youtube.com` while the app is open in the foreground.
 */
internal class YouTubeCastController(
    private val activity: MainActivity,
    private val config: Config,
    private val scope: CoroutineScope,
) {
    private var dialServer: CastDialServer? = null
    private var loungeClient: YouTubeLoungeClient? = null
    private var syncJob: Job? = null
    private var activeCastTab: BrowserTab? = null
    private var lastVideoId: String? = null
    private var lastListId: String? = null
    private var lastCurrentTimeSeconds: Double = 0.0
    private var lastStateCode: Int = 2

    private val sessionStore = object : YouTubeLoungeClient.SessionStore {
        override val deviceUuid: String
            get() = config.youtubeCastUuid.ifBlank {
                UUID.randomUUID().toString().also { config.youtubeCastUuid = it }
            }
        override var screenId: String
            get() = config.youtubeCastScreenId
            set(value) { config.youtubeCastScreenId = value }
        override var loungeToken: String
            get() = config.youtubeCastLoungeToken
            set(value) { config.youtubeCastLoungeToken = value }
        override var loungeTokenExpiryMs: Long
            get() = config.youtubeCastLoungeTokenExpiryMs
            set(value) { config.youtubeCastLoungeTokenExpiryMs = value }
    }

    fun start() {
        if (dialServer != null) return
        val lounge = YouTubeLoungeClient(
            scope = scope,
            store = sessionStore,
        ) { cmd -> handleCommand(cmd) }.also { it.start() }

        val dial = CastDialServer(
            context = activity,
            scope = scope,
            deviceUuid = sessionStore.deviceUuid,
            screenIdProvider = { lounge.screenId },
            onDialLaunch = { launch ->
                runBlocking {
                    withTimeoutOrNull(4_000L) {
                        lounge.prepareForDialLaunch(launch.pairingCode, launch.isMusic)
                    }
                }
                launch.videoId?.let { videoId ->
                    val url = buildWatchUrl(videoId, launch.listId, launch.startSeconds, launch.isMusic)
                    scope.launch { openInYouTubeTab(url, launch.isMusic) }
                }
            },
            onDialStop = {
                activeCastTab?.let { tab -> evaluatePlayerJs(tab, "v.pause()") }
                stopSyncLoop()
            },
        )
        if (!dial.start()) {
            dial.close()
            lounge.close()
            return
        }
        loungeClient = lounge
        dialServer = dial
    }

    fun stop() {
        stopSyncLoop()
        activeCastTab = null
        dialServer?.close()
        dialServer = null
        loungeClient?.close()
        loungeClient = null
    }

    private fun handleCommand(cmd: LoungeCommand) {
        when (cmd) {
            is LoungeCommand.SetPlaylist -> {
                lastVideoId = cmd.videoId
                lastListId = cmd.listId
                lastCurrentTimeSeconds = cmd.currentTimeSeconds
                lastStateCode = 3
                loungeClient?.sendNowPlaying(cmd.videoId, cmd.listId, cmd.currentTimeSeconds, stateCode = 3)
                val url = buildWatchUrl(cmd.videoId, cmd.listId, cmd.currentTimeSeconds, cmd.isMusic)
                openInYouTubeTab(url, cmd.isMusic)
            }
            LoungeCommand.GetNowPlaying -> {
                loungeClient?.sendNowPlaying(lastVideoId, lastListId, lastCurrentTimeSeconds, lastStateCode)
            }
            LoungeCommand.Play -> targetTab()?.let { evaluatePlayerJs(it, "v.play().catch(()=>{})") }
            LoungeCommand.Pause -> targetTab()?.let { evaluatePlayerJs(it, "v.pause()") }
            is LoungeCommand.SeekTo -> targetTab()?.let {
                evaluatePlayerJs(it, "v.currentTime=${cmd.newTimeSeconds}")
            }
            LoungeCommand.Next -> targetTab()?.let { tab ->
                evaluateRawJs(
                    tab,
                    "(document.querySelector('ytmusic-player-bar .next-button, .ytp-next-button'))?.click()",
                )
            }
            LoungeCommand.Previous -> targetTab()?.let { tab ->
                evaluateRawJs(
                    tab,
                    "(document.querySelector('ytmusic-player-bar .previous-button, .ytp-prev-button'))?.click()",
                )
            }
            LoungeCommand.StopVideo -> {
                targetTab()?.let { evaluatePlayerJs(it, "v.pause()") }
                stopSyncLoop()
            }
            is LoungeCommand.SetVolume -> targetTab()?.let {
                val level = (cmd.volume.coerceIn(0, 100) / 100.0)
                evaluatePlayerJs(it, "v.muted=false;v.volume=$level")
            }
            LoungeCommand.Mute -> targetTab()?.let { evaluatePlayerJs(it, "v.muted=true") }
            LoungeCommand.Unmute -> targetTab()?.let { evaluatePlayerJs(it, "v.muted=false") }
        }
    }

    private fun openInYouTubeTab(url: String, isMusic: Boolean) {
        val existing = activity.tabStore.openTabs.firstOrNull { tab ->
            !tab.closed && runCatching {
                val host = tab.url.toUri().host ?: return@runCatching false
                if (isMusic) host.equals(YOUTUBE_MUSIC_HOST, ignoreCase = true)
                else host.equals(YOUTUBE_HOST, ignoreCase = true) || host.equals("youtube.com", ignoreCase = true)
            }.getOrDefault(false)
        }
        val tab = if (existing != null) {
            if (activity.tabStore.currentTab.value !== existing) {
                activity.changeTab(existing, loadInitialUrl = false)
            }
            activity.navigate(url)
            if (activity.vb.controlsOverlay.isVisible) activity.closeCapsule()
            existing
        } else {
            activity.openInNewTab(url, activity.tabStore.openTabs.size, closeCapsuleAfter = true, navigateImmediately = true)
            activity.tabStore.currentTab.value
        }
        if (tab != null) {
            activeCastTab = tab
            startSyncLoop(tab)
        }
    }

    private fun targetTab(): BrowserTab? =
        activeCastTab?.takeIf { !it.closed } ?: activity.tabStore.currentTab.value?.takeIf { !it.closed }

    private fun evaluatePlayerJs(tab: BrowserTab, statement: String) {
        evaluateRawJs(tab, "(()=>{const v=document.querySelector('video');if(v){$statement}})()")
    }

    private fun evaluateRawJs(tab: BrowserTab, script: String) {
        (tab.engine.view as? WebView)?.evaluateJavascript(script, null)
    }

    private fun startSyncLoop(tab: BrowserTab) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && !tab.closed && activeCastTab === tab) {
                delay(2_000L)
                val webView = tab.engine.view as? WebView ?: continue
                webView.evaluateJavascript(
                    "(()=>{const v=document.querySelector('video');if(!v)return null;" +
                        "return JSON.stringify({t:v.currentTime||0,d:v.duration||0,p:v.paused,vol:Math.round((v.volume||1)*100),m:!!v.muted})})()",
                ) { json ->
                    if (json == null || json == "null") return@evaluateJavascript
                    val raw = runCatching {
                        val decoded = JSONTokener(json).nextValue()
                        if (decoded is String) JSONObject(decoded) else decoded as? JSONObject
                    }.getOrNull() ?: return@evaluateJavascript
                    val stateCode = if (raw.optBoolean("p", false)) 2 else 1
                    val currentTime = raw.optDouble("t", 0.0)
                    lastCurrentTimeSeconds = currentTime
                    lastStateCode = stateCode
                    loungeClient?.sendPlaybackState(
                        YouTubeLoungeClient.PlaybackState(
                            currentTimeSeconds = currentTime,
                            durationSeconds = raw.optDouble("d", 0.0),
                            stateCode = stateCode,
                            volume = raw.optInt("vol", 100),
                            muted = raw.optBoolean("m", false),
                        ),
                    )
                }
            }
        }
    }

    private fun stopSyncLoop() {
        syncJob?.cancel()
        syncJob = null
    }

    internal companion object {
        private const val YOUTUBE_HOST = "www.youtube.com"
        private const val YOUTUBE_MUSIC_HOST = "music.youtube.com"

        internal fun buildWatchUrl(
            videoId: String,
            listId: String?,
            startSeconds: Double?,
            isMusic: Boolean,
        ): String {
            val host = if (isMusic) YOUTUBE_MUSIC_HOST else YOUTUBE_HOST
            val params = ArrayList<String>(3)
            params.add("v=${URLEncoder.encode(videoId, "UTF-8")}")
            if (!listId.isNullOrBlank()) params.add("list=${URLEncoder.encode(listId, "UTF-8")}")
            if (startSeconds != null && startSeconds >= 1.0) params.add("t=${startSeconds.toInt()}s")
            return "https://$host/watch?${params.joinToString("&")}"
        }
    }
}
