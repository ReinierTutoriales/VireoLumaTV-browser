package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.JavascriptInterface
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every method here is callable from any page loaded in the WebView. The set must match the
 * methods the packaged scripts actually call.
 */
class AndroidJSInterfaceSurfaceTest {
    @Test
    fun bridgeExposesOnlyMethodsThePackagedPagesCall() {
        val exposed = AndroidJSInterface::class.java.methods
            .filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
            .map { it.name }
            .toSet()
        assertEquals(
            setOf(
                "pageFilters", "hiddenSelectors", "mediaStarted",
                "currentUrl", "reloadWithSslTrust", "getStringByName", "lastSSLError",
                "beginBlobDownload", "acceptBlobSize", "cancelBlobDownload", "takeBlobDownloadData",
            ),
            exposed,
        )
    }
}
