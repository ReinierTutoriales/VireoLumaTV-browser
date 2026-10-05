package com.reiniertutoriales.vireolumatv.service.downloads

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class DownloadBackoffCancellationTest {
    @Test fun cancellationDuringUnavailableResponseNeverOpensAnotherConnection() {
        val received = CountDownLatch(1)
        val requests = AtomicInteger()
        val errors = AtomicInteger()
        val done = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.add("Retry-After", "30")
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
            received.countDown()
        }
        server.start()
        val download = Download().apply {
            incognito = true
            url = "http://127.0.0.1:${server.address.port}/file"
            filename = "cancel-backoff.bin"
            filepath = File(VireoLumaTVApp.instance.cacheDir, filename).absolutePath
        }
        val callback = object : DownloadTask.Callback {
            override fun onProgress(task: DownloadTask) {}
            override fun onError(task: DownloadTask, responseCode: Int, responseMessage: String) { errors.incrementAndGet() }
            override fun onDone(task: DownloadTask) { done.incrementAndGet() }
        }
        val worker = Thread(FileDownloadTask(download, "network-test", callback))
        try {
            worker.start()
            assertTrue(received.await(5, TimeUnit.SECONDS))
            download.cancelled = true
            worker.join(3_000)
            assertFalse("Cancelled backoff must not wait 30 seconds", worker.isAlive)
            assertEquals(1, requests.get())
            assertEquals(0, errors.get())
            assertEquals(1, done.get())
            assertEquals(Download.CANCELLED_MARK, download.size)
        } finally {
            download.cancelled = true
            worker.interrupt()
            worker.join(1_000)
            server.stop(0)
        }
    }
}
