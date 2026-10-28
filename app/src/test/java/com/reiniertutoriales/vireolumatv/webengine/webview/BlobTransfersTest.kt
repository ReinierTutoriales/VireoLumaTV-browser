package com.reiniertutoriales.vireolumatv.webengine.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlobTransfersTest {
    private var now = 1_000L
    private var tokens = 0
    private val transfers = BlobTransfers({ now }, { "t${++tokens}" })
    private val data = "data:application/zip;base64,UEsDBA=="

    @Test fun aPressedTransferKeepsThePageSuppliedNameThroughToTheBytes() {
        val token = transfers.begin("blob:https://a.test/1", "report.zip", PAGE) { true }
        assertTrue(transfers.accept(token, 4, "application/zip"))
        assertNotNull(transfers.take(token, "blob:https://a.test/1", PAGE, data))
        assertNull("A token is single-use", transfers.take(token, "blob:https://a.test/1", PAGE, data))
    }

    @Test fun theTicketCarriesNameAndType() {
        val token = transfers.begin("blob:https://a.test/1", "report.zip", PAGE) { true }
        transfers.accept(token, 4, "application/zip")
        val ticket = transfers.take(token, "blob:https://a.test/1", PAGE, data)!!
        assertEquals("report.zip", ticket.fileName)
        assertEquals("application/zip", ticket.mimeType)
    }

    @Test fun withoutAPressNothingOpens() {
        assertEquals("", transfers.begin("blob:https://a.test/1", null, PAGE) { false })
    }

    @Test fun anOpenTransferBlocksAnotherWithoutConsumingThePress() {
        transfers.begin("blob:https://a.test/1", null, PAGE) { true }
        var consumed = false
        assertEquals("", transfers.begin("blob:https://a.test/2", null, PAGE) { consumed = true; true })
        assertFalse(consumed)
        now += BlobTransfers.TIMEOUT_MS
        assertEquals("t2", transfers.begin("blob:https://a.test/2", null, PAGE) { true })
    }

    @Test fun oversizedOrUnsizedBlobsCloseTheTransfer() {
        val token = transfers.begin("blob:https://a.test/1", null, PAGE) { true }
        assertFalse(transfers.accept(token, BlobTransfers.MAX_BYTES + 1, ""))
        assertNull(transfers.take(token, "blob:https://a.test/1", PAGE, data))
        val second = transfers.begin("blob:https://a.test/1", null, PAGE) { true }
        assertFalse(transfers.accept(second, 0, ""))
    }

    @Test fun bytesMustMatchTheTransfer() {
        fun open() = transfers.begin("blob:https://a.test/1", null, PAGE) { true }.also { transfers.accept(it, 4, "") }
        assertNull("other address", transfers.take(open(), "blob:https://a.test/2", PAGE, data))
        assertNull("other page", transfers.take(open(), "blob:https://a.test/1", "https://b.test/", data))
        assertNull("not base64", transfers.take(open(), "blob:https://a.test/1", PAGE, "data:text/plain,hi"))
        assertNull("not a data URL", transfers.take(open(), "blob:https://a.test/1", PAGE, "UEsDBA=="))
        val unsized = transfers.begin("blob:https://a.test/1", null, PAGE) { true }
        assertNull("size never accepted", transfers.take(unsized, "blob:https://a.test/1", PAGE, data))
    }

    @Test fun anExpiredTransferIsGone() {
        val token = transfers.begin("blob:https://a.test/1", null, PAGE) { true }
        now += BlobTransfers.TIMEOUT_MS
        assertFalse(transfers.accept(token, 4, ""))
    }

    @Test fun cancelOnlyClosesItsOwnTransfer() {
        val token = transfers.begin("blob:https://a.test/1", null, PAGE) { true }
        transfers.cancel("other")
        assertTrue(transfers.accept(token, 4, ""))
        transfers.cancel(token)
        assertNull(transfers.take(token, "blob:https://a.test/1", PAGE, data))
    }

    private companion object {
        const val PAGE = "https://a.test/page"
    }
}
