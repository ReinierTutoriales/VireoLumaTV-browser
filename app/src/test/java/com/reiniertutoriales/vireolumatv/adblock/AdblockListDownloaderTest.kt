package com.reiniertutoriales.vireolumatv.adblock

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class AdblockListDownloaderTest {
    @Test fun deniedOrInvalidPrimaryUsesValidatedMirrorWithIdentifiedUserAgent() {
        val primaryCalls = AtomicInteger()
        val mirrorCalls = AtomicInteger()
        var rejectPrimary = true
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val rules = "[Adblock Plus 2.0]\n" + (1..120).joinToString("\n") { "||ads$it.test^" }
        server.createContext("/primary") { exchange ->
            primaryCalls.incrementAndGet()
            val body = "<html>Forbidden</html>".toByteArray()
            exchange.sendResponseHeaders(if (rejectPrimary) 403 else 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/mirror") { exchange ->
            mirrorCalls.incrementAndGet()
            assertTrue(exchange.requestHeaders.getFirst("User-Agent").startsWith("VireoLumaTV/"))
            val body = rules.toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            repeat(2) {
                assertEquals(rules, AdblockListDownloader.download(listOf("$base/primary", "$base/mirror"), true))
                rejectPrimary = false
            }
            assertEquals(2, primaryCalls.get())
            assertEquals(2, mirrorCalls.get())
        } finally { server.stop(0) }
    }

    @Test fun savedCopyIsRevalidatedWithEtagAndHttp304SkipsTheDownload() {
        val rules = "[Adblock Plus 2.0]\n" + (1..120).joinToString("\n") { "||ads$it.test^" }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/list") { exchange ->
            if (exchange.requestHeaders.getFirst("If-None-Match") == "\"v1\"") {
                exchange.sendResponseHeaders(304, -1)
                exchange.close()
                return@createContext
            }
            exchange.responseHeaders.add("ETag", "\"v1\"")
            exchange.responseHeaders.add("Last-Modified", "Wed, 07 Oct 2026 19:12:42 GMT")
            val body = rules.toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/list"
            val first = AdblockListDownloader.fetch(listOf(url), true, null) as AdblockListDownloader.Result.Downloaded
            assertEquals(rules, first.text)
            assertEquals("\"v1\"", first.validators?.etag)
            assertSame(AdblockListDownloader.Result.NotModified,
                AdblockListDownloader.fetch(listOf(url), true, first.validators))
            // Validators of another endpoint (a mirror) are never sent.
            val foreign = first.validators!!.copy(url = "$url-mirror")
            assertTrue(AdblockListDownloader.fetch(listOf(url), true, foreign) is AdblockListDownloader.Result.Downloaded)
        } finally { server.stop(0) }
    }
}
