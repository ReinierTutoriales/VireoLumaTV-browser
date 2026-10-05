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
}
