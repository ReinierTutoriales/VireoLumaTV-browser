package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.JavascriptInterface
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every bridge method is callable by any frame; each set must be exactly what the packaged scripts call. */
class PageBridgeSurfaceTest {
    private fun exposed(type: Class<*>) = type.methods
        .filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
        .map { it.name }
        .toSet()

    private val filters = setOf("pageFilters", "hiddenSelectors", "mediaStarted")

    @Test
    fun modernBridgeIsReadOnly() {
        assertEquals(filters, exposed(FilterBridge::class.java))
    }

    @Test
    fun legacyBridgeAddsOnlyThePrivilegedCallsOldProvidersNeed() {
        assertEquals(
            filters + setOf(
                "trustCertificateOnce", "beginBlobDownload", "acceptBlobSize", "cancelBlobDownload", "takeBlobDownloadData",
            ),
            exposed(LegacyPrivilegedBridge::class.java),
        )
    }
}
