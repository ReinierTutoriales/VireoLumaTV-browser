package com.reiniertutoriales.vireolumatv.singleton

import android.app.Application
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.utils.IconFinder
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(application = Application::class, sdk = [28])
class FaviconNetworkTest {
    @Test fun repeatedAndConcurrentMissingIconsHaveAFixedRequestBudget() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val config = Config(app.getSharedPreferences("favicon-network-test", 0)).apply { privateBrowsing = true }
        AppContext.init(app, config)
        Favicons.clear()
        val requests = AtomicInteger()
        val manifests = AtomicInteger()
        val html = buildString {
            append("<head><link rel=\"manifest\" href=\"/manifest.json\">")
            repeat(100) { append("<link rel=\"icon\" href=\"/icon${it % 20}.png\" sizes=\"120x120\">") }
            append("</head>")
        }.toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            if (exchange.requestURI.path == "/manifest.json") manifests.incrementAndGet()
            val page = exchange.requestURI.path == "/"
            exchange.sendResponseHeaders(if (page) 200 else 404, if (page) html.size.toLong() else -1)
            exchange.responseBody.use { if (page) it.write(html) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/"
            (1..16).map { async { assertNull(Favicons.get(url)) } }.awaitAll()
            Favicons.trimMemory()
            repeat(10) { assertNull(Favicons.get(url)) }
            assertEquals("One HTML request and at most three distinct icon attempts", 4, requests.get())
            assertEquals("Do not fetch a manifest when HTML supplies usable candidates", 0, manifests.get())
        } finally { server.stop(0); Favicons.clear() }
    }

    @Test fun htmlAndManifestCandidateStorageIsBounded() {
        val extractor = IconFinder()
        val html = "<head>" + (1..1000).joinToString("") { "<link rel=\"icon\" href=\"/$it.png\">" } + "</head>"
        assertEquals(32, extractor.iconsFromDocument(URL("https://test.example"), html.reader().buffered()).first.size)
        val manifest = "{\"icons\":[" + (1..1000).joinToString(",") { "{\"src\":\"/$it.png\"}" } + "]}"
        assertEquals(32, extractor.iconsFromManifest(URL("https://test.example/manifest.json"), manifest.reader()).size)
    }
}
