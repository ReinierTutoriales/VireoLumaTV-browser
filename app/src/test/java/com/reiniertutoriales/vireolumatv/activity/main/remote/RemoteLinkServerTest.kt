package com.reiniertutoriales.vireolumatv.activity.main.remote

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.AddressInput
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class)
class RemoteLinkServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val searchTemplate = "https://duckduckgo.com/?q=%s"
    private val labels =
        RemoteLinkServer.Labels(
            title = "Send to TV",
            hint = "https://...",
            submit = "Open",
            sent = "Sent!",
            invalidUrl = "Invalid URL",
            error = "Error"
        )

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun startReturnsNullWhenNoHostAddressIsAvailable() {
        val server =
            RemoteLinkServer(
                scope = scope,
                searchTemplate = searchTemplate,
                labels = labels,
                hostAddress = null,
                mainDispatcher = Dispatchers.Unconfined
            ) {}
        assertNull(server.start())
        server.close()
    }

    @Test
    fun getRequiresMatchingSessionToken() {
        val server =
            RemoteLinkServer(
                scope = scope,
                searchTemplate = searchTemplate,
                labels = labels,
                hostAddress = "127.0.0.1",
                mainDispatcher = Dispatchers.Unconfined
            ) {}
        try {
            val url = server.start()
            assertNotNull(url)
            val base = url!!.substringBefore("/?")

            val missingToken = request("GET", "$base/")
            assertEquals(403, missingToken.code)

            val wrongToken = request("GET", "$base/?t=invalid")
            assertEquals(403, wrongToken.code)

            val validToken = request("GET", url)
            assertEquals(200, validToken.code)
            assertTrue(validToken.body.contains("<form"))
            assertTrue(validToken.body.contains(server.token))
        } finally {
            server.close()
        }
    }

    @Test
    fun postValidUrlDeliversMultipleLinksWhileServerStaysOpen() {
        val received = mutableListOf<String>()
        val server =
            RemoteLinkServer(
                scope = scope,
                searchTemplate = searchTemplate,
                labels = labels,
                hostAddress = "127.0.0.1",
                mainDispatcher = Dispatchers.Unconfined
            ) { received.add(it) }
        try {
            val url = server.start()!!
            val base = url.substringBefore("/?")
            val first = "https://music.youtube.com/watch?v=123"
            val second = "https://music.youtube.com/watch?v=456"

            val r1 = request("POST", "$base/open", "t=${encode(server.token)}&url=${encode(first)}")
            assertEquals(200, r1.code)
            assertTrue(r1.body.contains("\"ok\":true"))

            val r2 = request(
                method = "POST",
                targetUrl = "$base/open",
                formBody = """{"url":"$second"}""",
                contentType = "application/json; charset=UTF-8",
                bearerToken = server.token
            )
            assertEquals(200, r2.code)
            assertEquals(listOf(first, second), received)
        } finally {
            server.close()
        }
    }

    @Test
    fun preferredPortAndTokenPersistAcrossRestart() {
        val token = RemoteLinkServer.generateToken()
        val first =
            RemoteLinkServer(
                scope = scope,
                searchTemplate = searchTemplate,
                labels = labels,
                token = token,
                preferredPort = 0,
                hostAddress = "127.0.0.1",
                mainDispatcher = Dispatchers.Unconfined
            ) {}
        val firstUrl = first.start()!!
        val boundPort = first.boundPort!!
        first.close()

        val second =
            RemoteLinkServer(
                scope = scope,
                searchTemplate = searchTemplate,
                labels = labels,
                token = token,
                preferredPort = boundPort,
                hostAddress = "127.0.0.1",
                mainDispatcher = Dispatchers.Unconfined
            ) {}
        try {
            val secondUrl = second.start()!!
            assertEquals(firstUrl, secondUrl)
        } finally {
            second.close()
        }
    }

    @Test
    fun postSearchQueryResolvesUsingSearchTemplate() {
        val received = mutableListOf<String>()
        val server =
            RemoteLinkServer(
                scope = scope,
                searchTemplate = searchTemplate,
                labels = labels,
                hostAddress = "127.0.0.1",
                mainDispatcher = Dispatchers.Unconfined
            ) { received.add(it) }
        try {
            val url = server.start()!!
            val base = url.substringBefore("/?")
            val query = "android tv browser"
            val body = "t=${encode(server.token)}&url=${encode(query)}"

            val response = request("POST", "$base/open", body)
            assertEquals(200, response.code)
            assertEquals(listOf(AddressInput.resolve(query, searchTemplate)), received)
        } finally {
            server.close()
        }
    }

    @Test
    fun postRejectsUnsafeSchemesBlankInputAndInvalidToken() {
        val received = mutableListOf<String>()
        val server =
            RemoteLinkServer(
                scope = scope,
                searchTemplate = searchTemplate,
                labels = labels,
                hostAddress = "127.0.0.1",
                mainDispatcher = Dispatchers.Unconfined
            ) { received.add(it) }
        try {
            val url = server.start()!!
            val base = url.substringBefore("/?")

            val badToken = request("POST", "$base/open", "t=wrong&url=${encode("https://example.com")}")
            assertEquals(403, badToken.code)

            for (unsafe in listOf("", "   ", "javascript:alert(1)", "file:///etc/passwd", "intent://scan")) {
                val res = request("POST", "$base/open", "t=${encode(server.token)}&url=${encode(unsafe)}")
                assertEquals("Expected 400 for '$unsafe'", 400, res.code)
            }
            assertTrue(received.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun helpersValidateTokensAndFormParsing() {
        assertTrue(RemoteLinkServer.tokensEqual("abc123", "abc123"))
        assertFalse(RemoteLinkServer.tokensEqual(null, "abc123"))
        assertFalse(RemoteLinkServer.tokensEqual("abc124", "abc123"))

        val parsed = RemoteLinkServer.parseFormUrlEncoded("t=tok1&url=https%3A%2F%2Fexample.com%2Fa+b")
        assertEquals("tok1", parsed["t"])
        assertEquals("https://example.com/a b", parsed["url"])
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun request(
        method: String,
        targetUrl: String,
        formBody: String? = null,
        contentType: String = "application/x-www-form-urlencoded; charset=UTF-8",
        bearerToken: String? = null
    ): HttpResult {
        val conn = URI.create(targetUrl).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        if (bearerToken != null) {
            conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        if (formBody != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", contentType)
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(formBody) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        conn.disconnect()
        return HttpResult(code, text)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
