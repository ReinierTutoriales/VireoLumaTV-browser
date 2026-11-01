package com.reiniertutoriales.vireolumatv.utils

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.webkit.MimeTypeMap
import org.junit.Before

/** The name a download gets from its headers and address (RFC 6266 / RFC 5987 Content-Disposition). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DownloadUtilsGuessFileNameTest {
    @Before fun mimeTypes() {
        shadowOf(MimeTypeMap.getSingleton()).apply {
            addExtensionMimeTypeMapping("pdf", "application/pdf")
            addExtensionMimeTypeMapping("mp4", "video/mp4")
            addExtensionMimeTypeMapping("txt", "text/plain")
            addExtensionMimeTypeMapping("zip", "application/zip")
        }
    }

    private fun guess(url: String, disposition: String? = null, mime: String? = null) =
        FileNames.guessFileName(url, disposition, mime)

    @Test fun theGuessedNameIsAlreadySafeForTheQueueAndTheNotice() {
        // Shown before the service reserves a file and kept as the MediaStore name without a header.
        assertEquals("invoice_fdp.apk", guess("https://example.test/invoice%E2%80%AEfdp.apk"))
        assertEquals("invoice_fdp.zip", guess("https://example.test/x",
            "attachment; filename*=UTF-8''invoice%E2%80%AEfdp.zip", "application/zip"))
        assertEquals("a_b.txt", guess("https://example.test/a:b.txt"))
        assertEquals("evil.zip", guess("https://example.test/x", "attachment; filename=\"..\\\\evil.zip\""))
    }

    @Test fun nameComesFromTheAddressWithoutQuery() {
        assertEquals("report.pdf", guess("https://example.test/files/report.pdf?token=1"))
        assertEquals("a b.mp4", guess("https://example.test/a%20b.mp4"))
    }

    @Test fun addressWithoutNameGivesTheGenericName() {
        assertEquals("downloadfile.bin", guess("https://example.test/"))
        assertEquals("downloadfile.html", guess("https://example.test/", mime = "text/html"))
        assertEquals("downloadfile.txt", guess("https://example.test/", mime = "text/csv"))
        assertEquals("downloadfile.pdf", guess("https://example.test/", mime = "application/pdf"))
    }

    @Test fun dispositionNameWinsOverTheAddress() {
        assertEquals("plain.pdf", guess("https://example.test/x", "attachment; filename=plain.pdf"))
        assertEquals("quoted name.pdf", guess("https://example.test/x", "attachment; filename=\"quoted name.pdf\""))
        // The escaped quote is unescaped, then replaced: a quote is not allowed in a file name.
        assertEquals("esc_aped.pdf", guess("https://example.test/x", "attachment; filename=\"esc\\\"aped.pdf\""))
        assertEquals("inline.pdf", guess("https://example.test/x", "INLINE;FILENAME=inline.pdf"))
        assertEquals("evil.pdf", guess("https://example.test/x", "attachment; filename=\"../../evil.pdf\""))
    }

    @Test fun encodedDispositionNameIsDecoded() {
        assertEquals("résumé.pdf", guess("https://example.test/x",
            "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf"))
        assertEquals("café.txt", guess("https://example.test/x",
            "attachment; filename=x.txt; filename*=iso-8859-1'en'caf%E9.txt"))
    }

    @Test fun unreadableDispositionFallsBackToTheAddress() {
        assertEquals("file.zip", guess("https://example.test/file.zip", "form-data; name=a"))
    }

    @Test fun extensionFollowsAContradictingMimeType() {
        assertEquals("video.pdf", guess("https://example.test/video.mp4", mime = "application/pdf"))
        assertEquals("video.mp4", guess("https://example.test/video.mp4", mime = "application/octet-stream"))
        assertEquals("video.mp4", guess("https://example.test/video.mp4", mime = "video/mp4"))
        assertEquals("archive.tar.gz", guess("https://example.test/archive.tar.gz", mime = "application/x-unknown"))
    }

    @Test fun nameWithoutExtensionGetsOneFromTheMimeType() {
        assertEquals("readme.txt", guess("https://example.test/readme", mime = "text/plain"))
        assertEquals("blob.bin", guess("https://example.test/blob"))
    }

    @Test fun encodedNameWinsInEitherOrderAndQuotedSemicolonsStayInTheName() {
        assertEquals("ñ.txt", guess("https://example.test/x", "attachment; filename*=UTF-8''%C3%B1.txt; filename=n.txt"))
        assertEquals("a;b.txt", guess("https://example.test/x", "attachment; filename=\"a;b.txt\"; size=3"))
        assertEquals("n.txt", guess("https://example.test/x", "attachment; filename=n.txt; filename*=koi8-r''x.txt"))
    }
}
