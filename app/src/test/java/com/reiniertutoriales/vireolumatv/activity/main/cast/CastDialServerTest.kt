package com.reiniertutoriales.vireolumatv.activity.main.cast

import androidx.test.core.app.ApplicationProvider
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class)
class CastDialServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun ssdpSearchTargetExtractionAndResponseFormatting() {
        val search = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n"
        assertEquals("urn:dial-multiscreen-org:service:dial:1", CastDialServer.extractSearchTarget(search))
        assertNull(CastDialServer.extractSearchTarget("NOTIFY * HTTP/1.1\r\nST: ssdp:all\r\n\r\n"))

        val reply = CastDialServer.buildSsdpResponse(
            st = "urn:dial-multiscreen-org:service:dial:1",
            location = "http://192.168.1.20:12345/ssdp/device-desc.xml",
            usn = "uuid:test-uuid::urn:dial-multiscreen-org:service:dial:1",
        )
        assertTrue(reply.contains("LOCATION: http://192.168.1.20:12345/ssdp/device-desc.xml"))
        assertTrue(reply.contains("ST: urn:dial-multiscreen-org:service:dial:1"))
    }

    @Test
    fun onlyPhoneAppsAndYouTubeMayLaunch() {
        assertTrue(CastDialServer.isAllowedOrigin(null))
        assertTrue(CastDialServer.isAllowedOrigin("package:com.google.ios.youtubemusic"))
        assertTrue(CastDialServer.isAllowedOrigin("https://www.youtube.com"))
        assertTrue(CastDialServer.isAllowedOrigin("https://music.youtube.com"))
        assertFalse(CastDialServer.isAllowedOrigin("https://evil.example"))
        assertFalse(CastDialServer.isAllowedOrigin("https://youtube.com.evil.example"))
        assertFalse(CastDialServer.isAllowedOrigin("http://www.youtube.com"))
        assertFalse(CastDialServer.isAllowedOrigin("null"))
    }

    @Test
    fun ssdpAnswersTheSameSearchTargetsAsPeerDial() {
        val uuid = "11111111-2222-3333-4444-555555555555"
        assertEquals(
            listOf(
                "urn:dial-multiscreen-org:service:dial:1",
                "urn:dial-multiscreen-org:device:dial:1",
                "upnp:rootdevice",
                "ssdp:all",
                "uuid:$uuid",
            ),
            CastDialServer.serviceTypes(uuid),
        )
    }

    @Test
    fun dialServesARunningYouTubeAppAndForwardsLaunchThemeAndPairingCode() {
        val loopbackIf = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress())
        val launches = mutableListOf<CastDialServer.DialLaunchRequest>()
        val server = CastDialServer(
            context = ApplicationProvider.getApplicationContext(),
            scope = scope,
            deviceUuid = "11111111-2222-3333-4444-555555555555",
            hostInterfaceProvider = { CastDialServer.LocalInterface("127.0.0.1", loopbackIf) },
            onDialLaunch = { launches.add(it); it.theme == "m" },
        )
        try {
            assertTrue(server.start())
            val port = server.httpPort
            assertNotNull(port)
            val base = "http://127.0.0.1:$port"

            val desc = request("GET", "$base/ssdp/device-desc.xml")
            assertEquals(200, desc.code)
            // No trailing slash: the phone adds "/YouTube" itself.
            assertEquals("$base/apps", desc.headers["Application-URL"])
            assertTrue(desc.body.contains("<friendlyName>VireoLumaTV</friendlyName>"))
            assertTrue(desc.body.contains("<URLBase>$base</URLBase>"))

            val appState = request("GET", "$base/apps/YouTube")
            assertEquals(200, appState.code)
            assertTrue(appState.body.contains("<state>running</state>"))
            assertTrue(appState.body.contains("<options allowStop=\"false\"/>"))
            assertTrue(appState.body.contains("<link rel=\"run\" href=\""))
            // Without a screenId the phone must POST a launch that says which app is casting.
            assertFalse(appState.body.contains("screenId"))
            assertEquals(200, request("GET", "$base/apps//YouTube").code)

            val music = request("POST", "$base/apps/YouTube", "pairingCode=pair-1&theme=m&v=abc")
            assertEquals(200, music.code)
            assertTrue(music.headers["LOCATION"].orEmpty().startsWith("$base/apps/YouTube/"))
            assertEquals(CastDialServer.DialLaunchRequest("pair-1", "m"), launches[0])

            val youtube = request("POST", "$base/apps/YouTube?pairingCode=pair-2&theme=cl", "")
            assertEquals(200, youtube.code)
            assertNull(youtube.headers["LOCATION"])
            assertEquals(CastDialServer.DialLaunchRequest("pair-2", "cl"), launches[1])

            assertEquals(405, request("DELETE", "$base/apps/YouTube/run").code)
            // A web page on the network cannot pair with the TV.
            assertEquals(403, rawPostStatus(port!!, "/apps/YouTube", "pairingCode=pair-3&theme=m", "https://evil.example"))
            assertEquals(2, launches.size)
            assertEquals(204, request("GET", "$base/apps").code)
        } finally {
            server.close()
        }
    }

    private data class HttpResult(val code: Int, val body: String, val headers: Map<String, String>)

    /** HttpURLConnection will not send Origin, so a web page's POST is written by hand. */
    private fun rawPostStatus(port: Int, path: String, body: String, origin: String): Int {
        java.net.Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 2000
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val head = "POST $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nOrigin: $origin\r\n" +
                "Content-Type: text/plain\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply { write(head.toByteArray(StandardCharsets.UTF_8)); write(bytes); flush() }
            val status = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).readLine().orEmpty()
            return status.split(' ').getOrNull(1)?.toIntOrNull() ?: -1
        }
    }

    private fun request(method: String, targetUrl: String, formBody: String? = null): HttpResult {
        val conn = URI.create(targetUrl).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        if (formBody != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(formBody) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        val headers = conn.headerFields.mapNotNull { (k, v) ->
            if (k != null && v.isNotEmpty()) k to v.first() else null
        }.toMap()
        conn.disconnect()
        return HttpResult(code, text, headers)
    }
}
