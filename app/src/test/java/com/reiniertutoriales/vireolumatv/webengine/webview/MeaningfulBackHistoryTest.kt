package com.reiniertutoriales.vireolumatv.webengine.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeaningfulBackHistoryTest {
    @Test fun reloadsDoNotTrapBackOnSamePage() {
        val urls = listOf("https://site.test/list", "https://site.test/video", "https://site.test/video", "https://site.test/video")
        assertEquals(-3, meaningfulBackOffset(urls, 3))
    }

    @Test fun sameUrlReloadsAloneAreNotAUsefulBackDestination() {
        assertNull(meaningfulBackOffset(listOf("https://site.test/video", "https://site.test/video"), 1))
    }

    @Test fun inPageHashNavigationRemainsAvailable() {
        assertEquals(-1, meaningfulBackOffset(listOf("https://site.test/page#top", "https://site.test/page#video"), 1))
    }

    @Test fun aNormalDistinctPreviousPageStillGoesBackOneStep() {
        assertEquals(-1, meaningfulBackOffset(listOf("https://site.test/list", "https://site.test/video"), 1))
    }

    @Test fun unknownEntriesDoNotCauseUnsupportedSkipping() {
        assertEquals(-1, meaningfulBackOffset(listOf("https://site.test/list", null), 1))
        assertEquals(-1, meaningfulBackOffset(listOf(null, "https://site.test/video", "https://site.test/video"), 2))
    }

    @Test fun emptyOrInvalidHistoryHasNoBackDestination() {
        assertNull(meaningfulBackOffset(emptyList(), 0))
        assertNull(meaningfulBackOffset(listOf("https://site.test/page"), 0))
        assertNull(meaningfulBackOffset(listOf("https://site.test/page"), 2))
    }
}
