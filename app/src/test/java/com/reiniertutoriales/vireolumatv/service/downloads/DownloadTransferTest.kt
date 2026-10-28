package com.reiniertutoriales.vireolumatv.service.downloads

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class DownloadTransferTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val base = "http://127.0.0.1:${server.address.port}"

    /** Records how the task ended and whether its output was already finished at that moment. */
    private inner class Recorder : Transfer.Callback {
        var done = 0
        var errors = 0
        var errorCode = -1
        var fileAtEnd: Boolean? = null
        override fun onProgress(task: Transfer) {}
        override fun onError(task: Transfer, responseCode: Int, responseMessage: String) {
            errors++; errorCode = responseCode; fileAtEnd = File(task.job.filepath).exists()
        }
        override fun onDone(task: Transfer) { done++; fileAtEnd = File(task.job.filepath).exists() }
    }

    private fun download(name: String, path: String) = Download().apply {
        incognito = true
        url = base + path
        filename = name
        filepath = File(VireoLumaTVApp.instance.cacheDir, name).apply { delete() }.absolutePath
    }

    private fun HttpExchange.reply(code: Int, body: String = "", location: String? = null) {
        location?.let { responseHeaders.add("Location", it) }
        val bytes = body.toByteArray()
        sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.write(bytes)
        close()
    }

    @After fun stop() = server.stop(0)

    @Test fun followsRedirectsAndReportsTheFinishedFile() {
        server.createContext("/start") { it.reply(302, location = "/final") }
        server.createContext("/final") { it.reply(200, "payload") }
        val info = download("redirected.txt", "/start")
        val recorder = Recorder()
        FileTransfer(info, "test", recorder).run()
        assertEquals(1, recorder.done)
        assertEquals(true, recorder.fileAtEnd)
        assertEquals("payload", File(info.filepath).readText())
        assertEquals(7L, info.size)
    }

    @Test fun redirectToAnotherSchemeIsBrokenAndLeavesNoFile() {
        server.createContext("/start") { it.reply(302, location = "ftp://example.test/file") }
        val info = download("ftp.txt", "/start")
        val recorder = Recorder()
        FileTransfer(info, "test", recorder).run()
        assertEquals(1, recorder.errors)
        assertEquals(302, recorder.errorCode)
        assertEquals(Download.SIZE_FAILED, info.size)
        assertEquals(false, recorder.fileAtEnd)
    }

    @Test fun endlessRedirectsStopAfterTheLimit() {
        var hits = 0
        server.createContext("/loop") { hits++; it.reply(302, location = "/loop") }
        val info = download("loop.txt", "/loop")
        val recorder = Recorder()
        FileTransfer(info, "test", recorder).run()
        assertEquals(1, recorder.errors)
        assertEquals(MAX_REDIRECTS + 1, hits)
    }

    @Test fun cancellingMidTransferRemovesThePartialFileBeforeReporting() {
        val info = download("partial.bin", "/unused")
        val recorder = Recorder()
        val input = object : InputStream() {
            var served = 0
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (served++ == 1) info.cancelled = true
                b.fill(1, off, off + len)
                return len
            }
        }
        StreamTransfer(info, input, recorder).run()
        assertEquals(1, recorder.done)
        assertEquals(Download.SIZE_CANCELLED, info.size)
        assertEquals(0L, info.bytesReceived)
        assertEquals("The partial file must be gone when the task reports", false, recorder.fileAtEnd)
        assertNull(info.stream)
    }
}
