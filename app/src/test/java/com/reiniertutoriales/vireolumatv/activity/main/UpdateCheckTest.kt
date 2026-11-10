package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Looper
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater
import com.reiniertutoriales.vireolumatv.utils.UpdateSchedule
import kotlinx.coroutines.cancel
import androidx.lifecycle.lifecycleScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** When the generic build looks for a release by itself, and when a check stays due. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class UpdateCheckTest {
    private val activity = Robolectric.buildActivity(MainActivity::class.java).get().apply { startupDone = true }
    private val prefs = AppContext.config.prefs.apply { edit().remove(UpdateSchedule.LAST_CHECK_KEY).commit() }
    private val previousPrivate = AppContext.config.privateBrowsing
    private var fetches = 0

    init { AppContext.config.privateBrowsing = false }

    @After fun tearDown() {
        activity.lifecycleScope.cancel()
        AppContext.config.privateBrowsing = previousPrivate
        prefs.edit().remove(UpdateSchedule.LAST_CHECK_KEY).commit()
    }

    private fun check(result: () -> ReleaseUpdater.Release?) {
        activity.checkForUpdateIfDue { fetches++; result() }
        repeat(200) {
            if (activity.updateCheck?.isActive != true) return
            Thread.sleep(5)
            shadowOf(Looper.getMainLooper()).idle()
        }
        error("The update check did not finish")
    }

    private val due get() = UpdateSchedule.isDue(prefs, System.currentTimeMillis())

    @Test fun anUpToDateCheckWaitsADay() {
        check { null }
        assertEquals(1, fetches)
        assertFalse(due)
        check { null }
        assertEquals("Not due again the same day", 1, fetches)
    }

    @Test fun aFailedCheckStaysDue() {
        check { throw java.io.IOException("offline") }
        assertEquals(1, fetches)
        assertTrue(due)
    }

    @Test fun noCheckBeforeStartUpOrInPrivateMode() {
        activity.startupDone = false
        check { null }
        activity.startupDone = true
        AppContext.config.privateBrowsing = true
        check { null }
        assertEquals(0, fetches)
        assertTrue(due)
    }

    @Test fun aReleaseFoundDuringFullscreenVideoIsNotOfferedAndStaysDue() {
        activity.isFullscreen = true
        check { ReleaseUpdater.Release("99.0.0", "app.apk", "https://example.test/app.apk", "0".repeat(64)) }
        assertEquals(1, fetches)
        assertTrue(due)
    }
}
