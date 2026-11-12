package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.WebView
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowWebView

/**
 * WebView.pauseTimers() stops timers, layout and parsing in every WebView of the app. Closing a
 * blocked popup or a background tab must therefore pause that view only, or the page on screen
 * stalls (players stop, requests are never retried) until the app leaves and returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], shadows = [GlobalTimersTest.CountingWebView::class])
class GlobalTimersTest {
    @Implements(WebView::class)
    class CountingWebView : ShadowWebView() {
        @Implementation fun pauseTimers() { paused++ }

        companion object { var paused = 0 }
    }

    @Before fun reset() { CountingWebView.paused = 0 }

    @Test fun closingOrSettingAsideATabPausesOnlyItsOwnView() {
        val app = RuntimeEnvironment.getApplication()
        val closed = WebViewPageEngine(BrowserTab())
        val view = closed.createView(app) as WebView
        closed.detach(destroyView = true)
        val aside = WebViewPageEngine(BrowserTab())
        val kept = aside.createView(app) as WebView
        try {
            aside.detach(destroyView = false)
            assertTrue(shadowOf(view).wasOnPauseCalled())
            assertTrue(shadowOf(kept).wasOnPauseCalled())
            assertEquals("A tab detach must not pause every WebView", 0, CountingWebView.paused)
        } finally { aside.detach(destroyView = true) }
    }

    @Test fun leavingTheScreenStillPausesAllTimers() {
        val engine = WebViewPageEngine(BrowserTab())
        engine.createView(RuntimeEnvironment.getApplication())
        try {
            engine.pause()
            assertEquals(1, CountingWebView.paused)
        } finally { engine.detach(destroyView = true) }
    }
}
