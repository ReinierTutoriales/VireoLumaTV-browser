package com.reiniertutoriales.vireolumatv.webengine.webview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class BlobTransfersTest {
    private var now = 1_000L
    private var tokens = 0
    private val sinks = mutableListOf<MemorySink>()
    private var failOpen = false
    private val transfers = BlobTransfers({ now }, { "t${++tokens}" }) {
        if (failOpen) throw IOException("no space")
        MemorySink().also(sinks::add)
    }

    private class MemorySink : BlobSink {
        val bytes = ByteArrayOutputStream()
        var aborted = false
        var failWrite = false
        override fun write(bytes: ByteArray) { if (failWrite) throw IOException("disk"); this.bytes.write(bytes) }
        override fun finish(): InputStream = ByteArrayInputStream(bytes.toByteArray())
        override fun abort() { aborted = true }
    }

    private fun open(size: Long = 4, url: String = URL): String =
        transfers.begin(url, "report.zip", PAGE) { true }.also { assertTrue(transfers.accept(it, size, "application/zip")) }

    @Test fun chunksInOrderCompleteWithNameTypeAndBytes() {
        val token = open(5)
        assertEquals(3L, transfers.append(token, 0, byteArrayOf(1, 2, 3)))
        assertEquals(5L, transfers.append(token, 3, byteArrayOf(4, 5)))
        val completed = transfers.finish(token, URL, PAGE)!!
        assertEquals("report.zip", completed.ticket.fileName)
        assertEquals("application/zip", completed.ticket.mimeType)
        assertEquals(5L, completed.ticket.size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), completed.bytes.readBytes())
        assertNull("A token is single-use", transfers.finish(token, URL, PAGE))
        assertFalse(sinks.single().aborted)
    }

    @Test fun withoutAPressNothingOpens() {
        assertEquals("", transfers.begin(URL, null, PAGE) { false })
    }

    @Test fun anOpenTransferBlocksAnotherWithoutConsumingThePress() {
        transfers.begin(URL, null, PAGE) { true }
        var consumed = false
        assertEquals("", transfers.begin("blob:https://a.test/2", null, PAGE) { consumed = true; true })
        assertFalse(consumed)
        now += BlobTransfers.TIMEOUT_MS
        assertEquals("t2", transfers.begin("blob:https://a.test/2", null, PAGE) { true })
    }

    @Test fun oversizedUnsizedOrResizedBlobsCloseTheTransfer() {
        val token = transfers.begin(URL, null, PAGE) { true }
        assertFalse(transfers.accept(token, BlobTransfers.MAX_BYTES + 1, ""))
        assertEquals(-1L, transfers.append(token, 0, byteArrayOf(1)))
        val second = transfers.begin(URL, null, PAGE) { true }
        assertFalse(transfers.accept(second, 0, ""))
        val third = open()
        assertFalse("a second size closes it", transfers.accept(third, 2, ""))
        assertTrue(sinks.single().aborted)
    }

    @Test fun noPlaceToWriteRefusesTheSize() {
        failOpen = true
        val token = transfers.begin(URL, null, PAGE) { true }
        assertFalse(transfers.accept(token, 4, ""))
        assertEquals(-1L, transfers.append(token, 0, byteArrayOf(1)))
    }

    @Test fun chunksOutOfOrderEmptyOversizedOrBeyondTheSizeAbort() {
        fun rejected(chunk: () -> Long) {
            assertEquals(-1L, chunk())
            assertTrue(sinks.last().aborted)
        }
        rejected { open().let { transfers.append(it, 1, byteArrayOf(1)) } }
        rejected { open().let { transfers.append(it, 0, ByteArray(0)) } }
        rejected { open().let { transfers.append(it, 0, ByteArray(5)) } }
        rejected { open(BlobTransfers.MAX_BYTES).let { transfers.append(it, 0, ByteArray(BlobTransfers.CHUNK_BYTES + 1)) } }
        rejected { open().let { transfers.append(it, 0, byteArrayOf(1, 2)); transfers.append(it, 0, byteArrayOf(1, 2)) } }
        rejected { open().let { token -> sinks.last().failWrite = true; transfers.append(token, 0, byteArrayOf(1)) } }
        assertEquals(-1L, transfers.append("unknown", 0, byteArrayOf(1)))
    }

    @Test fun finishNeedsEveryByteTheSameAddressAndTheSamePage() {
        fun incomplete(finish: (String) -> BlobTransfers.Completed?) {
            val token = open()
            transfers.append(token, 0, byteArrayOf(1, 2, 3, 4))
            assertNull(finish(token))
            assertTrue(sinks.last().aborted)
        }
        incomplete { transfers.finish(it, "blob:https://a.test/2", PAGE) }
        incomplete { transfers.finish(it, URL, "https://b.test") }
        val partial = open()
        transfers.append(partial, 0, byteArrayOf(1, 2))
        assertNull(transfers.finish(partial, URL, PAGE))
        assertTrue(sinks.last().aborted)
    }

    @Test fun eachStepExtendsTheDeadlineAndIdleTransfersExpire() {
        val token = open(2)
        now += BlobTransfers.TIMEOUT_MS - 1
        assertEquals(1L, transfers.append(token, 0, byteArrayOf(1)))
        now += BlobTransfers.TIMEOUT_MS - 1
        assertEquals(2L, transfers.append(token, 1, byteArrayOf(2)))
        now += BlobTransfers.TIMEOUT_MS
        assertNull(transfers.finish(token, URL, PAGE))
        assertTrue(sinks.single().aborted)
    }

    @Test fun cancelOnlyClosesItsOwnTransfer() {
        val token = open()
        transfers.cancel("other")
        assertEquals(1L, transfers.append(token, 0, byteArrayOf(1)))
        transfers.cancel(token)
        assertTrue(sinks.single().aborted)
        assertEquals(-1L, transfers.append(token, 1, byteArrayOf(2)))
    }

    private companion object {
        const val PAGE = "https://a.test"
        const val URL = "blob:https://a.test/1"
    }
}
