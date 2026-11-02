package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class FrameStatsTest {
    @Test fun theSummaryShowsTheShareOfSlowFramesInTheGivenLanguage() {
        assertNull("Nothing measured yet", FrameStats.summary(Locale.US))
        repeat(998) { FrameStats.record(isJank = false) }
        repeat(2) { FrameStats.record(isJank = true) }
        assertEquals("0.2% · 2/1000", FrameStats.summary(Locale.US))
        assertEquals("Spanish uses a decimal comma", true, FrameStats.summary(Locale.forLanguageTag("es"))!!.startsWith("0,2"))
    }
}
