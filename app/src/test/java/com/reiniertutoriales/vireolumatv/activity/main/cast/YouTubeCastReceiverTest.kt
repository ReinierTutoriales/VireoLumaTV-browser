package com.reiniertutoriales.vireolumatv.activity.main.cast

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class)
class YouTubeCastReceiverTest {

    private class FakeSession(override val theme: String) : CastSession {
        val sent = ArrayList<LoungeMessage>()
        override fun send(messages: List<LoungeMessage>) {
            sent += messages
        }
    }

    private class FakePlayer : CastPlayer {
        val calls = ArrayList<String>()
        override fun play(video: CastVideo, positionSeconds: Double) {
            calls += "play ${video.id} ${video.theme} $positionSeconds"
        }
        override fun pause() { calls += "pause" }
        override fun resume() { calls += "resume" }
        override fun stopPlayback() { calls += "stop" }
        override fun seek(positionSeconds: Double) { calls += "seek $positionSeconds" }
        override fun setVolume(level: Int, muted: Boolean) { calls += "volume $level $muted" }
    }

    private val youtube = FakeSession("cl")
    private val music = FakeSession("m")
    private val player = FakePlayer()
    private val receiver = YouTubeCastReceiver(player)

    private fun incoming(text: String) = LoungeMessage.parseIncoming(text)

    private fun connectPhone(session: FakeSession, aid: Int = 5) {
        receiver.onDialLaunch(session)
        receiver.onMessages(
            session,
            incoming(
                """[[$aid,["loungeStatus",{"devices":"[{\"app\":\"ytios\",\"name\":\"iPhone\",\"theme\":\"${session.theme}\",\"id\":\"phone-1\",\"type\":\"REMOTE_CONTROL\"}]","connectionEventDetails":"{\"deviceId\":\"phone-1\"}"}]]]""",
            ),
            initial = false,
        )
    }

    @Test
    fun aPhoneJoiningGetsTheStateRepliesTheReferenceSends() {
        connectPhone(music)
        assertEquals(
            listOf("onAutoplayModeChanged", "onHasPreviousNextChanged", "nowPlaying", "onStateChange"),
            music.sent.map { it.name },
        )
        assertTrue(music.sent.all { it.aid == 5 })
        assertTrue(youtube.sent.isEmpty())
    }

    @Test
    fun setPlaylistFromYouTubeMusicPlaysTheTrackOnTheMusicSite() {
        connectPhone(music)
        music.sent.clear()
        receiver.onMessages(
            music,
            incoming(
                """[[6,["setPlaylist",{"listId":"RQ1","videoIds":"a1,b2,c3","videoId":"b2","currentIndex":"1","currentTime":"14"}]]]""",
            ),
            initial = false,
        )
        assertEquals(listOf("stop", "play b2 m 14.0"), player.calls)
        assertEquals(PlayerState.PLAYING, receiver.state.status)
        val nowPlaying = music.sent.last { it.name == "nowPlaying" }
        @Suppress("UNCHECKED_CAST")
        assertEquals("b2", (nowPlaying.payload as Map<String, Any?>)["videoId"])

        receiver.onMessages(music, incoming("""[[7,["next"]]]"""), initial = false)
        assertEquals("play c3 m 0.0", player.calls.last())
        receiver.onMessages(music, incoming("""[[8,["pause"]]]"""), initial = false)
        assertEquals("pause", player.calls.last())
        assertEquals(PlayerState.PAUSED, receiver.state.status)
    }

    @Test
    fun aSetPlaylistWithOnlyAVideoIdStillPlays() {
        // As the lounge forwarded it from a YouTube Music phone on 2026-11-15.
        connectPhone(music)
        receiver.onMessages(
            music,
            incoming("""[[6,["setPlaylist",{"currentTime":"0","sourceDeviceType":"REMOTE_CONTROL","videoId":"dQw4w9WgXcQ"}]]]"""),
            initial = false,
        )
        assertEquals("play dQw4w9WgXcQ m 0.0", player.calls.last())
    }

    @Test
    fun commandsForTheInactiveSessionAreIgnored() {
        connectPhone(music)
        receiver.onMessages(
            youtube,
            incoming("""[[9,["setPlaylist",{"listId":"RQ2","videoIds":"x","videoId":"x","currentIndex":"0"}]]]"""),
            initial = false,
        )
        assertTrue(player.calls.isEmpty())
        receiver.onMessages(youtube, incoming("""[[10,["getNowPlaying"]]]"""), initial = false)
        assertEquals(emptyMap<String, Any?>(), youtube.sent.single().payload)
    }

    @Test
    fun aLaunchFromTheOtherAppMovesTheReceiverToItsSession() {
        connectPhone(music)
        receiver.onDialLaunch(youtube)
        assertEquals("cl", receiver.activeTheme)
        assertEquals("loungeScreenDisconnected", music.sent.last().name)
    }

    @Test
    fun theTrackThePageMovesToIsReportedToThePhone() {
        connectPhone(music)
        receiver.onMessages(
            music,
            incoming("""[[6,["setPlaylist",{"listId":"RQ1","videoIds":"a1,b2","videoId":"a1","currentIndex":"0"}]]]"""),
            initial = false,
        )
        music.sent.clear()
        receiver.onPlayerObserved(1.0, 180.0, paused = false, volumeLevel = 100, muted = false, pageVideoId = "b2")
        @Suppress("UNCHECKED_CAST")
        val nowPlaying = music.sent.first { it.name == "nowPlaying" }.payload as Map<String, Any?>
        assertEquals("b2", nowPlaying["videoId"])
        assertEquals(1, nowPlaying["currentIndex"])
    }
}
