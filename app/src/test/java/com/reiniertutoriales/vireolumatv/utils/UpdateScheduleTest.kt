package com.reiniertutoriales.vireolumatv.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class UpdateScheduleTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("update-schedule-test", Context.MODE_PRIVATE).apply { edit().clear().commit() }

    @Test fun aFirstRunChecksThenWaitsADay() {
        val now = 10L * UpdateSchedule.INTERVAL_MS
        assertTrue(UpdateSchedule.isDue(prefs, now))
        UpdateSchedule.checked(prefs, now)
        assertFalse(UpdateSchedule.isDue(prefs, now + UpdateSchedule.INTERVAL_MS - 1))
        assertTrue(UpdateSchedule.isDue(prefs, now + UpdateSchedule.INTERVAL_MS))
    }

    @Test fun aClockSetBackwardsMakesTheCheckDue() {
        val now = 10L * UpdateSchedule.INTERVAL_MS
        UpdateSchedule.checked(prefs, now)
        assertTrue(UpdateSchedule.isDue(prefs, now - 1))
    }
}
