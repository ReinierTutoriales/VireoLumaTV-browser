package com.reiniertutoriales.vireolumatv.webengine.webview

import org.junit.Assert.*
import org.junit.Test

class MediaPolicyTest {
    @Test fun onlyCodecsWithoutHardwareDecoderAreBlocked() {
        assertEquals(listOf("vp9", "av1"), MediaPolicy.blockedCodecs(hasVp9 = false, hasAv1 = false))
        assertEquals(listOf("av1"), MediaPolicy.blockedCodecs(hasVp9 = true, hasAv1 = false))
        assertEquals(emptyList<String>(), MediaPolicy.blockedCodecs(hasVp9 = true, hasAv1 = true))
    }

    @Test fun scriptIsOnlyInjectedWhenThePolicyChangesSomething() {
        assertNull(MediaPolicy.configJson(emptyList(), 0))
        assertEquals("{\"block\":[\"vp9\",\"av1\"],\"maxHeight\":0}", MediaPolicy.configJson(listOf("vp9", "av1"), 0))
        assertEquals("{\"block\":[],\"maxHeight\":720}", MediaPolicy.configJson(emptyList(), 720))
    }
}
