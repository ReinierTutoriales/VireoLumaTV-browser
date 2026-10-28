package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.JavascriptInterface
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every bridge method is callable by any page; the set must be exactly what the packaged scripts call. */
class PageBridgeSurfaceTest {
    @Test
    fun bridgeExposesOnlyMethodsThePackagedPagesCall() {
        val exposed = PageBridge::class.java.methods
            .filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
            .map { it.name }
            .toSet()
        assertEquals(
            setOf(
                "pageFilters", "hiddenSelectors", "mediaStarted", "trustCertificateOnce",
                "beginBlobDownload", "acceptBlobSize", "cancelBlobDownload", "takeBlobDownloadData",
            ),
            exposed,
        )
    }
}
