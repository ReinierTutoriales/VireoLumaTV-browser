package com.reiniertutoriales.vireolumatv.webengine.webview

import com.reiniertutoriales.vireolumatv.Config.VideoCodecPolicy
import org.junit.Assert.*
import org.junit.Test

class MediaPolicyTest {
    @Test fun automaticModeOnlyBlocksCodecsWithoutHardwareDecoder() {
        assertEquals(listOf("vp9", "av1"), MediaPolicy.blockedCodecs(VideoCodecPolicy.AUTO, { false }, { false }))
        assertEquals(listOf("av1"), MediaPolicy.blockedCodecs(VideoCodecPolicy.AUTO, { true }, { false }))
        assertEquals(emptyList<String>(), MediaPolicy.blockedCodecs(VideoCodecPolicy.AUTO, { true }, { true }))
        assertEquals(listOf("vp9", "av1"), MediaPolicy.blockedCodecs(VideoCodecPolicy.FORCE_H264, { true }, { true }))
        var queried = false
        assertEquals(emptyList<String>(), MediaPolicy.blockedCodecs(VideoCodecPolicy.OFF, { queried = true; false }, { false }))
        assertFalse("Off must not enumerate decoders", queried)
    }

    @Test fun scriptIsOnlyInjectedWhenThePolicyChangesSomething() {
        assertNull(MediaPolicy.configJson(emptyList(), 0))
        assertEquals("{\"block\":[\"vp9\",\"av1\"],\"maxHeight\":0}", MediaPolicy.configJson(listOf("vp9", "av1"), 0))
        assertEquals("{\"block\":[],\"maxHeight\":720}", MediaPolicy.configJson(emptyList(), 720))
    }
}
