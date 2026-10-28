package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IconFinderTest {
    private val extractor = IconFinder()

    private fun html(base: String?, text: String) =
        extractor.iconsFromDocument(base?.let(::URL), text.reader().buffered())

    @Test fun aPageWithoutHeadEndIsReadOnlyUpToTheBudget() {
        val reader = ("<link rel=\"icon\" href=\"/bounded.png\">" + "x".repeat(IconFinder.MAX_DOCUMENT_CHARS + 8192))
            .reader().buffered()
        val (icons, _) = extractor.iconsFromDocument(URL("https://example.com"), reader)
        assertEquals("https://example.com/bounded.png", icons.single().src)
        assertTrue("Reading stopped with input left", reader.read() >= 0)
    }

    @Test fun candidateAddressesResolveAgainstThePageAndSizesAreRead() {
        val base = URL("http://example.com/folder/index.html")
        val relative = IconFinder.IconCandidate("test/test.ico", IconFinder.FALLBACK_ICON_TYPE, null, "32x32", base)
        assertEquals("http://example.com/folder/test/test.ico", relative.src)
        assertEquals(32, relative.width)
        assertEquals(32, relative.height)
        val rooted = IconFinder.IconCandidate("/test/test.ico", null, null, "any", base)
        assertEquals("http://example.com/test/test.ico", rooted.src)
        assertEquals(0, rooted.width)
        assertEquals("The type follows the extension", IconFinder.FALLBACK_ICON_TYPE, rooted.type)
    }

    @Test fun headIconsAndTheManifestAddressAreFound() {
        val (icons, manifest) = html(null, """
            <html><head>
            <link rel="icon" class="site" href="https://cdn.example/favicon.svg">
            <link rel="manifest" href="/manifest.json" crossorigin="use-credentials">
            </head></html>
        """.trimIndent())
        assertEquals("icon", icons.single().rel)
        assertEquals("image/svg+xml", icons.single().type)
        assertEquals("/manifest.json", manifest)
    }

    @Test fun relTokensQuotedValuesAndUpperCaseTagsAreUnderstood() {
        val (icons, manifest) = html("https://example.com/dir/", """
            <head>
            <link rel="shortcut icon" href="/a b.ico">
            <LINK REL=ICON HREF=/upper.png SIZES=32X32>
            <link href='touch.png' rel='apple-touch-icon-precomposed'>
            <link rel="stylesheet" href="/style.css">
            </head>
            <link rel="icon" href="/after-head.png">
        """.trimIndent())
        assertEquals(listOf("https://example.com/a b.ico", "https://example.com/upper.png", "https://example.com/dir/touch.png"),
            icons.map { it.src })
        assertEquals(32, icons[1].width)
        assertEquals("Apple touch icons default to 180 px", 180, icons[2].width)
        assertNull(manifest)
    }

    @Test fun manifestIconsResolveAgainstTheManifest() {
        val icons = extractor.iconsFromManifest(URL("http://example.com/test/manifest.json"), """
            {"name": "Weather", "icons": [
              {"src": "/images/icons-vector.svg", "type": "image/svg+xml", "sizes": "512x512"},
              {"src": "images/icons-192.png", "type": "image/png", "sizes": "192x192"}
            ], "id": "/?source=pwa"}
        """.trimIndent().reader())
        assertEquals(listOf("http://example.com/images/icons-vector.svg", "http://example.com/test/images/icons-192.png"),
            icons.map { it.src })
        assertEquals(192, icons[1].width)
    }

    @Test fun aBrokenManifestKeepsWhatWasRead() {
        val icons = extractor.iconsFromManifest(URL("http://example.com/m.json"),
            """{"icons": [{"src": "/a.png"}, {"src": """.reader())
        assertEquals(listOf("http://example.com/a.png"), icons.map { it.src })
    }
}
