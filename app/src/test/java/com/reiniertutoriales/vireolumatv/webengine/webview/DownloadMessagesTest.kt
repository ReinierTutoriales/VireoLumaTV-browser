package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Application
import android.net.Uri
import com.reiniertutoriales.vireolumatv.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
@RoboConfig(application = Application::class, sdk = [28])
class DownloadMessagesTest {
    private val page = Uri.parse("https://site.test/watch?v=1")

    @Test fun mainFrameOfTheVisiblePageActsForItsOrigin() {
        assertEquals("https://site.test", downloadMessageDocument(true, Uri.parse("https://site.test"), page))
        assertEquals("https://site.test", downloadMessageDocument(true, Uri.parse("https://SITE.test:443"), page))
        assertEquals("http://site.test:8080",
            downloadMessageDocument(true, Uri.parse("http://site.test:8080"), Uri.parse("http://site.test:8080/a")))
    }

    @Test fun iframesOtherOriginsAndInternalPagesAreRejected() {
        // An embedded frame, even one of the same site, never reaches downloads.
        assertNull(downloadMessageDocument(false, Uri.parse("https://site.test"), page))
        assertNull(downloadMessageDocument(false, Uri.parse("https://ads.example"), page))
        assertNull(downloadMessageDocument(true, Uri.parse("https://ads.example"), page))
        assertNull(downloadMessageDocument(true, Uri.parse("http://site.test"), page))
        assertNull(downloadMessageDocument(true, Uri.parse("https://site.test:8443"), page))
        assertNull(downloadMessageDocument(true, Uri.parse("null"), page))
        assertNull(downloadMessageDocument(true, null, page))
        assertNull(downloadMessageDocument(true, Uri.parse("https://site.test"), null))
        val home = Uri.parse(Config.HOME_PAGE_URL)
        assertNull(downloadMessageDocument(true, Uri.parse(webOrigin(home) ?: "https://x"), home))
        assertNull(downloadMessageDocument(true, Uri.parse("file://"), Uri.parse("file:///android_asset/")))
    }

    @Test fun requestsParseWithBoundedFieldsAndFreeFormLastField() {
        assertEquals(DownloadMessage.Begin("blob:https://site.test/id", "a\nb.txt"),
            DownloadMessage.parse("begin\nblob:https://site.test/id\na\nb.txt"))
        assertEquals(DownloadMessage.Begin("blob:https://site.test/id", null),
            DownloadMessage.parse("begin\nblob:https://site.test/id\n"))
        assertEquals(DownloadMessage.Size("t", 3, "text/plain"), DownloadMessage.parse("size\nt\n3\ntext/plain"))
        assertEquals(DownloadMessage.Cancel("t"), DownloadMessage.parse("cancel\nt"))
        val chunk = DownloadMessage.parse("chunk\nt\n524288\nYWJj") as DownloadMessage.Chunk
        assertEquals(listOf("t", 524288L, "YWJj"), listOf(chunk.token, chunk.offset, chunk.base64))
        assertEquals(DownloadMessage.Done("t", "blob:https://site.test/id"), DownloadMessage.parse("done\nt\nblob:https://site.test/id"))
    }

    @Test fun malformedRequestsAreIgnored() {
        val oversizedChunk = "chunk\nt\n0\n" + "A".repeat((BlobTransfers.CHUNK_BYTES + 2) / 3 * 4 + 9000)
        listOf(null, "", "begin", "begin\n\nname", "size\nt\nlots\ntype", "size\nt\n3", "cancel\n", "chunk\nt\n0",
            "chunk\n\n0\nYWJj", "chunk\nt\n-1\nYWJj", "chunk\nt\nx\nYWJj", "chunk\nt\n0\n", oversizedChunk,
            "done\nt", "done\nt\n", "data\nt\nurl\ndata:", "other\nt", "begin\n" + "x".repeat(5000) + "\nname",
        ).forEach { assertNull(it?.take(40), DownloadMessage.parse(it)) }
    }

    @Test fun repliesMatchTheScriptProtocol() {
        assertEquals("begin\nblob:https://site.test/id\nt", DownloadMessage.beginReply("blob:https://site.test/id", "t"))
        assertEquals("size\nt\n1", DownloadMessage.sizeReply("t", true))
        assertEquals("size\nt\n0", DownloadMessage.sizeReply("t", false))
        assertEquals("chunk\nt\n-1", DownloadMessage.chunkReply("t", -1))
    }
}
