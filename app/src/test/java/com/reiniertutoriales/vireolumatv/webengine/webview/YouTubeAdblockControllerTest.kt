package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class YouTubeAdblockControllerTest {
    @Test fun scopeAllowsHttpsYouTubeAndEmbedsOnly() {
        for (url in listOf("https://youtube.com/watch?v=x", "https://m.youtube.com/",
            "https://www.youtube-nocookie.com/embed/x", "https://WWW.YOUTUBE.COM/")) {
            assertTrue(url, YouTubeAdblockController.isYouTube(url))
        }
        for (url in listOf("https://youtube.com.evil.test/", "https://notyoutube.com/",
            "https://youtube.com@evil.test/", "http://youtube.com/", "about:blank",
            "file:///youtube.com/", "https://example.test/")) {
            assertFalse(url, YouTubeAdblockController.isYouTube(url))
        }
        assertFalse(YouTubeAdblockController.isYouTube(null))
    }
}
