package com.reiniertutoriales.vireolumatv.activity.main.cast

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.cast.YouTubeLoungeClient.LoungeCommand
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class)
class YouTubeLoungeClientTest {

    @Test
    fun parsesLengthPrefixedBrowserChannelFrames() {
        val frame1 = """[[0,["c","sid-1"]],[1,["S","gsid-1"]]]"""
        val frame2 = """[[2,["setPlaylist",{"videoId":"abc123","listId":"PL99","currentTime":"42.5","theme":"music"}]]]"""
        val stream = "${frame1.length}\n$frame1\n${frame2.length}\n$frame2\n"

        val envelopes = mutableListOf<JSONArray>()
        YouTubeLoungeClient.parseBrowserChannelStream(BufferedReader(StringReader(stream))) {
            envelopes.add(it)
        }

        assertEquals(2, envelopes.size)
        val cmdPayload = envelopes[1].getJSONArray(0).getJSONArray(1)
        val cmd = YouTubeLoungeClient.decodeLoungeCommand(cmdPayload.getString(0), cmdPayload.getJSONObject(1))
        assertTrue(cmd is LoungeCommand.SetPlaylist)
        val playlist = cmd as LoungeCommand.SetPlaylist
        assertEquals("abc123", playlist.videoId)
        assertEquals("PL99", playlist.listId)
        assertEquals(42.5, playlist.currentTimeSeconds, 0.01)
        assertTrue(playlist.isMusic)
    }

    @Test
    fun decodesPlaybackControlCommands() {
        assertEquals(LoungeCommand.Play, YouTubeLoungeClient.decodeLoungeCommand("play", null))
        assertEquals(LoungeCommand.Pause, YouTubeLoungeClient.decodeLoungeCommand("pause", null))
        assertEquals(LoungeCommand.Next, YouTubeLoungeClient.decodeLoungeCommand("next", null))
        assertEquals(LoungeCommand.Previous, YouTubeLoungeClient.decodeLoungeCommand("previous", null))
        assertEquals(LoungeCommand.StopVideo, YouTubeLoungeClient.decodeLoungeCommand("stopVideo", null))
        assertEquals(
            LoungeCommand.SeekTo(95.0),
            YouTubeLoungeClient.decodeLoungeCommand("seekTo", JSONObject("""{"newTime":"95.0"}""")),
        )
        assertEquals(
            LoungeCommand.SetVolume(65),
            YouTubeLoungeClient.decodeLoungeCommand("setVolume", JSONObject("""{"volume":"65","muted":"false"}""")),
        )
        assertEquals(
            LoungeCommand.Mute,
            YouTubeLoungeClient.decodeLoungeCommand("setVolume", JSONObject("""{"volume":"65","muted":"true"}""")),
        )
        val videoCmd = YouTubeLoungeClient.decodeLoungeCommand(
            "setPlaylist",
            JSONObject("""{"videoId":"xyz","audioOnly":false}"""),
        ) as LoungeCommand.SetPlaylist
        assertFalse(videoCmd.isMusic)
    }

    @Test
    fun buildsYouTubeAndYouTubeMusicWatchUrls() {
        assertEquals(
            "https://music.youtube.com/watch?v=abc123&list=PL1&t=30s",
            YouTubeCastController.buildWatchUrl("abc123", "PL1", 30.0, isMusic = true),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=xyz789",
            YouTubeCastController.buildWatchUrl("xyz789", null, 0.0, isMusic = false),
        )
    }
}
