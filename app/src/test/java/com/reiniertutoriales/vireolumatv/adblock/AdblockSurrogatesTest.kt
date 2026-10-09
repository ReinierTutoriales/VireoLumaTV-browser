package com.reiniertutoriales.vireolumatv.adblock

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AdblockSurrogatesTest {
    @Test fun onlyKnownLibrariesGetAnInertScript() {
        fun asset(url: String) = AdblockSurrogates.assetFor(Uri.parse(url))
        assertEquals("adsbygoogle.js", asset("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=ca-pub-1"))
        assertEquals("gpt.js", asset("https://securepubads.g.doubleclick.net/tag/js/gpt.js"))
        assertEquals("analytics.js", asset("https://www.google-analytics.com/analytics.js"))
        assertEquals("gtm.js", asset("https://www.googletagmanager.com/gtag/js?id=G-1"))
        // IMA gets a stand-in that answers "no ads", so players start the content.
        assertEquals("google-ima.js", asset("https://imasdk.googleapis.com/js/sdkloader/ima3.js"))
        assertNull(asset("https://pagead2.googlesyndication.com/pagead/show_ads_impl.js"))
        val response = AdblockSurrogates.responseFor(RuntimeEnvironment.getApplication(),
            Uri.parse("https://www.googletagservices.com/tag/js/gpt.js"))!!
        assertEquals(200, response.statusCode)
        assertEquals("application/javascript", response.mimeType)
        assertTrue(response.data.readBytes().decodeToString().contains("googletag"))
    }
}
