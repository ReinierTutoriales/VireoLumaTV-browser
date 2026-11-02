package com.reiniertutoriales.vireolumatv.webengine.webview

import java.io.IOException
import java.io.InputStream

/**
 * Downloads of `blob:` addresses, which only the page can read. A press opens one transfer and
 * hands the page a single-use token; the page reports the size (refused above [MAX_BYTES]) and then
 * sends the bytes in order, a chunk at a time, into a [BlobSink]. Only one chunk is in flight, so the
 * memory a transfer holds is one chunk whatever the file size. A transfer expires [TIMEOUT_MS] after
 * its last accepted step; only one is open at a time. Callers serialize all calls on one thread.
 */
internal class BlobTransfers(
    private val clock: () -> Long,
    private val newToken: () -> String,
    private val openSink: () -> BlobSink,
) {
    /** An open transfer; [page] is the document key it was opened from. */
    class Ticket(val token: String, val url: String, val fileName: String?, val page: String, internal var expires: Long) {
        var mimeType = ""
            internal set
        /** The declared size, 0 until accepted. */
        var size = 0L
            internal set
        var received = 0L
            internal set
        internal var sink: BlobSink? = null
    }

    /** A finished transfer: its ticket and the bytes, to be read once and closed. */
    class Completed(val ticket: Ticket, val bytes: InputStream)

    private var open: Ticket? = null

    /** A token for [url], or "" when another transfer is open or [activated] reports no fresh press. */
    @Synchronized
    fun begin(url: String, fileName: String?, page: String, activated: () -> Boolean): String {
        val now = clock()
        if (live(now) != null || !activated()) return ""
        return newToken().also { open = Ticket(it, url, fileName, page, now + TIMEOUT_MS) }
    }

    /** False closes the transfer: no size, one above [MAX_BYTES], a second size or no place to write. */
    @Synchronized
    fun accept(token: String, size: Long, mimeType: String): Boolean {
        val now = clock()
        val ticket = live(now)?.takeIf { it.token == token } ?: return false
        if (size !in 1..MAX_BYTES || ticket.sink != null) return close(false)
        ticket.sink = try { openSink() } catch (_: IOException) { return close(false) }
        ticket.mimeType = mimeType
        ticket.size = size
        ticket.expires = now + TIMEOUT_MS
        return true
    }

    /**
     * Writes the chunk that starts at [offset]; returns the bytes received so far, or -1 after closing
     * the transfer: wrong token, out of order, empty, above the declared size, or a write error.
     */
    @Synchronized
    fun append(token: String, offset: Long, chunk: ByteArray): Long {
        val now = clock()
        val ticket = live(now)?.takeIf { it.token == token } ?: return -1
        val sink = ticket.sink
        if (sink == null || offset != ticket.received || chunk.isEmpty() || chunk.size > CHUNK_BYTES ||
            ticket.received + chunk.size > ticket.size) return closeLong()
        try { sink.write(chunk) } catch (_: IOException) { return closeLong() }
        ticket.received += chunk.size
        ticket.expires = now + TIMEOUT_MS
        return ticket.received
    }

    /** Closes the transfer and returns its bytes when all declared bytes arrived for the same address and page. */
    @Synchronized
    fun finish(token: String, url: String, page: String): Completed? {
        val ticket = live(clock())?.takeIf { it.token == token } ?: return null
        val sink = ticket.sink
        if (sink == null || ticket.received != ticket.size || ticket.url != url || ticket.page != page) return close(null)
        open = null
        return try { Completed(ticket, sink.finish()) } catch (_: IOException) { sink.abort(); null }
    }

    @Synchronized
    fun cancel(token: String) {
        if (open?.token == token) close(Unit)
    }

    /** The open transfer, closing it first if it has expired. */
    private fun live(now: Long): Ticket? {
        val ticket = open ?: return null
        if (ticket.expires > now) return ticket
        return close(null)
    }

    private fun <T> close(result: T): T {
        open?.sink?.abort()
        open = null
        return result
    }

    private fun closeLong(): Long = close(-1L)

    companion object {
        /** Covers the slowest single step (reading or writing one chunk) on a slow TV chip. */
        const val TIMEOUT_MS = 30_000L
        const val MAX_BYTES = 32L * 1024 * 1024
        /** The largest decoded chunk accepted; generic_injects.js sends chunks of exactly this size. */
        const val CHUNK_BYTES = 512 * 1024
    }
}

/** Where a transfer's bytes go while they arrive. */
internal interface BlobSink {
    @Throws(IOException::class) fun write(bytes: ByteArray)
    /** Ends writing; the stream reads the bytes and releases them when closed. */
    @Throws(IOException::class) fun finish(): InputStream
    /** Ends writing and releases the bytes; never throws. */
    fun abort()
}
