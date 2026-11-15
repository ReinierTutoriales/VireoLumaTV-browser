package com.reiniertutoriales.vireolumatv.activity.main.cast

import androidx.test.core.app.ApplicationProvider
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.cast.DlnaMediaRendererServer.DlnaCommand
import com.reiniertutoriales.vireolumatv.activity.main.cast.DlnaMediaRendererServer.MediaKind
import com.reiniertutoriales.vireolumatv.activity.main.cast.DlnaMediaRendererServer.PlaybackSnapshot
import com.reiniertutoriales.vireolumatv.activity.main.cast.DlnaMediaRendererServer.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
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
class DlnaMediaRendererServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun helpersDetectMediaKindsAndFormatTime() {
        assertEquals(MediaKind.IMAGE, DlnaMediaRendererServer.detectMediaKind("http://192.168.1.5/photo.jpg", ""))
        assertEquals(MediaKind.AUDIO, DlnaMediaRendererServer.detectMediaKind("http://192.168.1.5/track.flac", ""))
        assertEquals(MediaKind.VIDEO, DlnaMediaRendererServer.detectMediaKind("http://192.168.1.5/stream", "<upnp:class>object.item.videoItem</upnp:class>"))
        assertEquals(95, DlnaMediaRendererServer.parseHmsSeconds("00:01:35"))
        assertEquals("01:02:03", DlnaMediaRendererServer.formatHmsSeconds(3723))
    }

    @Test
    fun servesUpnpDescriptionsAndHandlesSoapControlActions() {
        val loopbackIf = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress())
        val commands = mutableListOf<DlnaCommand>()
        val snapshot = PlaybackSnapshot(
            transportState = TransportState.PLAYING,
            currentUri = "http://192.168.1.5:8080/song.mp3",
            positionSeconds = 30,
            durationSeconds = 180,
            volume = 80,
            muted = false,
        )
        val server = DlnaMediaRendererServer(
            context = ApplicationProvider.getApplicationContext(),
            scope = scope,
            deviceUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            hostInterfaceProvider = { CastDialServer.LocalInterface("127.0.0.1", loopbackIf) },
            mainDispatcher = Dispatchers.Unconfined,
            stateProvider = { snapshot },
            onCommand = { commands.add(it) },
        )
        try {
            assertTrue(server.start())
            val base = "http://127.0.0.1:${server.httpPort}"

            val desc = http("GET", "$base/dlna/device.xml")
            assertEquals(200, desc.code)
            assertTrue(desc.body.contains("urn:schemas-upnp-org:device:MediaRenderer:1"))
            assertTrue(desc.body.contains("<friendlyName>VireoLumaTV</friendlyName>"))

            assertEquals(200, http("GET", "$base/dlna/scpd/avtransport.xml").code)
            assertEquals(200, http("GET", "$base/dlna/scpd/renderingcontrol.xml").code)
            assertEquals(200, http("GET", "$base/dlna/scpd/connectionmanager.xml").code)

            val loadSoap = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>http://192.168.1.5:8080/song.mp3?a=1&amp;b=2</CurrentURI>
                  <CurrentURIMetaData>&lt;DIDL-Lite&gt;&lt;item&gt;&lt;dc:title&gt;Mi Canción&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.audioItem&lt;/upnp:class&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>
                </u:SetAVTransportURI>
            </s:Body></s:Envelope>"""
            val loadRes = soap("$base/dlna/control/avtransport", "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI", loadSoap)
            assertEquals(200, loadRes.code)

            val unsafeSoap = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>file:///etc/passwd</CurrentURI>
                  <CurrentURIMetaData></CurrentURIMetaData>
                </u:SetAVTransportURI>
            </s:Body></s:Envelope>"""
            assertEquals(500, soap("$base/dlna/control/avtransport", "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI", unsafeSoap).code)

            assertEquals(200, soap("$base/dlna/control/avtransport", "urn:schemas-upnp-org:service:AVTransport:1#Play", "<s:Envelope/>").code)
            assertEquals(200, soap("$base/dlna/control/avtransport", "urn:schemas-upnp-org:service:AVTransport:1#Pause", "<s:Envelope/>").code)
            assertEquals(200, soap("$base/dlna/control/avtransport", "urn:schemas-upnp-org:service:AVTransport:1#Seek", "<Target>00:01:30</Target>").code)
            assertEquals(200, soap("$base/dlna/control/avtransport", "urn:schemas-upnp-org:service:AVTransport:1#Stop", "<s:Envelope/>").code)

            val posRes = soap("$base/dlna/control/avtransport", "urn:schemas-upnp-org:service:AVTransport:1#GetPositionInfo", "<s:Envelope/>")
            assertEquals(200, posRes.code)
            assertTrue(posRes.body.contains("<RelTime>00:00:30</RelTime>"))
            assertTrue(posRes.body.contains("<TrackDuration>00:03:00</TrackDuration>"))

            assertEquals(200, soap("$base/dlna/control/renderingcontrol", "urn:schemas-upnp-org:service:RenderingControl:1#SetVolume", "<DesiredVolume>75</DesiredVolume>").code)
            assertEquals(200, soap("$base/dlna/control/renderingcontrol", "urn:schemas-upnp-org:service:RenderingControl:1#SetMute", "<DesiredMute>1</DesiredMute>").code)

            val proto = soap("$base/dlna/control/connectionmanager", "urn:schemas-upnp-org:service:ConnectionManager:1#GetProtocolInfo", "<s:Envelope/>")
            assertEquals(200, proto.code)
            assertTrue(proto.body.contains("video/mp4"))
            assertTrue(proto.body.contains("image/jpeg"))

            assertEquals(
                listOf(
                    DlnaCommand.Load("http://192.168.1.5:8080/song.mp3?a=1&b=2", "Mi Canción", MediaKind.AUDIO),
                    DlnaCommand.Play,
                    DlnaCommand.Pause,
                    DlnaCommand.Seek(90),
                    DlnaCommand.Stop,
                    DlnaCommand.SetVolume(75),
                    DlnaCommand.SetMute(true),
                ),
                commands,
            )
        } finally {
            server.close()
        }
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun soap(url: String, soapAction: String, body: String): HttpResult =
        http("POST", url, body, soapAction)

    private fun http(method: String, targetUrl: String, body: String? = null, soapAction: String? = null): HttpResult {
        val conn = URI.create(targetUrl).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        if (soapAction != null) conn.setRequestProperty("SOAPAction", "\"$soapAction\"")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        conn.disconnect()
        return HttpResult(code, text)
    }
}
