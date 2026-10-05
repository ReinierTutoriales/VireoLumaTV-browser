package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StreamDiagnosticsTest {
    @Test fun mediaKindUsesPathNotTokensAndDoesNotTreatPagesAsStreams() {
        assertEquals("hls-playlist", StreamDiagnostics.kind(Uri.parse("https://cdn.test/live.M3U8?token=secret")))
        assertEquals("dash-manifest", StreamDiagnostics.kind(Uri.parse("https://cdn.test/live.mpd")))
        assertEquals("media", StreamDiagnostics.kind(Uri.parse("https://cdn.test/segment.m4s")))
        assertNull(StreamDiagnostics.kind(Uri.parse("https://page.test/watch?url=live.m3u8")))
    }
    @Test fun errorBurstsHaveFixedBudgetWithoutEventHistory() {
        var now = 0L
        val budget = StreamLogBudget { now }
        repeat(8) { assertTrue(budget.allow()) }
        repeat(1000) { assertFalse(budget.allow()) }
        now = 9999
        assertFalse(budget.allow())
        now = 10000
        assertTrue(budget.allow())
        now = 1 // Also tolerates clock replacement/reset in tests.
        assertTrue(budget.allow())
    }
}
