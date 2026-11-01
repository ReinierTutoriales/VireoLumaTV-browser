package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.WebSettings
import android.webkit.WebView
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig

/** Every page view gets the same sandbox and the user's playback choice. */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(application = VireoLumaTVApp::class, sdk = [28])
class PageSettingsTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun settingsWith(autoplay: Boolean): WebSettings {
        val config = Config(app.getSharedPreferences("page-settings-test", 0)).apply { allowAutoplayMedia = autoplay }
        val view = WebView(app)
        PageSettings.apply(view.settings, config)
        return view.settings
    }

    @Test fun pagesCannotReachDeviceFilesOrOtherAppsContent() {
        val settings = settingsWith(autoplay = false)
        assertFalse(settings.allowFileAccess)
        assertFalse(settings.allowContentAccess)
        @Suppress("DEPRECATION") assertFalse(settings.allowFileAccessFromFileURLs)
        @Suppress("DEPRECATION") assertFalse(settings.allowUniversalAccessFromFileURLs)
    }

    @Test fun pagesRunTheirScriptsZoomAndOpenWindowsOnlyThroughThePopupRules() {
        val settings = settingsWith(autoplay = false)
        assertTrue(settings.javaScriptEnabled)
        assertTrue(settings.domStorageEnabled)
        assertTrue(settings.builtInZoomControls)
        assertFalse(settings.displayZoomControls)
        assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
        assertTrue(settings.supportMultipleWindows())
        assertEquals(WebSettings.LOAD_DEFAULT, settings.cacheMode)
    }

    @Test fun autoplayFollowsTheSetting() {
        assertTrue(settingsWith(autoplay = false).mediaPlaybackRequiresUserGesture)
        assertFalse(settingsWith(autoplay = true).mediaPlaybackRequiresUserGesture)
    }
}
