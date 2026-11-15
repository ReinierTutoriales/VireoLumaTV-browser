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
    fun dialHttpEndpointsServeDescriptionAppStateAndLaunch() {
        val loopbackIf = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress())
        val launches = mutableListOf<CastDialServer.DialLaunchRequest>()
        var stops = 0
        val server = CastDialServer(
            context = ApplicationProvider.getApplicationContext(),
            scope = scope,
            deviceUuid = "11111111-2222-3333-4444-555555555555",
            screenIdProvider = { "screen-xyz" },
            hostInterfaceProvider = { CastDialServer.LocalInterface("127.0.0.1", loopbackIf) },
            onDialLaunch = { launches.add(it) },
            onDialStop = { stops++ },
            launchRunningWindowMs = 150L,
        )
        try {
            assertTrue(server.start())
            val port = server.httpPort
            assertNotNull(port)
            val base = "http://127.0.0.1:$port"

            val desc = request("GET", "$base/ssdp/device-desc.xml")
            assertEquals(200, desc.code)
            assertEquals("$base/apps/", desc.headers["Application-URL"])
            assertTrue(desc.body.contains("<friendlyName>VireoLumaTV</friendlyName>"))

            val appState = request("GET", "$base/apps/YouTube")
            assertEquals(200, appState.code)
            // "stopped" makes the phone POST a launch carrying its theme (YouTube or YouTube Music).
            assertTrue(appState.body.contains("<state>stopped</state>"))
            assertTrue(appState.body.contains("<screenId>screen-xyz</screenId>"))

            val launch = request("POST", "$base/apps/YouTube", "pairingCode=pair-99&v=dQw4w9WgXcQ&theme=music&t=15")
            assertEquals(201, launch.code)
            assertEquals("$base/apps/YouTube/run", launch.headers["LOCATION"])
            assertEquals(1, launches.size)
            assertEquals("pair-99", launches[0].pairingCode)
            assertEquals("dQw4w9WgXcQ", launches[0].videoId)
            assertEquals(15.0, launches[0].startSeconds!!, 0.01)
            assertTrue(launches[0].isMusic)

            // YouTube Music's lounge theme is "m".
            request("POST", "$base/apps/YouTube", "pairingCode=pair-100&theme=m")
            assertTrue(launches[1].isMusic)
            request("POST", "$base/apps/YouTube", "pairingCode=pair-101&theme=cl")
            assertFalse(launches[2].isMusic)
            request("POST", "$base/apps/YouTube?pairingCode=pair-102&theme=m", "")
            assertEquals("pair-102", launches[3].pairingCode)
            assertTrue(launches[3].isMusic)

            val runningState = request("GET", "$base/apps/YouTube")
            assertEquals(200, runningState.code)
            assertTrue(runningState.body.contains("<state>running</state>"))
            assertTrue(runningState.body.contains("<link rel=\"run\" href=\"run\"/>"))

            val runEndpoint = request("GET", "$base/apps/YouTube/run")
            assertEquals(200, runEndpoint.code)
            assertTrue(runEndpoint.body.contains("<state>running</state>"))

            val stop = request("DELETE", "$base/apps/YouTube/run")
            assertEquals(200, stop.code)
            assertEquals(1, stops)

            val stoppedAgain = request("GET", "$base/apps/YouTube")
            assertEquals(200, stoppedAgain.code)
            assertTrue(stoppedAgain.body.contains("<state>stopped</state>"))

            // Auto-resets back to "stopped" after the launch window even without DELETE.
            request("POST", "$base/apps/YouTube", "pairingCode=pair-103&theme=m")
            assertTrue(request("GET", "$base/apps/YouTube").body.contains("<state>running</state>"))
            Thread.sleep(250)
            assertTrue(request("GET", "$base/apps/YouTube").body.contains("<state>stopped</state>"))
        } finally {
            server.close()
        }
    }

    private data class HttpResult(val code: Int, val body: String, val headers: Map<String, String>)

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
