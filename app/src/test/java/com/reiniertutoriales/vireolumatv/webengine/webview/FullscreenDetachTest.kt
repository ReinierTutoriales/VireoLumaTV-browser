package com.reiniertutoriales.vireolumatv.webengine.webview

import android.view.View
import android.webkit.WebChromeClient
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A page left in fullscreen while its tab is set aside (a popup opener) must be told it ended. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class FullscreenDetachTest {
    private class Callback : WebChromeClient.CustomViewCallback {
        var hidden = 0
        override fun onCustomViewHidden() { hidden++ }
    }

    @Test fun detachingAFullscreenTabEndsFullscreenForThePageAndAcceptsTheNextRequest() {
        val app = RuntimeEnvironment.getApplication()
        val engine = WebViewPageEngine(BrowserTab())
        try {
            val page = engine.createView(app) as BrowserWebView
            val chrome = page.webChromeClient!!
            val first = Callback()
            val video = View(app)
            chrome.onShowCustomView(video, first)
            engine.detach(destroyView = false)
            assertEquals("Chromium must learn that the fullscreen view is gone", 1, first.hidden)
            assertNull(video.parent)
            val second = Callback()
            chrome.onShowCustomView(View(app), second)
            assertEquals("A later fullscreen request must not be refused as already shown", 0, second.hidden)
            engine.closeFullscreen()
            assertEquals(1, second.hidden)
            assertEquals("The first callback is not called again", 1, first.hidden)
        } finally { engine.detach(destroyView = true) }
    }
}
