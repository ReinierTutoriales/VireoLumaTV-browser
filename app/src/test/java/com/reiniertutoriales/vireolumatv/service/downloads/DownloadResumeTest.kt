package com.reiniertutoriales.vireolumatv.service.downloads

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.Collections

/** A cut connection goes on from the bytes written when the server confirms the same file. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class DownloadResumeTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val body = "0123456789".toByteArray()
    /** Range, If-Range and Accept-Encoding of every request, in order. */
    private val requests = Collections.synchronizedList(mutableListOf<Triple<String?, String?, String?>>())

    private inner class Recorder : Transfer.Callback {
        var done = 0
        var errors = 0
        override fun onProgress(task: Transfer) {}
        override fun onError(task: Transfer, responseCode: Int, responseMessage: String) { errors++ }
        override fun onDone(task: Transfer) { done++ }
    }

    @After fun stop() = server.stop(0)

    private fun download(name: String) = Download().apply {
        incognito = true
        url = "$base/file"
        filename = name
        filepath = File(VireoLumaTVApp.instance.cacheDir, name).apply { delete() }.absolutePath
    }

    private fun run(info: Download): Recorder = Recorder().also {
        FileTransfer(info, "test", it) { _, _ -> 0L }.run()
    }

    private fun HttpExchange.record() {
        requests += Triple(requestHeaders.getFirst("Range"), requestHeaders.getFirst("If-Range"),
            requestHeaders.getFirst("Accept-Encoding"))
    }

    /** Announces the whole body but sends only [count] bytes, then drops the connection. */
    private fun HttpExchange.cutAfter(count: Int, validator: Pair<String, String>?) {
        validator?.let { responseHeaders.add(it.first, it.second) }
        sendResponseHeaders(200, body.size.toLong())
        responseBody.write(body, 0, count)
        responseBody.flush()
        // The fixed-length stream refuses a short close; ending the exchange drops the socket.
        try { close() } catch (_: IOException) {}
    }

    private fun HttpExchange.whole(validator: Pair<String, String>? = null) {
        validator?.let { responseHeaders.add(it.first, it.second) }
        sendResponseHeaders(200, body.size.toLong())
        responseBody.write(body)
        close()
    }

    private fun HttpExchange.rest(from: Int) {
        responseHeaders.add("Content-Range", "bytes $from-${body.size - 1}/${body.size}")
        sendResponseHeaders(206, (body.size - from).toLong())
        responseBody.write(body, from, body.size - from)
        close()
    }

    @Test fun aCutDownloadResumesWithRangeAndIfRangeOnTheSameFile() {
        server.createContext("/file") { exchange ->
            exchange.record()
            if (requests.size == 1) exchange.cutAfter(4, "ETag" to "\"v1\"") else exchange.rest(4)
        }
        val info = download("resumed.bin")
        val recorder = run(info)
        assertEquals(1, recorder.done)
        assertEquals(0, recorder.errors)
        assertEquals(listOf(Triple(null, null, "identity"), Triple("bytes=4-", "\"v1\"", "identity")), requests.toList())
        assertEquals("0123456789", File(info.filepath).readText())
        assertEquals(10L, info.size)
    }

    @Test fun lastModifiedIsUsedWhenThereIsNoStrongETag() {
        val modified = "Sat, 31 Oct 2026 10:00:00 GMT"
        server.createContext("/file") { exchange ->
            exchange.record()
            if (requests.size == 1) {
                exchange.responseHeaders.add("ETag", "W/\"weak\"")
                exchange.cutAfter(3, "Last-Modified" to modified)
            } else exchange.rest(3)
        }
        val info = download("last-modified.bin")
        assertEquals(1, run(info).done)
        assertEquals(Triple("bytes=3-", modified, "identity"), requests[1])
        assertEquals("0123456789", File(info.filepath).readText())
    }

    @Test fun withoutAValidatorTheDownloadStartsOverInsteadOfMixingTwoFiles() {
        server.createContext("/file") { exchange ->
            exchange.record()
            if (requests.size == 1) exchange.cutAfter(6, null) else exchange.whole()
        }
        val info = download("restarted.bin")
        assertEquals(1, run(info).done)
        assertNull("No range without proof of the same file", requests[1].first)
        assertEquals("0123456789", File(info.filepath).readText())
    }

    @Test fun aChangedFileAnsweredWholeReplacesThePartialOne() {
        server.createContext("/file") { exchange ->
            exchange.record()
            // The server ignores the stale If-Range and sends the new file whole.
            if (requests.size == 1) exchange.cutAfter(5, "ETag" to "\"v1\"") else exchange.whole("ETag" to "\"v2\"")
        }
        val info = download("replaced.bin")
        assertEquals(1, run(info).done)
        assertEquals(Triple("bytes=5-", "\"v1\"", "identity"), requests[1])
        assertEquals("0123456789", File(info.filepath).readText())
    }

    @Test fun aPartialAnswerForAnotherOffsetIsNotAppended() {
        server.createContext("/file") { exchange ->
            exchange.record()
            when (requests.size) {
                1 -> exchange.cutAfter(4, "ETag" to "\"v1\"")
                else -> {
                    exchange.responseHeaders.add("Content-Range", "bytes 0-9/10")
                    exchange.sendResponseHeaders(206, 10)
                    exchange.responseBody.write(body)
                    exchange.close()
                }
            }
        }
        val info = download("wrong-range.bin")
        val recorder = run(info)
        assertEquals(1, recorder.errors)
        assertEquals(0, recorder.done)
        assertFalse("A failed download leaves no file", File(info.filepath).exists())
    }

    @Test fun aConnectionThatKeepsFailingWithoutProgressGivesUp() {
        server.createContext("/file") { exchange ->
            exchange.record()
            exchange.cutAfter(0, "ETag" to "\"v1\"")
        }
        val info = download("gives-up.bin")
        val recorder = run(info)
        assertEquals(1, recorder.errors)
        assertEquals(CONNECT_ATTEMPTS, requests.size)
        assertFalse(File(info.filepath).exists())
    }

    @Test fun progressBetweenCutsKeepsTheDownloadGoing() {
        // More cuts than CONNECT_ATTEMPTS, each after new bytes: never counted as consecutive failures.
        server.createContext("/file") { exchange ->
            exchange.record()
            val from = exchange.requestHeaders.getFirst("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            if (from + 1 < body.size) {
                exchange.responseHeaders.add("ETag", "\"v1\"")
                if (from == 0) {
                    exchange.sendResponseHeaders(200, body.size.toLong())
                } else {
                    exchange.responseHeaders.add("Content-Range", "bytes $from-${body.size - 1}/${body.size}")
                    exchange.sendResponseHeaders(206, (body.size - from).toLong())
                }
                exchange.responseBody.write(body, from, 1)
                exchange.responseBody.flush()
                try { exchange.close() } catch (_: IOException) {}
            } else exchange.rest(from)
        }
        val info = download("many-cuts.bin")
        assertEquals(1, run(info).done)
        assertEquals(body.size, requests.size)
        assertEquals("0123456789", File(info.filepath).readText())
    }

    @Test fun aServerThatCannotBeReachedIsTriedAgainThenReported() {
        server.stop(0)
        val info = download("unreachable.bin")
        val recorder = run(info)
        assertEquals(1, recorder.errors)
        assertEquals(0, recorder.done)
    }

    @Test fun certificateAndProtocolErrorsAreNotRetried() {
        assertFalse(FileTransfer.isTransient(java.net.ProtocolException("bad")))
        assertFalse(FileTransfer.isTransient(javax.net.ssl.SSLHandshakeException("x").apply {
            initCause(java.security.cert.CertificateException("untrusted"))
        }))
        assertFalse(FileTransfer.isTransient(javax.net.ssl.SSLPeerUnverifiedException("x")))
        assertTrue(FileTransfer.isTransient(java.net.SocketTimeoutException()))
        assertTrue(FileTransfer.isTransient(java.net.UnknownHostException()))
        assertTrue(FileTransfer.isTransient(java.io.EOFException()))
    }
}
