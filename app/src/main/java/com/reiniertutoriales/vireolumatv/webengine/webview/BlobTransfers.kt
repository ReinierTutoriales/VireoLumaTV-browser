package com.reiniertutoriales.vireolumatv.webengine.webview

/**
 * Downloads of `blob:` addresses, which only the page can read. A press opens one transfer and
 * hands the page a single-use token; the page then reports the size (refused above
 * [MAX_BYTES], before any encoding) and hands over the bytes as a base64 `data:` URL. A transfer
 * expires after [TIMEOUT_MS]; only one is open at a time. Called from the JavaBridge thread.
 */
internal class BlobTransfers(private val clock: () -> Long, private val newToken: () -> String) {
    /** An open transfer; [page] is the page address it was opened from. */
    class Ticket(val token: String, val url: String, val fileName: String?, val page: String, val expires: Long) {
        var mimeType = ""
            internal set
        var sized = false
            internal set
    }

    private var open: Ticket? = null

    /** A token for [url], or "" when another transfer is open or [activated] reports no fresh press. */
    @Synchronized
    fun begin(url: String, fileName: String?, page: String, activated: () -> Boolean): String {
        val now = clock()
        if (live(now) != null || !activated()) return ""
        return newToken().also { open = Ticket(it, url, fileName, page, now + TIMEOUT_MS) }
    }

    /** False closes the transfer: no size, or one above [MAX_BYTES]. */
    @Synchronized
    fun accept(token: String, size: Long, mimeType: String): Boolean {
        val ticket = live(clock())?.takeIf { it.token == token } ?: return false
        if (size !in 1..MAX_BYTES) {
            open = null
            return false
        }
        ticket.mimeType = mimeType
        ticket.sized = true
        return true
    }

    @Synchronized
    fun cancel(token: String) {
        if (open?.token == token) open = null
    }

    /** Closes the transfer and returns it when the bytes match it: same address, same page, accepted size. */
    @Synchronized
    fun take(token: String, url: String, page: String, dataUrl: String): Ticket? {
        val ticket = live(clock())?.takeIf { it.token == token } ?: return null
        open = null
        return ticket.takeIf { it.sized && it.url == url && it.page == page && fits(dataUrl) }
    }

    /** The open transfer, closing it first if it has expired. */
    private fun live(now: Long): Ticket? {
        val ticket = open ?: return null
        if (ticket.expires > now) return ticket
        open = null
        return null
    }

    /** A base64 `data:` URL whose payload cannot decode to more than [MAX_BYTES]. */
    private fun fits(dataUrl: String): Boolean {
        val comma = dataUrl.indexOf(',')
        if (!dataUrl.startsWith("data:", ignoreCase = true) || comma <= 0 || comma == dataUrl.lastIndex) return false
        if (!dataUrl.substring(0, comma).contains(";base64", ignoreCase = true)) return false
        return dataUrl.length - comma - 1 <= (MAX_BYTES + 2) / 3 * 4
    }

    companion object {
        /** Covers the read and encoding of the largest accepted blob on a slow TV chip. */
        const val TIMEOUT_MS = 30_000L
        const val MAX_BYTES = 32L * 1024 * 1024
    }
}
