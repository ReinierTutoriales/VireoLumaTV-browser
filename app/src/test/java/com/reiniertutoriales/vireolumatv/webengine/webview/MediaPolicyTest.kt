package com.reiniertutoriales.vireolumatv.webengine.webview

import org.junit.Assert.*
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPolicyTest {
    @Test fun onlyCodecsWithoutHardwareDecoderAreBlocked() {
        assertEquals(listOf("vp9", "av1"), MediaPolicy.blockedCodecs(hasVp8 = true, hasVp9 = false, hasAv1 = false))
        assertEquals(listOf("av1"), MediaPolicy.blockedCodecs(hasVp8 = true, hasVp9 = true, hasAv1 = false))
        assertEquals(emptyList<String>(), MediaPolicy.blockedCodecs(hasVp8 = true, hasVp9 = true, hasAv1 = true))
    }

    @Test fun vp8AndVp9CapabilitiesAreIndependent() {
        assertEquals(listOf("vp9"), MediaPolicy.blockedCodecs(hasVp8 = true, hasVp9 = false, hasAv1 = true))
        assertEquals(listOf("vp8"), MediaPolicy.blockedCodecs(hasVp8 = false, hasVp9 = true, hasAv1 = true))
        assertEquals(listOf("vp8", "vp9", "av1"), MediaPolicy.blockedCodecs(false, false, false))
    }

    @Test fun scriptIsOnlyInjectedWhenThePolicyChangesSomething() {
        assertNull(MediaPolicy.configJson(emptyList(), 0))
        assertEquals("{\"block\":[\"vp9\",\"av1\"],\"maxHeight\":0}", MediaPolicy.configJson(listOf("vp9", "av1"), 0))
        assertEquals("{\"block\":[],\"maxHeight\":720}", MediaPolicy.configJson(emptyList(), 720))
    }

    /** A decoder that sustains up to [h30] at 30 fps and [h60] at 60 fps. */
    private fun decoder(h30: Int, h60: Int, tenBit: Boolean = false) = object : HardwareVideo.Probe {
        override val tenBit = tenBit
        override fun decodes(width: Int, height: Int, fps: Int) = height <= (if (fps > 30) h60 else h30) &&
            width <= HardwareVideo.widthFor(if (fps > 30) h60 else h30)
    }

    @Test fun theBestDecoderSetsEachLimitAndTenBitComesFromAnyOfThem() {
        val video = HardwareVideo.of(listOf(decoder(1080, 720), decoder(2160, 1080, tenBit = true)))
        assertEquals(HardwareVideo(2160, 1080, true), video)
        assertEquals(HardwareVideo(1080, 0, false), HardwareVideo.of(listOf(decoder(1080, 240))))
        assertNull("No hardware decoder, no entry", HardwareVideo.of(emptyList()))
        assertEquals(3840, HardwareVideo.widthFor(2160))
        assertEquals(1920, HardwareVideo.widthFor(1080))
        assertEquals(854, HardwareVideo.widthFor(480))
    }

    @Test fun hardwareLimitsTravelToThePageUnlessTheUserLiftsThem() {
        val hw = mapOf("vp9" to HardwareVideo(2160, 1080, true))
        assertEquals("{\"block\":[\"av1\"],\"maxHeight\":0,\"hw\":{\"vp9\":{\"h30\":2160,\"h60\":1080,\"ten\":true}}}",
            MediaPolicy.configJson(listOf("av1"), 0, hw))
        assertNull("No limit and no missing decoder: nothing to inject", MediaPolicy.configJson(emptyList(), MediaPolicy.NO_LIMIT, emptyMap()))
        assertTrue(MediaPolicy.hardwareLimits(MediaPolicy.NO_LIMIT).isEmpty())
    }
}
