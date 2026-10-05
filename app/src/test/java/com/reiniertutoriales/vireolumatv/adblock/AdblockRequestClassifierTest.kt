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

    @Test fun mixedNavigationAcceptDoesNotTurnADocumentIntoAnImage() {
        val headers = mapOf("Accept" to "text/html,application/xhtml+xml,image/avif,image/webp,*/*;q=0.8")
        assertEquals("document", classify("/", headers, true))
        assertEquals("unknown", classify("/", headers))
        assertEquals("script", classify("/ad.MJS?image=.png", headers))
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

    @Test fun nativeFlagsDoNotMixObjectsWithImagesAndSupportMediaAndFonts() {
        assertEquals(8, AdblockRequestClassifier.filterOption("object"))
        assertEquals(0, AdblockRequestClassifier.filterOption("object") and AdblockRequestClassifier.filterOption("image"))
        assertEquals(16, AdblockRequestClassifier.filterOption("xhr"))
        assertEquals(64, AdblockRequestClassifier.filterOption("subdocument"))
        assertEquals(128, AdblockRequestClassifier.filterOption("document"))
        assertEquals(1048576, AdblockRequestClassifier.filterOption("font"))
        assertEquals(2097152, AdblockRequestClassifier.filterOption("media"))
    }
}
