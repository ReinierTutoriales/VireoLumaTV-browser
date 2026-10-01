package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Application
import android.net.Uri
import com.reiniertutoriales.vireolumatv.Config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
@RoboConfig(application = Application::class, sdk = [28])
class BridgePagePolicyTest {
    @Test fun downloadedBlobMustComeFromAWebDocumentNotTheSyntheticHomeOrigin() {
        assertTrue(BridgePagePolicy.isNormalWebPage(Uri.parse("https://example.com/page")))
        listOf(Config.HOME_PAGE_URL, Config.HOME_PAGE_URL + "index.html", "about:home", "file:///android_asset/", "http:opaque", "https://", "data:text/html,hello").forEach {
            assertFalse(it, BridgePagePolicy.isNormalWebPage(Uri.parse(it)))
        }
    }
    @Test fun certificateAccessRequiresTheExactPackagedPageAndAnActiveGeneratedWarning() {
        val page = Uri.parse("file:///android_asset/")
        val warning = "internal://warning?type=certificate&url=https%3A%2F%2Fexample.com"
        assertTrue(BridgePagePolicy.isCertificatePage(page, warning, true))
        assertFalse(BridgePagePolicy.isCertificatePage(page, null, true))
        assertFalse(BridgePagePolicy.isCertificatePage(page, warning, false))
        assertFalse(BridgePagePolicy.isCertificatePage(Uri.parse("file:///sdcard/evil.html"), warning, true))
        assertFalse(BridgePagePolicy.isCertificatePage(Uri.parse("https://example.com"), warning, true))
    }
}
