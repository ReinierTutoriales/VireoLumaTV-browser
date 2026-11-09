package com.reiniertutoriales.vireolumatv.adblock

import android.app.Application
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AdblockRequestClassifierTest {
    private fun classify(path: String, headers: Map<String, String> = emptyMap(), main: Boolean = false) =
        AdblockRequestClassifier.classify(Uri.parse("https://example.test$path"), headers, main)

    @Test fun navigationAcceptMakesAnIframeWhateverItsAddressAndNeverAnImage() {
        // Chromium's frame Accept values, with and without JPEG XL and signed exchanges.
        val frames = listOf(
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/jxl,image/avif,image/webp,image/apng,*/*;q=0.8",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8," +
                "application/signed-exchange;v=b3;q=0.7",
        )
        for (accept in frames) {
            val headers = mapOf("accept" to accept)
            assertEquals("document", classify("/", headers, true))
            assertEquals("subdocument", classify("/", headers))
            assertEquals("subdocument", classify("/ad.MJS?image=.png", headers))
        }
        // Only that navigation form counts: a single HTML type keeps the earlier order.
        assertEquals("script", classify("/ad.js", mapOf("Accept" to "text/html")))
    }

    @Test fun parameterizedNavigationAcceptWinsOverMisleadingScriptExtension() {
        val headers = mapOf("Accept" to
            "text/html;q=0.9, application/xhtml+xml;q=0.8, image/avif,*/*;q=0.5")
        assertEquals("subdocument", classify("/tracking.js", headers))
        assertEquals("script", classify("/tracking.js",
            headers + ("Sec-Fetch-Dest" to "script")))
        assertEquals("script", classify("/tracking.js",
            mapOf("Accept" to "text/html;q=0.9, application/json")))
    }

    @Test fun destinationWinsOverUrlAndHeaderNamesAreCaseInsensitive() {
        assertEquals("script", classify("/ads.png", mapOf("sec-fetch-dest" to "script", "accept" to "image/webp")))
        assertEquals("subdocument", classify("/ads.js", mapOf("Sec-Fetch-Dest" to "iframe")))
        assertEquals("xhr", classify("/data.js", mapOf("SEC-FETCH-DEST" to "empty")))
        assertEquals("xhr", classify("/data", mapOf("x-requested-with" to "XMLHttpRequest")))
        assertEquals("font", classify("/font.WOFF2"))
        assertEquals("media", classify("/stream.m3u8"))
        assertEquals("image", classify("/icon.AVIF"))
        assertEquals("unknown", classify("", mapOf("Accept" to "*/*")))
        assertEquals("image", classify("/icon", mapOf("accept" to "image/avif,image/webp,*/*;q=0.8")))
    }

    @Test fun extensionlessJsonRequestsUseXhrRulesWithoutGuessingForMixedAccept() {
        assertEquals("xhr", classify("/youtubei/v1/player", mapOf("Accept" to "application/json")))
        assertEquals("xhr", classify("/api/ads", mapOf("accept" to "application/problem+json")))
        assertEquals("unknown", classify("/api", mapOf("Accept" to "application/json,text/html")))
    }

    @Test fun navigationUsesItsOwnHostWhileSubresourcesUseTheContainingPage() {
        val destination = Uri.parse("https://new-page.test/")
        val previousPage = Uri.parse("https://old-page.test/")
        assertEquals(destination, AdblockRequestClassifier.pageContext(destination, previousPage, true))
        assertEquals(previousPage, AdblockRequestClassifier.pageContext(destination, previousPage, false))
    }
}
