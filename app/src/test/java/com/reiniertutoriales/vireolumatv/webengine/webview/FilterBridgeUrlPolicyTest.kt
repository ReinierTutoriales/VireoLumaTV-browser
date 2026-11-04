package com.reiniertutoriales.vireolumatv.webengine.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterBridgeUrlPolicyTest {
    @Test fun acceptsWebOrigins() {
        assertTrue(FilterBridgeUrlPolicy.isWebFrame("https://example.com/watch"))
        assertTrue(FilterBridgeUrlPolicy.isWebFrame("http://localhost:8080/"))
    }

    @Test fun rejectsHostlessOrUnsupportedSchemes() {
        for (url in listOf(null, "", "http://", "https:/relative", "file:///android_asset/a",
                "javascript:alert(1)", "data:text/html,hello", "blob:https://example.com/id",
                "https://", "https://example.com/" + "x".repeat(4096))) {
            assertFalse("Rejected: $url", FilterBridgeUrlPolicy.isWebFrame(url))
        }
    }
}
