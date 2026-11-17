package com.reiniertutoriales.vireolumatv.activity.main.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Local DIAL (SSDP + HTTP) responder that advertises VireoLumaTV as a YouTube / YouTube Music
 * Cast target on the LAN while the app is open in the foreground.
 */
internal class CastDialServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deviceUuid: String,
    private val friendlyName: String = "VireoLumaTV",
    private val hostInterfaceProvider: () -> LocalInterface? = ::findLocalInterface,
    /** Returns whether the launch succeeded (pairing code registered on the theme's session). */
    private val onDialLaunch: (DialLaunchRequest) -> Boolean,
) : Closeable {

    data class LocalInterface(
        val hostAddress: String,
        val networkInterface: NetworkInterface,
    )

    /** Launch data of a YouTube or YouTube Music phone: its pairing code and lounge theme ("cl" or "m"). */
    data class DialLaunchRequest(
        val pairingCode: String?,
        val theme: String?,
    )

    /** DIAL "run" id of the always-running YouTube app, as peer-dial reports it. */
    private val appPid = UUID.randomUUID().toString()

    private val serverDispatcher = Dispatchers.IO.limitedParallelism(4)
    private var serverSocket: ServerSocket? = null
    private var ssdpSocket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var httpJob: Job? = null
    private var ssdpJob: Job? = null
    private var activeHost: String? = null
    private var activeNetIf: NetworkInterface? = null
    @Volatile private var closed = false

    val httpPort: Int?
        get() = serverSocket?.localPort

    fun start(): Boolean {
        if (closed) return false
        if (serverSocket != null) return true
        val localIf = hostInterfaceProvider() ?: return false
        val http = try {
            ServerSocket(0)
        } catch (error: Exception) {
            Log.w(TAG, "Could not bind DIAL HTTP socket", error)
            return false
        }
        serverSocket = http
        activeHost = localIf.hostAddress
        activeNetIf = localIf.networkInterface

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
            multicastLock = wifi?.createMulticastLock("VireoLumaTV-DIAL")?.apply {
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

    private fun startSsdpLoop(localIf: LocalInterface) {
        ssdpJob = scope.launch(serverDispatcher) {
            val socket = try {
                MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(SSDP_PORT))
                    networkInterface = localIf.networkInterface
                    joinGroup(InetSocketAddress(InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT), localIf.networkInterface)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Could not join SSDP multicast", error)
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
                val st = extractSearchTarget(text) ?: continue
                if (st in serviceTypes(deviceUuid)) {
                    val port = serverSocket?.localPort ?: continue
                    val location = "http://${localIf.hostAddress}:$port/ssdp/device-desc.xml"
                    val responseBytes = buildSsdpResponse(
                        st = st,
                        location = location,
                        usn = "uuid:$deviceUuid::$st",
                    ).toByteArray(Charsets.UTF_8)
                    val reply = DatagramPacket(responseBytes, responseBytes.size, packet.address, packet.port)
                    runCatching { socket.send(reply) }
                }
            }
        }
    }

    private fun sendAlive(socket: MulticastSocket, host: String) {
        val port = serverSocket?.localPort ?: return
        val location = "http://$host:$port/ssdp/device-desc.xml"
        val group = runCatching { InetAddress.getByName(SSDP_ADDRESS) }.getOrNull() ?: return
        for (nt in serviceTypes(deviceUuid)) {
            val msg = buildSsdpNotify(nt = nt, usn = "uuid:$deviceUuid::$nt", location = location, nts = "ssdp:alive")
            val bytes = msg.toByteArray(Charsets.UTF_8)
            runCatching { socket.send(DatagramPacket(bytes, bytes.size, group, SSDP_PORT)) }
        }
    }

    private fun sendByeBye() {
        val socket = ssdpSocket ?: return
        val group = runCatching { InetAddress.getByName(SSDP_ADDRESS) }.getOrNull() ?: return
        for (nt in serviceTypes(deviceUuid)) {
            val msg = buildSsdpNotify(nt = nt, usn = "uuid:$deviceUuid::$nt", location = "", nts = "ssdp:byebye")
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
                val host = activeHost ?: "127.0.0.1"
                val port = serverSocket?.localPort ?: 0
                val baseUrl = "http://$host:$port"
                val path = request.path.replace(Regex("/{2,}"), "/").trimEnd('/').ifEmpty { "/" }

                when {
                    // A text/plain POST from any web page reaches us without a CORS preflight.
                    request.method != "GET" && !isAllowedOrigin(request.origin) -> {
                        Log.w(TAG, "DIAL ${request.method} refused for origin ${request.origin}")
                        writeResponse(output, 403, "Forbidden", "text/plain", "")
                    }
                    request.method == "GET" && path == "/ssdp/device-desc.xml" -> {
                        writeResponse(
                            output = output,
                            status = 200,
                            reason = "OK",
                            contentType = "application/xml",
                            body = deviceDescXml(deviceUuid, friendlyName, baseUrl),
                            extraHeaders = mapOf("Application-URL" to "$baseUrl/apps"),
                        )
                    }
                    request.method == "GET" && path == "/apps" -> writeResponse(output, 204, "No Content", "text/plain", "")
                    request.method == "GET" && path == "/apps/YouTube" -> {
                        writeResponse(output, 200, "OK", "application/xml", youtubeAppXml(appPid))
                    }
                    request.method == "POST" && path == "/apps/YouTube" -> {
                        // The app is always running, so peer-dial answers 200, with LOCATION when it launched.
                        val params = parseFormUrlEncoded(request.query) + parseFormUrlEncoded(request.body)
                        val launch = DialLaunchRequest(
                            pairingCode = params["pairingCode"]?.takeIf { it.isNotBlank() },
                            theme = params["theme"]?.takeIf { it.isNotBlank() },
                        )
                        Log.i(TAG, "DIAL launch theme=${launch.theme} pairing=${launch.pairingCode != null}")
                        val launched = onDialLaunch(launch)
                        writeResponse(
                            output = output,
                            status = 200,
                            reason = "OK",
                            contentType = "text/plain",
                            body = "",
                            extraHeaders = if (launched) mapOf("LOCATION" to "$baseUrl/apps/YouTube/$appPid") else emptyMap(),
                        )
                    }
                    request.method == "DELETE" && path.startsWith("/apps/YouTube/") ->
                        writeResponse(output, 405, "Method Not Allowed", "text/plain", "")
                    else -> writeResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not found")
                }
            }.onFailure { error ->
                if (!closed) Log.d(TAG, "DIAL client request ended", error)
            }
        }
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val query: String,
        val body: String,
        val origin: String? = null,
    )

    internal companion object {
        private const val TAG = "CastDialServer"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val DIAL_SERVICE_TYPE = "urn:dial-multiscreen-org:service:dial:1"
        private const val DIAL_DEVICE_TYPE = "urn:dial-multiscreen-org:device:dial:1"
        private const val SSDP_ALL = "ssdp:all"

        /**
         * Who may launch or stop the app: native apps send no Origin or a
         * "package:" one; of web pages, only YouTube's own. Anything else is a page trying to cast
         * from the browser of someone on the network.
         */
        internal fun isAllowedOrigin(origin: String?): Boolean {
            if (origin.isNullOrBlank()) return true
            if (origin.startsWith("package:")) return true
            val uri = runCatching { java.net.URI(origin) }.getOrNull() ?: return false
            val host = uri.host?.lowercase(Locale.ROOT) ?: return false
            return uri.scheme.equals("https", ignoreCase = true) &&
                (host == "youtube.com" || host.endsWith(".youtube.com"))
        }

        /** Search targets peer-dial answers and announces. */
        internal fun serviceTypes(uuid: String) =
            listOf(DIAL_SERVICE_TYPE, DIAL_DEVICE_TYPE, "upnp:rootdevice", SSDP_ALL, "uuid:$uuid")
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val MAX_BODY_BYTES = 8 * 1024

        internal fun findLocalInterface(): LocalInterface? {
            val candidates = ArrayList<LocalInterface>()
            val ifaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                val active = runCatching { iface.isUp && !iface.isLoopback && !iface.isVirtual }.getOrDefault(false)
                if (!active) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && addr.isSiteLocalAddress && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        candidates.add(LocalInterface(host, iface))
                    }
                }
            }
            return candidates.firstOrNull { it.hostAddress.startsWith("192.168.") || it.hostAddress.startsWith("10.") }
                ?: candidates.firstOrNull()
        }

        internal fun deviceDescXml(uuid: String, friendlyName: String, baseUrl: String): String {
            val safeName = xmlEscape(friendlyName)
            return """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <URLBase>${xmlEscape(baseUrl)}</URLBase>
  <device>
    <deviceType>urn:dial-multiscreen-org:device:dial:1</deviceType>
    <friendlyName>$safeName</friendlyName>
    <manufacturer>VireoLumaTV</manufacturer>
    <modelName>Android TV</modelName>
    <UDN>uuid:${xmlEscape(uuid)}</UDN>
    <serviceList>
      <service>
        <serviceType>urn:dial-multiscreen-org:service:dial:1</serviceType>
        <serviceId>urn:dial-multiscreen-org:serviceId:dial</serviceId>
        <controlURL>/ssdp/notfound</controlURL>
        <eventSubURL>/ssdp/notfound</eventSubURL>
        <SCPDURL>/ssdp/notfound</SCPDURL>
      </service>
    </serviceList>
  </device>
</root>"""
        }

        /**
         * Running, no stop, and no screenId in additionalData, as peer-dial serves it for
         * yt-cast-receiver: the phone then POSTs a launch with its pairing code and theme.
         */
        internal fun youtubeAppXml(pid: String): String =
            """<?xml version="1.0" encoding="UTF-8"?>
<service xmlns="urn:dial-multiscreen-org:schemas:dial" dialVer="1.7">
  <name>YouTube</name>
  <options allowStop="false"/>
  <state>running</state>
  <link rel="run" href="${xmlEscape(pid)}"/>
</service>"""

        internal fun extractSearchTarget(packetText: String): String? {
            val lines = packetText.split("\r\n", "\n")
            val first = lines.firstOrNull()?.trim() ?: return null
            if (!first.startsWith("M-SEARCH ", ignoreCase = true)) return null
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).trim().equals("ST", ignoreCase = true)) {
                    return line.substring(colon + 1).trim()
                }
            }
            return null
        }

        internal fun buildSsdpResponse(st: String, location: String, usn: String): String =
            "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "DATE: ${httpDateNow()}\r\n" +
                "EXT:\r\n" +
                "LOCATION: $location\r\n" +
                "OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n" +
                "CONFIGID.UPNP.ORG: 7337\r\n" +
                "BOOTID.UPNP.ORG: 7337\r\n" +
                "SERVER: Linux/Android UPnP/1.0 VireoLumaTV/1.0\r\n" +
                "ST: $st\r\n" +
                "USN: $usn\r\n\r\n"

        internal fun buildSsdpNotify(nt: String, usn: String, location: String, nts: String): String =
            buildString {
                append("NOTIFY * HTTP/1.1\r\n")
                append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                append("CACHE-CONTROL: max-age=1800\r\n")
                if (location.isNotEmpty()) append("LOCATION: $location\r\n")
                append("NT: $nt\r\n")
                append("NTS: $nts\r\n")
                append("SERVER: Linux/Android UPnP/1.0 VireoLumaTV/1.0\r\n")
                append("USN: $usn\r\n\r\n")
            }

        internal fun parseFormUrlEncoded(encoded: String): Map<String, String> {
            if (encoded.isEmpty()) return emptyMap()
            val out = HashMap<String, String>()
            for (pair in encoded.split('&')) {
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                val k = runCatching { URLDecoder.decode(pair.substring(0, eq), "UTF-8") }.getOrNull() ?: continue
                val v = runCatching { URLDecoder.decode(pair.substring(eq + 1), "UTF-8") }.getOrNull() ?: continue
                out[k] = v
            }
            return out
        }

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
            val qIndex = target.indexOf('?')
            val path = if (qIndex >= 0) target.substring(0, qIndex) else target
            val query = if (qIndex >= 0) target.substring(qIndex + 1) else ""

            var contentLength = 0
            var origin: String? = null
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).trim()
                when {
                    name.equals("Content-Length", ignoreCase = true) ->
                        contentLength = line.substring(colon + 1).trim().toIntOrNull() ?: return null
                    name.equals("Origin", ignoreCase = true) -> origin = line.substring(colon + 1).trim()
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
            return HttpRequest(method, path, query, String(bodyBytes, Charsets.UTF_8), origin)
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
                for ((k, v) in extraHeaders) append("$k: $v\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(headers.toByteArray(Charsets.ISO_8859_1))
            if (payload.isNotEmpty()) output.write(payload)
            output.flush()
        }

        private fun httpDateNow(): String =
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }.format(Date())

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
    }
}
