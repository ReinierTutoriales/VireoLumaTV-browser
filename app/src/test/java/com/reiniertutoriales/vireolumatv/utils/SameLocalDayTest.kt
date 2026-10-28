package com.reiniertutoriales.vireolumatv.utils

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/** Day headers depend on this: a row starts a new section when its local day changes. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class SameLocalDayTest {
    private lateinit var previous: TimeZone

    @Before
    fun fixZone() {
        previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(previous)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, hour, minute) }.timeInMillis

    @Test
    fun sameLocalDayIsSameDateRegardlessOfTime() {
        assertTrue(sameLocalDay(at(2026, 5, 10, 0, 1), at(2026, 5, 10, 23, 59)))
    }

    @Test
    fun midnightStartsANewDay() {
        assertFalse(sameLocalDay(at(2026, 5, 10, 23, 59), at(2026, 5, 11, 0, 0)))
    }

    @Test
    fun sameDayNumberInAnotherYearIsNotTheSameDate() {
        // Feb 1 is day 32 of both years: comparing day-of-year alone would wrongly match them.
        assertFalse(sameLocalDay(at(2024, 2, 1, 12), at(2025, 2, 1, 12)))
    }

    @Test
    fun missingDateSentinelNeverMatches() {
        assertFalse(sameLocalDay(at(2026, 5, 10, 12, 0), -1L))
    }
}
