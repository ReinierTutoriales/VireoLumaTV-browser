package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.WebView
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.WebTabState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class WebViewUserAgentTest {
    @Test fun defaultSelectionResetsTheLiveWebViewWithoutReplacingIt() {
        val app = RuntimeEnvironment.getApplication()
        val reference = WebView(app)
        reference.settings.userAgentString = null
        val expectedDefault = reference.settings.userAgentString
        reference.destroy()
        val engine = WebViewWebEngine(WebTabState())
        try {
            val live = engine.getOrCreateView(app) as WebViewEx
            engine.userAgentString = "CustomBrowser/2.0"
            assertEquals("CustomBrowser/2.0", live.settings.userAgentString)
            engine.userAgentString = null
            assertEquals(expectedDefault, live.settings.userAgentString)
            assertNotEquals("CustomBrowser/2.0", live.settings.userAgentString)
            assertNull(engine.userAgentString)
            assertSame("Changing UA must not replace the view", live, engine.getView())
            engine.userAgentString = "DesktopBrowser/3.0"
            assertEquals("DesktopBrowser/3.0", live.settings.userAgentString)
        } finally { engine.onDetachFromWindow(completely = true, destroyTab = true) }
    }
}
