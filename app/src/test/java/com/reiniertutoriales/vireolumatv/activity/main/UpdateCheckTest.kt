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
import org.junit.Assert.assertSame
import java.util.concurrent.TimeUnit
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
        waitForCheck()
    }

    private fun waitForCheck() {
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

    @Test fun fullscreenOfferIsRetainedAndShownOnceWithoutAnotherFetch() {
        val release = ReleaseUpdater.Release("99.0.0", "app.apk", "https://example.test/app.apk", "0".repeat(64))
        activity.isFullscreen = true
        activity.updateForeground = true
        check { release }
        assertSame(release, activity.pendingUpdate)
        check { error("Cached release must not be fetched again") }
        var offers = 0
        assertFalse(activity.showPendingUpdate { offers++ })
        activity.isFullscreen = false
        assertTrue(activity.showPendingUpdate { assertSame(release, it); offers++ })
        assertFalse(activity.showPendingUpdate { offers++ })
        assertEquals(1, offers)
        assertEquals(1, fetches)
        assertFalse(due)
        assertTrue(activity.updateTimer?.isActive == true)
    }

    @Test fun aBackgroundResultWaitsForForegroundAndPrivateModeCannotShowIt() {
        val release = ReleaseUpdater.Release("99.0.0", "app.apk", "https://example.test/app.apk", "0".repeat(64))
        check { release }
        assertFalse(activity.showPendingUpdate { error("Background offer") })
        assertSame(release, activity.pendingUpdate)
        assertTrue(due)
        activity.updateForeground = true
        AppContext.config.privateBrowsing = true
        assertFalse(activity.showPendingUpdate { error("Private offer") })
        AppContext.config.privateBrowsing = false
        assertTrue(activity.showPendingUpdate { assertSame(release, it) })
        assertFalse(due)
    }

    @Test fun dueTimerChecksWhileForegroundAndPauseCancelsTheNextTimer() {
        activity.updateForeground = true
        activity.scheduleUpdateCheck(10L) { fetches++; null }
        shadowOf(Looper.getMainLooper()).idleFor(11L, TimeUnit.MILLISECONDS)
        waitForCheck()
        assertEquals(1, fetches)
        val next = requireNotNull(activity.updateTimer)
        assertTrue(next.isActive)
        activity.stopUpdateTimer()
        assertTrue(next.isCancelled)
        assertFalse(activity.updateForeground)
        activity.scheduleUpdateCheck(10L) { error("Paused check") }
        assertEquals(null, activity.updateTimer)
    }

    @Test fun aFailedForegroundCheckSchedulesABoundedRetry() {
        activity.updateForeground = true
        check { throw java.io.IOException("offline") }
        assertTrue(due)
        assertTrue(activity.updateTimer?.isActive == true)
        assertTrue(UpdateSchedule.RETRY_MS > 0L && UpdateSchedule.RETRY_MS < UpdateSchedule.INTERVAL_MS)
    }

    @Test fun dismissAfterPauseRearmsOneTimerWithoutRefetchingTheOfferedRelease() {
        activity.updateForeground = true
        activity.isFullscreen = true
        check { ReleaseUpdater.Release("99.0.0", "app.apk", "https://example.test/app.apk", "0".repeat(64)) }
        activity.isFullscreen = false
        assertTrue(activity.showPendingUpdate {})
        val beforePause = requireNotNull(activity.updateTimer)
        activity.stopUpdateTimer()
        assertTrue(beforePause.isCancelled)
        activity.updateForeground = true
        activity.onUpdateOfferDismissed()
        val afterDismiss = requireNotNull(activity.updateTimer)
        assertTrue(afterDismiss.isActive)
        assertEquals(1, fetches)
        activity.onUpdateOfferDismissed()
        assertTrue(afterDismiss.isCancelled)
        assertTrue(activity.updateTimer?.isActive == true)
        assertEquals(1, fetches)
    }
}
