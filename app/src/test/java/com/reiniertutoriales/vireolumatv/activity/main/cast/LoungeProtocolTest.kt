package com.reiniertutoriales.vireolumatv.activity.main.cast

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class)
class LoungeProtocolTest {

    /** A screen bind response as YouTube returned it on 2026-11-15. */
    private val bindResponse = """594
[[0,["c","9E2668AE12D306DC","",8]]
,[1,["S","mka8mzNoRD_K3I5wmitoXPqt1kaB0LhvIPEn3-p23Iw"]]
,[2,["loungeStatus",{"queueId":"RQk6F","devices":"[{\"app\":\"ytios\",\"name\":\"iPhone\",\"theme\":\"m\",\"id\":\"phone-1\",\"type\":\"REMOTE_CONTROL\"}]"}]]
,[3,["getNowPlaying"]]
]
"""

    @Test
    fun parsesSessionIdsStatusAndCommandsFromABindResponse() {
        val messages = LoungeMessage.parseIncoming(bindResponse)
        assertEquals(listOf("c", "S", "loungeStatus", "getNowPlaying"), messages.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), messages.map { it.aid })
        assertEquals("9E2668AE12D306DC", (messages[0].payload as JSONArray).getString(0))
        assertEquals("mka8mzNoRD_K3I5wmitoXPqt1kaB0LhvIPEn3-p23Iw", messages[1].payload)
        assertTrue(messages[2].payloadObject!!.getString("devices").contains("REMOTE_CONTROL"))
        assertNull(messages[3].payload)

        val params = LoungeBindParams("dev", "VireoLumaTV", "vireolumatv", "m", "VireoLumaTV", "Android TV")
        params.loungeIdToken = "token"
        messages.forEach { params.updateWith(it) }
        assertEquals("9E2668AE12D306DC", params.sid)
        assertEquals("mka8mzNoRD_K3I5wmitoXPqt1kaB0LhvIPEn3-p23Iw", params.gsessionId)
        assertTrue(params.canPoll())
    }

    @Test
    fun bindQueriesCarryTheReferenceScreenParameters() {
        val params = LoungeBindParams("dev-1", "VireoLumaTV", "vireolumatv", "m", "VireoLumaTV", "Android TV")
        params.loungeIdToken = "token-1"
        val rid = params.rid
        val init = decode(params.initSessionQuery())
        assertEquals("LOUNGE_SCREEN", init["device"])
        assertEquals("m", init["theme"])
        assertEquals("dsp,mic,dpa,ntb", init["capabilities"])
        assertEquals("m", init["cst"])
        assertEquals("2", init["mdxVersion"])
        assertEquals("8", init["VER"])
        assertEquals("2", init["v"])
        assertEquals("1", init["CVER"])
        assertEquals("token-1", init["loungeIdToken"])
        assertEquals(rid.toString(), init["RID"])
        assertEquals("TVHTML5", JSONObject(init.getValue("deviceInfo")).getString("clientName"))
        assertEquals(rid + 1, params.rid)

        params.sid = "SID"
        params.gsessionId = "GS"
        val rpc = decode(params.rpcQuery())
        assertEquals("rpc", rpc["RID"])
        assertEquals("xmlhttp", rpc["TYPE"])
        assertEquals("0", rpc["CI"])
        assertEquals("3", rpc["AID"])
        assertFalse(rpc.containsKey("deviceInfo"))

        // A reply keeps the incoming AID; an unsolicited message takes the next one.
        assertEquals("7", decode(params.sendMessageQuery(7))["AID"])
        assertEquals("7", decode(params.sendMessageQuery(null))["AID"])
        assertEquals("8", decode(params.sendMessageQuery(null))["AID"])
    }

    @Test
    fun outgoingMessagesUseTheReferencePayloads() {
        val state = PlayerState(
            status = PlayerState.PLAYING,
            position = 12.5,
            duration = 200.0,
            cpn = "cpn",
            current = CastVideo("vid", "m", playlistId = "RQ1", index = 2),
            volumeLevel = 80,
            muted = false,
        )
        @Suppress("UNCHECKED_CAST")
        val nowPlaying = LoungeMessage.nowPlaying(4, state).payload as Map<String, Any?>
        assertEquals("vid", nowPlaying["videoId"])
        assertEquals("RQ1", nowPlaying["listId"])
        assertEquals(2, nowPlaying["currentIndex"])
        assertEquals(200.0, nowPlaying["loadedTime"])
        assertEquals(emptyMap<String, Any?>(), LoungeMessage.nowPlaying(4, null).payload)
        assertEquals("12", LoungeSession.formatValue(12.0))
        assertEquals("12.5", LoungeSession.formatValue(12.5))
    }

    @Test
    fun watchUrlsUseTheSendersSiteAndSkipLoungeQueueIds() {
        assertEquals(
            "https://music.youtube.com/watch?v=abc123&t=30s",
            YouTubeCastController.buildWatchUrl("abc123", "RQk6F", 30.0, isMusic = true),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=xyz789&list=PL1",
            YouTubeCastController.buildWatchUrl("xyz789", "PL1", 0.0, isMusic = false),
        )
    }

    @Test
    fun aCastRetriesSoundUntilThePagePlaysUnmuted() {
        // Muted or paused after a cast: unmute and spend a try.
        assertEquals(4 to true, YouTubeCastController.soundStep(5, muted = true, paused = false))
        assertEquals(3 to true, YouTubeCastController.soundStep(4, muted = false, paused = true))
        // Playing with sound: done.
        assertEquals(0 to false, YouTubeCastController.soundStep(3, muted = false, paused = false))
        // No tries left, or the phone paused or muted: leave the page alone.
        assertEquals(0 to false, YouTubeCastController.soundStep(0, muted = true, paused = true))
    }

    private fun decode(query: String): Map<String, String> = query.split('&').associate {
        val (k, v) = it.split('=', limit = 2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }
}
