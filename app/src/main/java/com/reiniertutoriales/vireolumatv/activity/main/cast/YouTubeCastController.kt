package com.reiniertutoriales.vireolumatv.activity.main.cast

import android.webkit.WebView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.util.UUID

/**
 * Android side of the YouTube / YouTube Music cast receiver: DIAL discovery, one lounge session per
 * sender app, and the WebView tab that plays what is cast. Works while the app is in the foreground.
 */
internal class YouTubeCastController(
    private val activity: MainActivity,
    private val config: Config,
    private val scope: CoroutineScope,
) : CastPlayer {
    private var dialServer: CastDialServer? = null
    private var sessions: Map<String, LoungeSession> = emptyMap()
    private var receiver: YouTubeCastReceiver? = null
    private var observeJob: Job? = null
    private var activeCastTab: BrowserTab? = null
    /** Tries left to start the cast with sound; zero once it plays unmuted or the phone pauses or mutes. */
    private var soundTries = 0

    private val deviceUuid: String
        get() = config.youtubeCastUuid.ifBlank {
            UUID.randomUUID().toString().also { config.youtubeCastUuid = it }
        }

    fun start() {
        if (dialServer != null) return
        val receiver = YouTubeCastReceiver(this)
        val onMessages: (LoungeSession, List<LoungeMessage>, Boolean) -> Unit = { session, messages, initial ->
            receiver.onMessages(session, messages, initial)
        }
        val youtube = LoungeSession(
            theme = YouTubeCastReceiver.THEME_YOUTUBE,
            store = object : LoungeSession.ScreenStore {
                override var screenId: String
                    get() = config.youtubeCastScreenId
                    set(value) { config.youtubeCastScreenId = value }
            },
            deviceId = deviceUuid,
            screenName = SCREEN_NAME,
            scope = scope,
            onMessages = onMessages,
        )
        val music = LoungeSession(
            theme = YouTubeCastReceiver.THEME_MUSIC,
            store = object : LoungeSession.ScreenStore {
                override var screenId: String
                    get() = config.youtubeMusicCastScreenId
                    set(value) { config.youtubeMusicCastScreenId = value }
            },
            deviceId = deviceUuid,
            screenName = SCREEN_NAME,
            scope = scope,
            onMessages = onMessages,
        )
        val bySessionTheme = mapOf(youtube.theme to youtube, music.theme to music)

        val dial = CastDialServer(
            context = activity,
            scope = scope,
            deviceUuid = deviceUuid,
            friendlyName = SCREEN_NAME,
            onDialLaunch = { launch ->
                // Called on a DIAL thread; the reply waits for the pairing code, as in the reference.
                val session = launch.theme?.let { bySessionTheme[it] }
                val code = launch.pairingCode
                if (session == null || code == null) {
                    false
                } else {
                    runBlocking {
                        withContext(Dispatchers.Main) { receiver.onDialLaunch(session) }
                        withTimeoutOrNull(PAIRING_TIMEOUT_MS) { session.registerPairingCode(code) } == true
                    }
                }
            },
        )
        if (!dial.start()) {
            dial.close()
            return
        }
        youtube.start()
        music.start()
        this.receiver = receiver
        sessions = bySessionTheme
        dialServer = dial
    }

    fun stop() {
        stopObserving()
        activeCastTab = null
        dialServer?.close()
        dialServer = null
        sessions.values.forEach { it.close() }
        sessions = emptyMap()
        receiver = null
    }

    override fun play(video: CastVideo, positionSeconds: Double) {
        val isMusic = video.theme == YouTubeCastReceiver.THEME_MUSIC
        soundTries = SOUND_TRIES
        openInYouTubeTab(buildWatchUrl(video.id, video.playlistId, positionSeconds, isMusic), isMusic)
    }

    override fun pause() {
        soundTries = 0
        targetTab()?.let { evaluatePlayerJs(it, "v.pause()") }
    }

    override fun resume() {
        targetTab()?.let { evaluatePlayerJs(it, "v.play().catch(()=>{})") }
    }

    override fun stopPlayback() {
        soundTries = 0
        targetTab()?.let { evaluatePlayerJs(it, "v.pause()") }
    }

    override fun seek(positionSeconds: Double) {
        targetTab()?.let { evaluatePlayerJs(it, "v.currentTime=$positionSeconds") }
    }

    override fun setVolume(level: Int, muted: Boolean) {
        if (muted) soundTries = 0
        val volume = level.coerceIn(0, 100) / 100.0
        targetTab()?.let { evaluatePlayerJs(it, "v.volume=$volume;v.muted=$muted") }
    }

    private fun openInYouTubeTab(url: String, isMusic: Boolean) {
        val tabs = activity.tabStore.openTabs.filter { !it.closed }
        val existing = pickCastTab(
            hosts = tabs.map { tab -> runCatching { tab.url.toUri().host }.getOrNull() },
            castIndex = tabs.indexOfFirst { it === activeCastTab },
            currentIndex = tabs.indexOfFirst { it === activity.tabStore.currentTab.value },
            isMusic = isMusic,
        )?.let { tabs[it] }
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
            // The phone asked for this playback, so this tab may start it with sound even when
            // "Let pages play video and sound by themselves" is off; other tabs keep that setting.
            (tab.engine.view as? WebView)?.settings?.mediaPlaybackRequiresUserGesture = false
            activeCastTab = tab
            startObserving(tab)
        }
    }

    private fun targetTab(): BrowserTab? =
        activeCastTab?.takeIf { !it.closed } ?: activity.tabStore.currentTab.value?.takeIf { !it.closed }

    private fun evaluatePlayerJs(tab: BrowserTab, statement: String) {
        (tab.engine.view as? WebView)?.evaluateJavascript(
            "(()=>{const v=document.querySelector('video');if(v){$statement}})()",
            null,
        )
    }

    /** Reads the page's video element so senders see the real position, pause state and track. */
    private fun startObserving(tab: BrowserTab) {
        observeJob?.cancel()
        observeJob = scope.launch {
            while (isActive && !tab.closed && activeCastTab === tab) {
                delay((YouTubeCastReceiver.OBSERVE_INTERVAL_S * 1000).toLong())
                val webView = tab.engine.view as? WebView ?: continue
                webView.evaluateJavascript(OBSERVE_SCRIPT) { json ->
                    val raw = decodeJson(json) ?: return@evaluateJavascript
                    val (tries, unmute) = soundStep(soundTries, raw.optBoolean("m", false), raw.optBoolean("p", false))
                    soundTries = tries
                    if (unmute) webView.evaluateJavascript(UNMUTE_SCRIPT, null)
                    receiver?.onPlayerObserved(
                        positionSeconds = raw.optDouble("t", 0.0),
                        durationSeconds = raw.optDouble("d", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                        paused = raw.optBoolean("p", false),
                        volumeLevel = raw.optInt("vol", 100),
                        muted = raw.optBoolean("m", false),
                        pageVideoId = raw.optString("id").takeIf { it.isNotEmpty() },
                    )
                }
            }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    internal companion object {
        private const val SCREEN_NAME = "VireoLumaTV"
        private const val YOUTUBE_HOST = "www.youtube.com"
        private const val YOUTUBE_MUSIC_HOST = "music.youtube.com"
        private const val PAIRING_TIMEOUT_MS = 4_000L
        private const val SOUND_TRIES = 5
        private const val OBSERVE_SCRIPT =
            "(()=>{const v=document.querySelector('video');if(!v)return null;" +
                "const id=new URL(location.href).searchParams.get('v')||'';" +
                "return JSON.stringify({t:v.currentTime||0,d:v.duration||0,p:v.paused," +
                "vol:Math.round((v.volume||1)*100),m:!!v.muted,id:id})})()"

        /**
         * YouTube starts muted or paused when it last could not autoplay with sound: retry the
         * unmute a few times after a cast, and stop as soon as it plays with sound.
         */
        internal fun soundStep(tries: Int, muted: Boolean, paused: Boolean): Pair<Int, Boolean> = when {
            tries <= 0 -> 0 to false
            muted || paused -> (tries - 1) to true
            else -> 0 to false
        }

        private val YOUTUBE_HOSTS = setOf("youtube.com", YOUTUBE_HOST, "m.youtube.com", YOUTUBE_MUSIC_HOST)

        /**
         * Where a cast plays, so every cast reuses one tab: the tab of the last cast, then the
         * current tab if it is on YouTube, then a tab on the sender's site, then any YouTube tab.
         * Null opens a new tab. Android serves youtube.com as m.youtube.com, so that counts too.
         */
        internal fun pickCastTab(hosts: List<String?>, castIndex: Int, currentIndex: Int, isMusic: Boolean): Int? {
            fun isYouTube(i: Int) = hosts.getOrNull(i)?.lowercase() in YOUTUBE_HOSTS
            fun isSendersSite(i: Int) = (hosts.getOrNull(i)?.lowercase() == YOUTUBE_MUSIC_HOST) == isMusic
            return castIndex.takeIf { it in hosts.indices }
                ?: currentIndex.takeIf { it in hosts.indices && isYouTube(it) }
                ?: hosts.indices.firstOrNull { isYouTube(it) && isSendersSite(it) }
                ?: hosts.indices.firstOrNull { isYouTube(it) }
        }

        /** The site's own player API, so its mute button agrees; the bare element otherwise. */
        private const val UNMUTE_SCRIPT =
            "(()=>{const p=document.getElementById('movie_player');" +
                "if(p&&p.unMute){p.unMute();if(p.playVideo)p.playVideo();return}" +
                "const v=document.querySelector('video');if(v){v.muted=false;v.play().catch(()=>{})}})()"

        private fun decodeJson(json: String?): JSONObject? {
            if (json == null || json == "null") return null
            return runCatching {
                val decoded = JSONTokener(json).nextValue()
                if (decoded is String) JSONObject(decoded) else decoded as? JSONObject
            }.getOrNull()
        }

        /** Lounge queue ids ("RQ…") are not playlists the site can open, so only real ones go in the URL. */
        internal fun buildWatchUrl(
            videoId: String,
            listId: String?,
            startSeconds: Double?,
            isMusic: Boolean,
        ): String {
            val host = if (isMusic) YOUTUBE_MUSIC_HOST else YOUTUBE_HOST
            val params = ArrayList<String>(3)
            params.add("v=${URLEncoder.encode(videoId, "UTF-8")}")
            if (!listId.isNullOrBlank() && !listId.startsWith("RQ")) {
                params.add("list=${URLEncoder.encode(listId, "UTF-8")}")
            }
            if (startSeconds != null && startSeconds >= 1.0) params.add("t=${startSeconds.toInt()}s")
            return "https://$host/watch?${params.joinToString("&")}"
        }
    }
}
