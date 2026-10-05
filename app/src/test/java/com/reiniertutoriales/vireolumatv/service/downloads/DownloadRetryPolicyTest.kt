package com.reiniertutoriales.vireolumatv.service.downloads

import org.junit.Assert.*
import org.junit.Test

class DownloadRetryPolicyTest {
    @Test fun retriesGrowInsteadOfRepeatingAtAFixedRate() {
        assertEquals(listOf(3_000L, 6_000L, 12_000L, 24_000L), (1..4).map { DownloadRetryPolicy.delayMillis(it, null) })
    }
    @Test fun honorsServerSecondsAndDatesAndStopsInsteadOfRetryingTooEarly() {
        assertEquals(30_000L, DownloadRetryPolicy.delayMillis(1, "30"))
        assertEquals(30_000L, DownloadRetryPolicy.delayMillis(1, "Thu, 01 Jan 1970 00:00:30 GMT", 0))
        assertNull(DownloadRetryPolicy.delayMillis(1, "120"))
        assertNull(DownloadRetryPolicy.delayMillis(1, "999999999999999999999999999"))
        assertNull(DownloadRetryPolicy.delayMillis(1, "Thu, 01 Jan 1970 00:02:00 GMT", 0))
        assertEquals(3_000L, DownloadRetryPolicy.delayMillis(1, "malformed"))
    }
}
