package com.reiniertutoriales.vireolumatv.utils

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class OfflineGraceTest {
    private val states = mutableListOf<Boolean>()
    private val grace = OfflineGrace(Handler(Looper.getMainLooper()), 3_000) { states += it }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun aBriefLossNeverReachesThePage() {
        grace.report(false)
        advance(1_000)
        grace.report(true)
        advance(5_000)
        assertEquals(listOf(true), states)
    }

    @Test fun aLastingLossIsReportedAfterTheGraceAndRecoveryAtOnce() {
        grace.report(false)
        advance(2_900)
        assertEquals(emptyList<Boolean>(), states)
        advance(200)
        assertEquals(listOf(false), states)
        grace.report(true)
        assertEquals(listOf(false, true), states)
    }
}
