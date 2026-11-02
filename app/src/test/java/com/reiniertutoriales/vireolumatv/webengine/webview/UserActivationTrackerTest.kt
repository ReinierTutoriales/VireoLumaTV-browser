package com.reiniertutoriales.vireolumatv.webengine.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserActivationTrackerTest {
    private var now = 10_000L
    private val tracker = UserActivationTracker { now }

    @Test fun noActivationMeansNoAuthorization() {
        assertFalse(tracker.consume())
    }

    @Test fun recentActivationAuthorizesExactlyOnce() {
        tracker.mark()
        now += 500
        assertTrue(tracker.consume())
        assertFalse(tracker.consume())
    }

    @Test fun staleActivationIsRejectedAndCleared() {
        tracker.mark()
        now += UserActivationTracker.WINDOW_MS + 1
        assertFalse(tracker.consume())
        now += 1
        assertFalse(tracker.consume())
    }

    @Test fun activationAtTheWindowEdgeIsAccepted() {
        tracker.mark()
        now += UserActivationTracker.WINDOW_MS
        assertTrue(tracker.consume())
    }

    @Test fun clockGoingBackwardsIsRejected() {
        tracker.mark()
        now -= 1
        assertFalse(tracker.consume())
    }
}
