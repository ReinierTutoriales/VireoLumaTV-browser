package com.reiniertutoriales.vireolumatv.adblock

import android.net.Uri
import com.reiniertutoriales.vireolumatv.model.PopupGuard
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PopupGuardTest {
    private var blocked = 0
    private var allowed = 0
    private fun guard() = PopupGuard({ it.host == "ads.test" }, { blocked++ }, { allowed++ })

    @Test fun adDestinationIsCancelledOnceAndNeverShown() {
        val g = guard()
        assertTrue(g.onNavigation(Uri.parse("https://ads.test/pop")))
        assertTrue(g.isBlocked)
        g.allow() // the timeout must not show a blocked popup
        assertEquals(1, blocked)
        assertEquals(0, allowed)
    }

    @Test fun safeDestinationIsShownAndRedirectsToAdsAreStillCaught() {
        val g = guard()
        assertFalse(g.onNavigation(Uri.parse("https://site.test/login")))
        assertFalse(g.onNavigation(Uri.parse("about:blank")))
        assertEquals(1, allowed)
        assertTrue(g.onNavigation(Uri.parse("https://ads.test/redirect")))
        assertEquals(1, blocked)
    }

    @Test fun windowThatNeverNavigatesIsClosedAtTheTimeout() {
        val g = guard()
        assertFalse(g.onNavigation(Uri.parse("about:blank")))
        g.expire()
        assertTrue(g.isBlocked)
        assertEquals(1, blocked)
        assertEquals(0, allowed)
        // Late navigations of the closed window are cancelled without a second notification.
        assertTrue(g.onNavigation(Uri.parse("https://site.test/late")))
        assertEquals(1, blocked)
    }

    @Test fun timeoutAfterASafeNavigationKeepsTheWindow() {
        val g = guard()
        assertFalse(g.onNavigation(Uri.parse("https://site.test/login")))
        g.expire()
        assertFalse(g.isBlocked)
        assertEquals(0, blocked)
        assertEquals(1, allowed)
    }

    @Test fun screeningStopsAfterTheShownPageFinished() {
        val g = guard()
        g.allow()
        g.onPageFinished("https://site.test/")
        assertFalse(g.onNavigation(Uri.parse("https://ads.test/later-user-click")))
        assertEquals(0, blocked)
        assertEquals(1, allowed)
    }
}
