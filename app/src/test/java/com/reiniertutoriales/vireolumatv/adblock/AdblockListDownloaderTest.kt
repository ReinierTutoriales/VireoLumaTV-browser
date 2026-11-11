package com.reiniertutoriales.vireolumatv.adblock

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.concurrent.atomic.AtomicInteger

class AdblockListDownloaderTest {
    @Test fun aPartialListCannotReplaceTheCompleteMirror() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val partial = "[Adblock Plus 2.0]\n" + (1..120).joinToString("\n") { "||ads$it.test^" }
        val complete = partial + "\n||extra-ad.test^"
        server.createContext("/partial") { exchange ->
            val body = partial.toByteArray()
            exchange.responseHeaders.add("Content-Range", "bytes 0-${body.size - 1}/${body.size + 100}")
            exchange.sendResponseHeaders(206, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/complete") { exchange ->
            val body = complete.toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals(complete, AdblockListDownloader.download(listOf("$base/partial", "$base/complete"), true))
            assertThrows(java.io.IOException::class.java) {
                AdblockListDownloader.download(listOf("$base/partial"), true)
            }
        } finally { server.stop(0) }
    }

    @Test fun redirectsCannotSilentlyChangeFilterListEndpoint() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "http://untrusted.test/list")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/redirect"
            val error = assertThrows(java.io.IOException::class.java) {
                AdblockListDownloader.download(listOf(url), true)
            }
            assertTrue(error.message!!.contains("No usable filter list endpoint"))
        } finally { server.stop(0) }
    }

    @Test fun externalCleartextFilterEndpointsAreRejected() {
        assertThrows(java.io.IOException::class.java) {
            AdblockListDownloader.download(listOf("http://example.test/list.txt"), true)
        }
    }

    @Test fun loopbackCleartextIsRejectedOutsideUnitTests() {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/list") { exchange ->
            calls.incrementAndGet()
            val body = "[Adblock Plus 2.0]\n||ads.test^".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val previous = System.clearProperty(AdblockListDownloader.TEST_LOOPBACK_PROPERTY)
        try {
            assertThrows(java.io.IOException::class.java) {
                AdblockListDownloader.download(listOf("http://127.0.0.1:${server.address.port}/list"), true)
            }
            assertEquals(0, calls.get())
        } finally {
            previous?.let { System.setProperty(AdblockListDownloader.TEST_LOOPBACK_PROPERTY, it) }
            server.stop(0)
        }
    }

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

    @Test fun gzipResponseIsDecodedAndGzipIsRequested() {
        val rules = "[Adblock Plus 2.0]\n" + (1..120).joinToString("\n") { "||ads$it.test^" }
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(rules.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/gzip") { exchange ->
            assertEquals("gzip", exchange.requestHeaders.getFirst("Accept-Encoding"))
            exchange.responseHeaders.add("Content-Encoding", "gzip")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/gzip"
            assertEquals(rules, AdblockListDownloader.download(listOf(url), true))
        } finally { server.stop(0) }
    }

    @Test fun oversizedGzipCannotReplaceTheValidatedMirror() {
        val rules = "[Adblock Plus 2.0]\n" + (1..120).joinToString("\n") { "||ads$it.test^" }
        val oversized = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(ByteArray(8 * 1024 * 1024 + 1) { 65 }) }
        }.toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/oversized") { exchange ->
            exchange.responseHeaders.add("Content-Encoding", "gzip")
            exchange.sendResponseHeaders(200, oversized.size.toLong())
            exchange.responseBody.use { it.write(oversized) }
        }
        server.createContext("/mirror") { exchange ->
            val bytes = rules.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals(rules, AdblockListDownloader.download(listOf("$base/oversized", "$base/mirror"), true))
        } finally { server.stop(0) }
    }

    @Test fun unsupportedEncodingFallsBackToValidatedMirror() {
        val rules = "[Adblock Plus 2.0]\n" + (1..120).joinToString("\n") { "||ads$it.test^" }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/unsupported") { exchange ->
            val bytes = rules.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Encoding", "br")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/mirror") { exchange ->
            val bytes = rules.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals(rules, AdblockListDownloader.download(listOf("$base/unsupported", "$base/mirror"), true))
        } finally { server.stop(0) }
    }

}
