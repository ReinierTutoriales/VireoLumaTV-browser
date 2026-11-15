package com.reiniertutoriales.vireolumatv.activity.main.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.activity.main.ExternalWebNavigation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID

/**
 * Local UPnP/DLNA MediaRenderer (SSDP + HTTP/SOAP) that advertises VireoLumaTV on the LAN
 * for video, music, and photo casting apps while the browser is open in the foreground.
 */
internal class DlnaMediaRendererServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deviceUuid: String,
    private val friendlyName: String = "VireoLumaTV",
    private val hostInterfaceProvider: () -> CastDialServer.LocalInterface? = CastDialServer::findLocalInterface,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val stateProvider: () -> PlaybackSnapshot,
    private val onCommand: (DlnaCommand) -> Unit,
) : Closeable {

    enum class MediaKind { VIDEO, AUDIO, IMAGE }

    enum class TransportState { STOPPED, PLAYING, PAUSED_PLAYBACK, TRANSITIONING }

    data class PlaybackSnapshot(
        val transportState: TransportState = TransportState.STOPPED,
        val currentUri: String = "",
        val positionSeconds: Int = 0,
        val durationSeconds: Int = 0,
        val volume: Int = 100,
        val muted: Boolean = false,
    )

    sealed class DlnaCommand {
        data class Load(val uri: String, val title: String, val kind: MediaKind) : DlnaCommand()
        data object Play : DlnaCommand()
        data object Pause : DlnaCommand()
        data object Stop : DlnaCommand()
        data class Seek(val positionSeconds: Int) : DlnaCommand()
        data class SetVolume(val volume: Int) : DlnaCommand()
        data class SetMute(val muted: Boolean) : DlnaCommand()
    }

    private val serverDispatcher = Dispatchers.IO.limitedParallelism(4)
    private var serverSocket: ServerSocket? = null
    private var ssdpSocket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var httpJob: Job? = null
    private var ssdpJob: Job? = null
    @Volatile private var closed = false
    @Volatile private var currentUri: String = ""

    val httpPort: Int?
        get() = serverSocket?.localPort

    fun start(): Boolean {
        if (closed) return false
        if (serverSocket != null) return true
        val localIf = hostInterfaceProvider() ?: return false
        val http = try {
            ServerSocket(0)
        } catch (error: Exception) {
            Log.w(TAG, "Could not bind DLNA HTTP socket", error)
            return false
        }
        serverSocket = http
        acquireMulticastLock()
        startHttpAcceptLoop(http)
        startSsdpLoop(localIf)
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        sendByeBye()
        httpJob?.cancel()
        httpJob = null
        ssdpJob?.cancel()
        ssdpJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { ssdpSocket?.close() }
        ssdpSocket = null
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("VireoLumaTV-DLNA")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
    }

    private fun startHttpAcceptLoop(http: ServerSocket) {
        httpJob = scope.launch(serverDispatcher) {
            while (isActive && !closed && !http.isClosed) {
                val client = try {
                    http.accept()
                } catch (_: Exception) {
                    break
                }
                launch(serverDispatcher) {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                        handleHttpClient(client)
                    } ?: runCatching { client.close() }
                }
            }
        }
    }

    private fun startSsdpLoop(localIf: CastDialServer.LocalInterface) {
        ssdpJob = scope.launch(serverDispatcher) {
            val socket = try {
                MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(SSDP_PORT))
                    networkInterface = localIf.networkInterface
                    joinGroup(InetSocketAddress(InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT), localIf.networkInterface)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Could not join DLNA SSDP multicast", error)
                return@launch
            }
            ssdpSocket = socket
            sendAlive(socket, localIf.hostAddress)
            val buf = ByteArray(2048)
            while (isActive && !closed && !socket.isClosed) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: Exception) {
                    break
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                val st = CastDialServer.extractSearchTarget(text) ?: continue
                if (st in SEARCH_TARGETS || st == SSDP_ALL) {
                    val port = serverSocket?.localPort ?: continue
                    val location = "http://${localIf.hostAddress}:$port/dlna/device.xml"
                    val replyTargets = if (st == SSDP_ALL) ADVERTISED_TYPES else listOf(st)
                    for (target in replyTargets) {
                        val usn = if (target.startsWith("uuid:")) target else "uuid:$deviceUuid::$target"
                        val responseBytes = CastDialServer.buildSsdpResponse(target, location, usn).toByteArray(Charsets.UTF_8)
                        val reply = DatagramPacket(responseBytes, responseBytes.size, packet.address, packet.port)
                        runCatching { socket.send(reply) }
                    }
                }
            }
        }
    }

    private fun sendAlive(socket: MulticastSocket, host: String) {
        val port = serverSocket?.localPort ?: return
        val location = "http://$host:$port/dlna/device.xml"
        val group = runCatching { InetAddress.getByName(SSDP_ADDRESS) }.getOrNull() ?: return
        for (nt in ADVERTISED_TYPES + "uuid:$deviceUuid") {
            val usn = if (nt.startsWith("uuid:")) nt else "uuid:$deviceUuid::$nt"
            val msg = CastDialServer.buildSsdpNotify(nt = nt, usn = usn, location = location, nts = "ssdp:alive")
            val bytes = msg.toByteArray(Charsets.UTF_8)
            runCatching { socket.send(DatagramPacket(bytes, bytes.size, group, SSDP_PORT)) }
        }
    }

    private fun sendByeBye() {
        val socket = ssdpSocket ?: return
        val group = runCatching { InetAddress.getByName(SSDP_ADDRESS) }.getOrNull() ?: return
        for (nt in ADVERTISED_TYPES + "uuid:$deviceUuid") {
            val usn = if (nt.startsWith("uuid:")) nt else "uuid:$deviceUuid::$nt"
            val msg = CastDialServer.buildSsdpNotify(nt = nt, usn = usn, location = "", nts = "ssdp:byebye")
            val bytes = msg.toByteArray(Charsets.UTF_8)
            runCatching { socket.send(DatagramPacket(bytes, bytes.size, group, SSDP_PORT)) }
        }
    }

    private fun handleHttpClient(socket: Socket) {
        socket.use { client ->
            runCatching {
                client.soTimeout = SOCKET_TIMEOUT_MS
                val input = client.getInputStream()
                val output = client.getOutputStream()
                val request = readHttpRequest(client, input) ?: return
                when {
                    request.method == "GET" && request.path == "/dlna/device.xml" -> {
                        writeResponse(output, 200, "OK", "text/xml; charset=utf-8", deviceXml(deviceUuid, friendlyName))
                    }
                    request.method == "GET" && request.path == "/dlna/scpd/avtransport.xml" -> {
                        writeResponse(output, 200, "OK", "text/xml; charset=utf-8", AV_TRANSPORT_SCPD)
                    }
                    request.method == "GET" && request.path == "/dlna/scpd/renderingcontrol.xml" -> {
                        writeResponse(output, 200, "OK", "text/xml; charset=utf-8", RENDERING_CONTROL_SCPD)
                    }
                    request.method == "GET" && request.path == "/dlna/scpd/connectionmanager.xml" -> {
                        writeResponse(output, 200, "OK", "text/xml; charset=utf-8", CONNECTION_MANAGER_SCPD)
                    }
                    request.method == "POST" && request.path == "/dlna/control/avtransport" -> {
                        handleAvTransportAction(request, output)
                    }
                    request.method == "POST" && request.path == "/dlna/control/renderingcontrol" -> {
                        handleRenderingControlAction(request, output)
                    }
                    request.method == "POST" && request.path == "/dlna/control/connectionmanager" -> {
                        handleConnectionManagerAction(request, output)
                    }
                    (request.method == "SUBSCRIBE" || request.method == "UNSUBSCRIBE") &&
                        request.path in EVENT_PATHS -> {
                        handleGenaSubscription(request, output)
                    }
                    else -> writeResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not found")
                }
            }.onFailure { error ->
                if (!closed) Log.d(TAG, "DLNA client request ended", error)
            }
        }
    }

    private fun handleAvTransportAction(request: HttpRequest, output: OutputStream) {
        val action = extractSoapAction(request.soapAction)
        val snap = stateProvider()
        when (action) {
            "SetAVTransportURI" -> {
                val rawUri = extractXmlTag(request.body, "CurrentURI")?.let(::xmlUnescape).orEmpty().trim()
                val parsedUri = runCatching { rawUri.toUri() }.getOrNull()
                if (!ExternalWebNavigation.isAllowed(parsedUri)) {
                    writeResponse(output, 500, "Internal Server Error", "text/xml; charset=utf-8", soapFault(716, "Resource not found"))
                    return
                }
                val metaRaw = extractXmlTag(request.body, "CurrentURIMetaData")?.let(::xmlUnescape).orEmpty()
                val title = extractXmlTag(metaRaw, "dc:title")
                    ?: extractXmlTag(metaRaw, "title")
                    ?: rawUri.substringAfterLast('/').substringBefore('?').ifBlank { friendlyName }
                val kind = detectMediaKind(rawUri, metaRaw)
                currentUri = rawUri
                dispatchCommand(DlnaCommand.Load(rawUri, title, kind))
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(AV_TRANSPORT_TYPE, "SetAVTransportURI", ""))
            }
            "Play" -> {
                dispatchCommand(DlnaCommand.Play)
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(AV_TRANSPORT_TYPE, "Play", ""))
            }
            "Pause" -> {
                dispatchCommand(DlnaCommand.Pause)
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(AV_TRANSPORT_TYPE, "Pause", ""))
            }
            "Stop" -> {
                dispatchCommand(DlnaCommand.Stop)
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(AV_TRANSPORT_TYPE, "Stop", ""))
            }
            "Seek" -> {
                val target = extractXmlTag(request.body, "Target").orEmpty()
                val seconds = parseHmsSeconds(target) ?: 0
                dispatchCommand(DlnaCommand.Seek(seconds))
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(AV_TRANSPORT_TYPE, "Seek", ""))
            }
            "GetTransportInfo" -> {
                val body = "<CurrentTransportState>${snap.transportState.name}</CurrentTransportState>" +
                    "<CurrentTransportStatus>OK</CurrentTransportStatus>" +
                    "<CurrentSpeed>1</CurrentSpeed>"
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(AV_TRANSPORT_TYPE, "GetTransportInfo", body))
            }
            "GetPositionInfo" -> {
                val dur = formatHmsSeconds(snap.durationSeconds)
                val pos = formatHmsSeconds(snap.positionSeconds)
                val uri = xmlEscape(snap.currentUri.ifEmpty { currentUri })
                val body = "<Track>1</Track><TrackDuration>$dur</TrackDuration><TrackMetaData></TrackMetaData>" +
                    "<TrackURI>$uri</TrackURI><RelTime>$pos</RelTime><AbsTime>$pos</AbsTime>" +
                    "<RelCount>2147483647</RelCount><AbsCount>2147483647</AbsCount>"
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(AV_TRANSPORT_TYPE, "GetPositionInfo", body))
            }
            "GetMediaInfo" -> {
                val dur = formatHmsSeconds(snap.durationSeconds)
                val uri = xmlEscape(snap.currentUri.ifEmpty { currentUri })
                val body = "<NrTracks>1</NrTracks><MediaDuration>$dur</MediaDuration>" +
                    "<CurrentURI>$uri</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>" +
                    "<NextURI></NextURI><NextURIMetaData></NextURIMetaData>" +
                    "<PlayMedium>NETWORK</PlayMedium><RecordMedium>NOT_IMPLEMENTED</RecordMedium><WriteStatus>NOT_IMPLEMENTED</WriteStatus>"
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(AV_TRANSPORT_TYPE, "GetMediaInfo", body))
            }
            "GetCurrentTransportActions" -> {
                val body = "<Actions>Play,Pause,Stop,Seek</Actions>"
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(AV_TRANSPORT_TYPE, "GetCurrentTransportActions", body))
            }
            else -> {
                writeResponse(output, 500, "Internal Server Error", "text/xml; charset=utf-8", soapFault(401, "Invalid Action"))
            }
        }
    }

    private fun handleRenderingControlAction(request: HttpRequest, output: OutputStream) {
        val action = extractSoapAction(request.soapAction)
        val snap = stateProvider()
        when (action) {
            "GetVolume" -> {
                val body = "<CurrentVolume>${snap.volume.coerceIn(0, 100)}</CurrentVolume>"
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(RENDERING_CONTROL_TYPE, "GetVolume", body))
            }
            "SetVolume" -> {
                val desired = extractXmlTag(request.body, "DesiredVolume")?.trim()?.toIntOrNull()?.coerceIn(0, 100) ?: 100
                dispatchCommand(DlnaCommand.SetVolume(desired))
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(RENDERING_CONTROL_TYPE, "SetVolume", ""))
            }
            "GetMute" -> {
                val muteVal = if (snap.muted) "1" else "0"
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(RENDERING_CONTROL_TYPE, "GetMute", "<CurrentMute>$muteVal</CurrentMute>"))
            }
            "SetMute" -> {
                val desired = extractXmlTag(request.body, "DesiredMute")?.trim()
                val muted = desired == "1" || desired.equals("true", ignoreCase = true)
                dispatchCommand(DlnaCommand.SetMute(muted))
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(RENDERING_CONTROL_TYPE, "SetMute", ""))
            }
            else -> {
                writeResponse(output, 500, "Internal Server Error", "text/xml; charset=utf-8", soapFault(401, "Invalid Action"))
            }
        }
    }

    private fun handleConnectionManagerAction(request: HttpRequest, output: OutputStream) {
        val action = extractSoapAction(request.soapAction)
        when (action) {
            "GetProtocolInfo" -> {
                val body = "<Source></Source><Sink>${xmlEscape(SINK_PROTOCOLS)}</Sink>"
                writeResponse(output, 200, "OK", "text/xml; charset=utf-8", soapResponse(CONNECTION_MANAGER_TYPE, "GetProtocolInfo", body))
            }
            else -> {
                writeResponse(output, 500, "Internal Server Error", "text/xml; charset=utf-8", soapFault(401, "Invalid Action"))
            }
        }
    }

    private fun handleGenaSubscription(request: HttpRequest, output: OutputStream) {
        if (request.method == "UNSUBSCRIBE") {
            writeResponse(output, 200, "OK", "text/plain; charset=utf-8", "")
            return
        }
        val sid = request.sid.ifBlank { "uuid:${UUID.randomUUID()}" }
        writeResponse(
            output = output,
            status = 200,
            reason = "OK",
            contentType = "text/plain; charset=utf-8",
            body = "",
            extraHeaders = mapOf(
                "SID" to sid,
                "TIMEOUT" to "Second-1800",
            ),
        )
    }

    private fun dispatchCommand(command: DlnaCommand) {
        if (closed) return
        scope.launch(mainDispatcher) {
            if (!closed) onCommand(command)
        }
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val soapAction: String,
        val sid: String,
        val body: String,
    )

    internal companion object {
        private const val TAG = "DlnaMediaRendererServer"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_ALL = "ssdp:all"
        private const val MEDIA_RENDERER_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1"
        private const val AV_TRANSPORT_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val RENDERING_CONTROL_TYPE = "urn:schemas-upnp-org:service:RenderingControl:1"
        private const val CONNECTION_MANAGER_TYPE = "urn:schemas-upnp-org:service:ConnectionManager:1"
        private val EVENT_PATHS = setOf(
            "/dlna/event/avtransport",
            "/dlna/event/renderingcontrol",
            "/dlna/event/connectionmanager",
        )
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val MAX_BODY_BYTES = 32 * 1024

        private val ADVERTISED_TYPES = listOf(
            "upnp:rootdevice",
            MEDIA_RENDERER_TYPE,
            AV_TRANSPORT_TYPE,
            RENDERING_CONTROL_TYPE,
            CONNECTION_MANAGER_TYPE,
        )
        private val SEARCH_TARGETS = ADVERTISED_TYPES.toSet()

        private const val SINK_PROTOCOLS =
            "http-get:*:video/mp4:*,http-get:*:video/x-matroska:*,http-get:*:video/webm:*," +
                "http-get:*:audio/mpeg:*,http-get:*:audio/mp4:*,http-get:*:audio/flac:*," +
                "http-get:*:image/jpeg:*,http-get:*:image/png:*,http-get:*:image/webp:*,http-get:*:*:*"

        internal fun detectMediaKind(uri: String, metaXml: String): MediaKind {
            val lowerMeta = metaXml.lowercase(Locale.ROOT)
            when {
                "object.item.imageitem" in lowerMeta || "image/" in lowerMeta -> return MediaKind.IMAGE
                "object.item.audioitem" in lowerMeta || "audio/" in lowerMeta -> return MediaKind.AUDIO
                "object.item.videoitem" in lowerMeta || "video/" in lowerMeta -> return MediaKind.VIDEO
            }
            val path = uri.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
            return when {
                path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
                    path.endsWith(".webp") || path.endsWith(".gif") -> MediaKind.IMAGE
                path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac") ||
                    path.endsWith(".flac") || path.endsWith(".ogg") || path.endsWith(".wav") -> MediaKind.AUDIO
                else -> MediaKind.VIDEO
            }
        }

        internal fun parseHmsSeconds(value: String): Int? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split(':')
            if (parts.size == 3) {
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                val s = parts[2].substringBefore('.').toIntOrNull() ?: return null
                return (h * 3600 + m * 60 + s).coerceAtLeast(0)
            }
            return trimmed.substringBefore('.').toIntOrNull()?.coerceAtLeast(0)
        }

        internal fun formatHmsSeconds(totalSeconds: Int): String {
            val safe = totalSeconds.coerceAtLeast(0)
            val h = safe / 3600
            val m = (safe % 3600) / 60
            val s = safe % 60
            return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
        }

        internal fun extractSoapAction(header: String): String {
            val clean = header.trim().trim('"')
            return clean.substringAfter('#').trim()
        }

        internal fun extractXmlTag(xml: String, tag: String): String? {
            val regex = Regex("<(?:[a-zA-Z0-9_-]+:)?$tag(?:\\s[^>]*)?>(.*?)</(?:[a-zA-Z0-9_-]+:)?$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            return regex.find(xml)?.groupValues?.getOrNull(1)
        }

        internal fun deviceXml(uuid: String, friendlyName: String): String =
            """<?xml version="1.0" encoding="UTF-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <device>
    <deviceType>$MEDIA_RENDERER_TYPE</deviceType>
    <friendlyName>${xmlEscape(friendlyName)}</friendlyName>
    <manufacturer>VireoLumaTV</manufacturer>
    <modelName>VireoLumaTV</modelName>
    <UDN>uuid:${xmlEscape(uuid)}</UDN>
    <serviceList>
      <service>
        <serviceType>$AV_TRANSPORT_TYPE</serviceType>
        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
        <SCPDURL>/dlna/scpd/avtransport.xml</SCPDURL>
        <controlURL>/dlna/control/avtransport</controlURL>
        <eventSubURL>/dlna/event/avtransport</eventSubURL>
      </service>
      <service>
        <serviceType>$RENDERING_CONTROL_TYPE</serviceType>
        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
        <SCPDURL>/dlna/scpd/renderingcontrol.xml</SCPDURL>
        <controlURL>/dlna/control/renderingcontrol</controlURL>
        <eventSubURL>/dlna/event/renderingcontrol</eventSubURL>
      </service>
      <service>
        <serviceType>$CONNECTION_MANAGER_TYPE</serviceType>
        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
        <SCPDURL>/dlna/scpd/connectionmanager.xml</SCPDURL>
        <controlURL>/dlna/control/connectionmanager</controlURL>
        <eventSubURL>/dlna/event/connectionmanager</eventSubURL>
      </service>
    </serviceList>
  </device>
</root>"""

        private fun soapResponse(serviceType: String, action: String, innerXml: String): String =
            """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:${action}Response xmlns:u="$serviceType">$innerXml</u:${action}Response>
  </s:Body>
</s:Envelope>"""

        private fun soapFault(code: Int, description: String): String =
            """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <s:Fault>
      <faultcode>s:Client</faultcode>
      <faultstring>UPnPError</faultstring>
      <detail>
        <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
          <errorCode>$code</errorCode>
          <errorDescription>${xmlEscape(description)}</errorDescription>
        </UPnPError>
      </detail>
    </s:Fault>
  </s:Body>
</s:Envelope>"""

        private fun readHttpRequest(socket: Socket, input: InputStream): HttpRequest? {
            val deadlineNs = System.nanoTime() + REQUEST_TIMEOUT_MS * 1_000_000L
            fun refreshTimeout(): Boolean {
                val remainingMs = (deadlineNs - System.nanoTime()) / 1_000_000L
                if (remainingMs <= 0L) return false
                socket.soTimeout = remainingMs.coerceAtMost(SOCKET_TIMEOUT_MS.toLong()).toInt()
                return true
            }
            val headerBytes = ByteArrayOutputStream()
            var state = 0
            while (headerBytes.size() < MAX_HEADER_BYTES) {
                if (!refreshTimeout()) return null
                val b = input.read()
                if (b == -1) return null
                headerBytes.write(b)
                state = when {
                    state == 0 && b == '\r'.code -> 1
                    state == 1 && b == '\n'.code -> 2
                    state == 2 && b == '\r'.code -> 3
                    state == 3 && b == '\n'.code -> break
                    b == '\r'.code -> 1
                    else -> 0
                }
            }
            val headerText = headerBytes.toString(Charsets.ISO_8859_1.name())
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull()?.split(' ') ?: return null
            if (requestLine.size < 2) return null
            val method = requestLine[0].uppercase(Locale.ROOT)
            val target = requestLine[1]
            val path = target.substringBefore('?')

            var contentLength = 0
            var soapAction = ""
            var sid = ""
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                when {
                    name.equals("Content-Length", ignoreCase = true) -> {
                        contentLength = value.toIntOrNull() ?: return null
                    }
                    name.equals("SOAPAction", ignoreCase = true) -> soapAction = value
                    name.equals("SID", ignoreCase = true) -> sid = value
                }
            }
            if (contentLength !in 0..MAX_BODY_BYTES) return null
            val bodyBytes = ByteArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength) {
                if (!refreshTimeout()) return null
                val n = input.read(bodyBytes, readTotal, contentLength - readTotal)
                if (n < 0) return null
                readTotal += n
            }
            return HttpRequest(method, path, soapAction, sid, String(bodyBytes, Charsets.UTF_8))
        }

        private fun writeResponse(
            output: OutputStream,
            status: Int,
            reason: String,
            contentType: String,
            body: String,
            extraHeaders: Map<String, String> = emptyMap(),
        ) {
            val payload = body.toByteArray(Charsets.UTF_8)
            val headers = buildString {
                append("HTTP/1.1 $status $reason\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${payload.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("EXT:\r\n")
                for ((k, v) in extraHeaders) append("$k: $v\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(headers.toByteArray(Charsets.ISO_8859_1))
            if (payload.isNotEmpty()) output.write(payload)
            output.flush()
        }

        private fun xmlEscape(text: String): String = buildString(text.length) {
            for (ch in text) when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }

        private val NUMERIC_ENTITY_REGEX = Regex("&#(x[0-9A-Fa-f]+|\\d+);")

        internal fun xmlUnescape(text: String): String {
            val numericDecoded = NUMERIC_ENTITY_REGEX.replace(text) { match ->
                val raw = match.groupValues[1]
                val codePoint = if (raw.startsWith("x", ignoreCase = true)) {
                    raw.substring(1).toIntOrNull(16)
                } else {
                    raw.toIntOrNull(10)
                }
                if (codePoint != null && codePoint in 1..0x10FFFF && Character.isValidCodePoint(codePoint)) {
                    String(Character.toChars(codePoint))
                } else {
                    match.value
                }
            }
            return numericDecoded
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
        }

        private const val AV_TRANSPORT_SCPD = """<?xml version="1.0" encoding="UTF-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <actionList>
    <action><name>SetAVTransportURI</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>CurrentURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>
      <argument><name>CurrentURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>Play</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>Speed</name><direction>in</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>Pause</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>Stop</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>Seek</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>Unit</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekMode</relatedStateVariable></argument>
      <argument><name>Target</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekTarget</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>GetTransportInfo</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>CurrentTransportState</name><direction>out</direction><relatedStateVariable>TransportState</relatedStateVariable></argument>
      <argument><name>CurrentTransportStatus</name><direction>out</direction><relatedStateVariable>TransportStatus</relatedStateVariable></argument>
      <argument><name>CurrentSpeed</name><direction>out</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>GetPositionInfo</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>Track</name><direction>out</direction><relatedStateVariable>CurrentTrack</relatedStateVariable></argument>
      <argument><name>TrackDuration</name><direction>out</direction><relatedStateVariable>CurrentTrackDuration</relatedStateVariable></argument>
      <argument><name>TrackMetaData</name><direction>out</direction><relatedStateVariable>CurrentTrackMetaData</relatedStateVariable></argument>
      <argument><name>TrackURI</name><direction>out</direction><relatedStateVariable>CurrentTrackURI</relatedStateVariable></argument>
      <argument><name>RelTime</name><direction>out</direction><relatedStateVariable>RelativeTimePosition</relatedStateVariable></argument>
      <argument><name>AbsTime</name><direction>out</direction><relatedStateVariable>AbsoluteTimePosition</relatedStateVariable></argument>
      <argument><name>RelCount</name><direction>out</direction><relatedStateVariable>RelativeCounterPosition</relatedStateVariable></argument>
      <argument><name>AbsCount</name><direction>out</direction><relatedStateVariable>AbsoluteCounterPosition</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>GetMediaInfo</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>NrTracks</name><direction>out</direction><relatedStateVariable>NumberOfTracks</relatedStateVariable></argument>
      <argument><name>MediaDuration</name><direction>out</direction><relatedStateVariable>CurrentMediaDuration</relatedStateVariable></argument>
      <argument><name>CurrentURI</name><direction>out</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>
      <argument><name>CurrentURIMetaData</name><direction>out</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>
      <argument><name>NextURI</name><direction>out</direction><relatedStateVariable>NextAVTransportURI</relatedStateVariable></argument>
      <argument><name>NextURIMetaData</name><direction>out</direction><relatedStateVariable>NextAVTransportURIMetaData</relatedStateVariable></argument>
      <argument><name>PlayMedium</name><direction>out</direction><relatedStateVariable>PlaybackStorageMedium</relatedStateVariable></argument>
      <argument><name>RecordMedium</name><direction>out</direction><relatedStateVariable>RecordStorageMedium</relatedStateVariable></argument>
      <argument><name>WriteStatus</name><direction>out</direction><relatedStateVariable>RecordMediumWriteStatus</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>GetCurrentTransportActions</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>Actions</name><direction>out</direction><relatedStateVariable>CurrentTransportActions</relatedStateVariable></argument>
    </argumentList></action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_SeekMode</name><dataType>string</dataType><allowedValueList><allowedValue>REL_TIME</allowedValue></allowedValueList></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_SeekTarget</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>AVTransportURI</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>AVTransportURIMetaData</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>NextAVTransportURI</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>NextAVTransportURIMetaData</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>PlaybackStorageMedium</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>RecordStorageMedium</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>RecordMediumWriteStatus</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>TransportState</name><dataType>string</dataType><allowedValueList><allowedValue>STOPPED</allowedValue><allowedValue>PLAYING</allowedValue><allowedValue>PAUSED_PLAYBACK</allowedValue><allowedValue>TRANSITIONING</allowedValue></allowedValueList></stateVariable>
    <stateVariable sendEvents="no"><name>TransportStatus</name><dataType>string</dataType><allowedValueList><allowedValue>OK</allowedValue><allowedValue>ERROR_OCCURRED</allowedValue></allowedValueList></stateVariable>
    <stateVariable sendEvents="no"><name>TransportPlaySpeed</name><dataType>string</dataType><allowedValueList><allowedValue>1</allowedValue></allowedValueList></stateVariable>
    <stateVariable sendEvents="no"><name>NumberOfTracks</name><dataType>ui4</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>CurrentTrack</name><dataType>ui4</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>CurrentTrackDuration</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>CurrentMediaDuration</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>CurrentTrackMetaData</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>CurrentTrackURI</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>RelativeTimePosition</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>AbsoluteTimePosition</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>RelativeCounterPosition</name><dataType>i4</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>AbsoluteCounterPosition</name><dataType>i4</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>CurrentTransportActions</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="yes"><name>LastChange</name><dataType>string</dataType></stateVariable>
  </serviceStateTable>
</scpd>"""

        private const val RENDERING_CONTROL_SCPD = """<?xml version="1.0" encoding="UTF-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <actionList>
    <action><name>GetVolume</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
      <argument><name>CurrentVolume</name><direction>out</direction><relatedStateVariable>Volume</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>SetVolume</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
      <argument><name>DesiredVolume</name><direction>in</direction><relatedStateVariable>Volume</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>GetMute</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
      <argument><name>CurrentMute</name><direction>out</direction><relatedStateVariable>Mute</relatedStateVariable></argument>
    </argumentList></action>
    <action><name>SetMute</name><argumentList>
      <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
      <argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>
      <argument><name>DesiredMute</name><direction>in</direction><relatedStateVariable>Mute</relatedStateVariable></argument>
    </argumentList></action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>
    <stateVariable sendEvents="no"><name>A_ARG_TYPE_Channel</name><dataType>string</dataType><allowedValueList><allowedValue>Master</allowedValue></allowedValueList></stateVariable>
    <stateVariable sendEvents="no"><name>Volume</name><dataType>ui2</dataType><allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange></stateVariable>
    <stateVariable sendEvents="no"><name>Mute</name><dataType>boolean</dataType></stateVariable>
    <stateVariable sendEvents="yes"><name>LastChange</name><dataType>string</dataType></stateVariable>
  </serviceStateTable>
</scpd>"""

        private const val CONNECTION_MANAGER_SCPD = """<?xml version="1.0" encoding="UTF-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <actionList>
    <action><name>GetProtocolInfo</name><argumentList>
      <argument><name>Source</name><direction>out</direction><relatedStateVariable>SourceProtocolInfo</relatedStateVariable></argument>
      <argument><name>Sink</name><direction>out</direction><relatedStateVariable>SinkProtocolInfo</relatedStateVariable></argument>
    </argumentList></action>
  </actionList>
  <serviceStateTable>
    <stateVariable sendEvents="yes"><name>SourceProtocolInfo</name><dataType>string</dataType></stateVariable>
    <stateVariable sendEvents="yes"><name>SinkProtocolInfo</name><dataType>string</dataType></stateVariable>
  </serviceStateTable>
</scpd>"""
    }
}
