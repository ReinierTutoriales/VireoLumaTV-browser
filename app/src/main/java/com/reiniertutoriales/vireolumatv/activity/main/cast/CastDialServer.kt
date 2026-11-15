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

/**
 * Local DIAL (SSDP + HTTP) responder that advertises VireoLumaTV as a YouTube / YouTube Music
 * Cast target on the LAN while the app is open in the foreground.
 */
internal class CastDialServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deviceUuid: String,
    private val friendlyName: String = "VireoLumaTV",
    private val screenIdProvider: () -> String?,
    private val hostInterfaceProvider: () -> LocalInterface? = ::findLocalInterface,
    private val onDialLaunch: (DialLaunchRequest) -> Unit,
    private val onDialStop: () -> Unit = {},
) : Closeable {

    data class LocalInterface(
        val hostAddress: String,
        val networkInterface: NetworkInterface,
    )

    data class DialLaunchRequest(
        val pairingCode: String?,
        val videoId: String?,
        val listId: String?,
        val startSeconds: Double?,
        val isMusic: Boolean,
    )

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
                if (st == DIAL_SERVICE_TYPE || st == SSDP_ALL || st == DIAL_DEVICE_TYPE) {
                    val port = serverSocket?.localPort ?: continue
                    val location = "http://${localIf.hostAddress}:$port/ssdp/device-desc.xml"
                    val replySt = if (st == SSDP_ALL) DIAL_SERVICE_TYPE else st
                    val responseBytes = buildSsdpResponse(
                        st = replySt,
                        location = location,
                        usn = "uuid:$deviceUuid::$replySt",
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
        for (nt in listOf(DIAL_SERVICE_TYPE, "uuid:$deviceUuid")) {
            val usn = if (nt.startsWith("uuid:")) nt else "uuid:$deviceUuid::$nt"
            val msg = buildSsdpNotify(nt = nt, usn = usn, location = location, nts = "ssdp:alive")
            val bytes = msg.toByteArray(Charsets.UTF_8)
            runCatching { socket.send(DatagramPacket(bytes, bytes.size, group, SSDP_PORT)) }
        }
    }

    private fun sendByeBye() {
        val socket = ssdpSocket ?: return
        val group = runCatching { InetAddress.getByName(SSDP_ADDRESS) }.getOrNull() ?: return
        for (nt in listOf(DIAL_SERVICE_TYPE, "uuid:$deviceUuid")) {
            val usn = if (nt.startsWith("uuid:")) nt else "uuid:$deviceUuid::$nt"
            val msg = buildSsdpNotify(nt = nt, usn = usn, location = "", nts = "ssdp:byebye")
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
                val baseAppUrl = "http://$host:$port/apps/"

                when {
                    request.method == "GET" && request.path == "/ssdp/device-desc.xml" -> {
                        val xml = deviceDescXml(deviceUuid, friendlyName)
                        writeResponse(
                            output = output,
                            status = 200,
                            reason = "OK",
                            contentType = "application/xml; charset=utf-8",
                            body = xml,
                            extraHeaders = mapOf("Application-URL" to baseAppUrl),
                        )
                    }
                    request.method == "GET" && (request.path == "/apps/YouTube" || request.path == "/apps/YouTube/") -> {
                        val xml = youtubeAppXml(screenIdProvider())
                        writeResponse(
                            output = output,
                            status = 200,
                            reason = "OK",
                            contentType = "application/xml; charset=utf-8",
                            body = xml,
                        )
                    }
                    request.method == "POST" && (request.path == "/apps/YouTube" || request.path == "/apps/YouTube/") -> {
                        val params = parseFormUrlEncoded(request.body)
                        Log.d(
                            TAG,
                            "DIAL launch keys=${params.keys.filter { it != "pairingCode" }} theme=${params["theme"]} clientName=${params["clientName"]}",
                        )
                        val launch = DialLaunchRequest(
                            pairingCode = params["pairingCode"]?.takeIf { it.isNotBlank() },
                            videoId = params["v"]?.takeIf { it.isNotBlank() },
                            listId = params["list"]?.takeIf { it.isNotBlank() },
                            startSeconds = params["t"]?.toDoubleOrNull(),
                            isMusic = params["theme"]?.equals("music", ignoreCase = true) == true ||
                                params["clientName"]?.contains("music", ignoreCase = true) == true,
                        )
                        onDialLaunch(launch)
                        writeResponse(
                            output = output,
                            status = 201,
                            reason = "Created",
                            contentType = "text/plain; charset=utf-8",
                            body = "",
                            extraHeaders = mapOf("LOCATION" to "${baseAppUrl}YouTube/run"),
                        )
                    }
                    request.method == "DELETE" && request.path == "/apps/YouTube/run" -> {
                        onDialStop()
                        writeResponse(output, 200, "OK", "text/plain; charset=utf-8", "")
                    }
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
        val body: String,
    )

    internal companion object {
        private const val TAG = "CastDialServer"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val DIAL_SERVICE_TYPE = "urn:dial-multiscreen-org:service:dial:1"
        private const val DIAL_DEVICE_TYPE = "urn:dial-multiscreen-org:device:dial:1"
        private const val SSDP_ALL = "ssdp:all"
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

        internal fun deviceDescXml(uuid: String, friendlyName: String): String {
            val safeName = xmlEscape(friendlyName)
            val safeUuid = xmlEscape(uuid)
            return """<?xml version="1.0" encoding="UTF-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <device>
    <deviceType>urn:dial-multiscreen-org:device:dial:1</deviceType>
    <friendlyName>$safeName</friendlyName>
    <manufacturer>VireoLumaTV</manufacturer>
    <modelName>VireoLumaTV</modelName>
    <UDN>uuid:$safeUuid</UDN>
    <serviceList>
      <service>
        <serviceType>urn:dial-multiscreen-org:service:dial:1</serviceType>
        <serviceId>urn:dial-multiscreen-org:serviceId:dial</serviceId>
        <controlURL>/upnp/control/dial</controlURL>
        <eventSubURL>/upnp/event/dial</eventSubURL>
        <SCPDURL>/upnp/scpd/dial.xml</SCPDURL>
      </service>
    </serviceList>
  </device>
</root>"""
        }

        internal fun youtubeAppXml(screenId: String?): String {
            val extra = if (!screenId.isNullOrBlank()) {
                "\n  <additionalData>\n    <screenId>${xmlEscape(screenId)}</screenId>\n  </additionalData>"
            } else ""
            return """<?xml version="1.0" encoding="UTF-8"?>
<service xmlns="urn:dial-multiscreen-org:schemas:dial" dialVer="2.1">
  <name>YouTube</name>
  <options allowStop="true"/>
  <state>running</state>$extra
</service>"""
        }

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

            var contentLength = 0
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                    contentLength = line.substring(colon + 1).trim().toIntOrNull() ?: return null
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
            return HttpRequest(method, path, String(bodyBytes, Charsets.UTF_8))
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
