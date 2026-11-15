package com.reiniertutoriales.vireolumatv.activity.main.remote

import android.util.Log
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.activity.main.AddressInput
import com.reiniertutoriales.vireolumatv.activity.main.ExternalWebNavigation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * Ephemeral local HTTP server used only while the "Send from phone" dialog is open.
 * Requires a per-session cryptographic token so other devices on the LAN cannot inject URLs
 * without scanning the QR code, and stops automatically once a valid link is received or closed.
 */
internal class RemoteLinkServer(
    private val scope: CoroutineScope,
    private val searchTemplate: String,
    private val labels: Labels,
    private val hostAddress: String? = findLocalIpv4Address(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val onLinkReceived: (String) -> Unit,
) : Closeable {

    data class Labels(
        val title: String,
        val hint: String,
        val submit: String,
        val sent: String,
        val invalidUrl: String,
        val error: String,
    )

    val token: String = generateToken()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    @Volatile private var closed = false
    @Volatile private var delivered = false

    val url: String?
        get() {
            val host = hostAddress ?: return null
            val port = serverSocket?.localPort ?: return null
            return "http://$host:$port/?t=$token"
        }

    fun start(): String? {
        if (closed || hostAddress == null) return null
        if (serverSocket != null) return url
        val socket = try {
            ServerSocket(0)
        } catch (error: Exception) {
            Log.w(TAG, "Could not bind local server", error)
            return null
        }
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !closed && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                launch(Dispatchers.IO) { handleClient(client) }
            }
        }
        return url
    }

    override fun close() {
        if (closed) return
        closed = true
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            runCatching {
                client.soTimeout = SOCKET_TIMEOUT_MS
                val input = client.getInputStream()
                val output = client.getOutputStream()
                val request = readHttpRequest(input) ?: return
                when {
                    request.method == "GET" && request.path == "/" -> {
                        val provided = queryParam(request.query, "t")
                        if (!tokensEqual(provided, token)) {
                            writeResponse(output, 403, "Forbidden", "text/plain; charset=utf-8", "Forbidden")
                        } else {
                            writeResponse(output, 200, "OK", "text/html; charset=utf-8", buildPageHtml())
                        }
                    }
                    request.method == "POST" && request.path == "/open" -> {
                        val params = parseFormUrlEncoded(request.body)
                        if (!tokensEqual(params["t"], token)) {
                            writeResponse(output, 403, "Forbidden", "application/json; charset=utf-8", """{"ok":false,"error":"forbidden"}""")
                            return
                        }
                        val resolved = resolveSubmittedUrl(params["url"].orEmpty(), searchTemplate)
                        if (resolved == null) {
                            writeResponse(output, 400, "Bad Request", "application/json; charset=utf-8", """{"ok":false,"error":"invalid_url"}""")
                            return
                        }
                        writeResponse(output, 200, "OK", "application/json; charset=utf-8", """{"ok":true}""")
                        deliverOnce(resolved)
                    }
                    else -> writeResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not found")
                }
            }.onFailure { error ->
                if (!closed) Log.d(TAG, "Remote client request ended", error)
            }
        }
    }

    private fun deliverOnce(resolvedUrl: String) {
        synchronized(this) {
            if (delivered || closed) return
            delivered = true
        }
        close()
        scope.launch(mainDispatcher) { onLinkReceived(resolvedUrl) }
    }

    private fun buildPageHtml(): String {
        val title = htmlEscape(labels.title)
        val hint = htmlEscape(labels.hint)
        val submit = htmlEscape(labels.submit)
        val sent = jsEscape(labels.sent)
        val invalid = jsEscape(labels.invalidUrl)
        val err = jsEscape(labels.error)
        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>$title</title>
<style>
*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0b0d10;color:#f3f5f7;font-family:system-ui,-apple-system,sans-serif;padding:20px}
main{width:100%;max-width:440px;background:#151921;border:1px solid #262c38;border-radius:16px;padding:24px}
h1{margin:0 0 16px;font-size:20px;font-weight:600}
input{width:100%;padding:14px;border-radius:10px;border:1px solid #353d4d;background:#0b0d10;color:#fff;font-size:16px;margin-bottom:12px}
button{width:100%;padding:14px;border:0;border-radius:10px;background:#2f80ed;color:#fff;font-size:16px;font-weight:600;cursor:pointer}
button:disabled{opacity:.6}
p{margin:14px 0 0;min-height:20px;font-size:14px;color:#9fb0c7}
</style>
</head>
<body>
<main>
<h1>$title</h1>
<form id="f">
<input id="u" type="text" inputmode="url" autocomplete="off" autocapitalize="none" placeholder="$hint" required autofocus>
<button id="b" type="submit">$submit</button>
</form>
<p id="s"></p>
</main>
<script>
const f=document.getElementById('f'),u=document.getElementById('u'),b=document.getElementById('b'),s=document.getElementById('s');
f.addEventListener('submit',async e=>{
  e.preventDefault();
  const val=u.value.trim();
  if(!val)return;
  b.disabled=true;s.textContent='';
  try{
    const r=await fetch('/open',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({t:'$token',url:val})});
    if(r.ok){s.textContent='$sent';u.value='';}
    else if(r.status===400){s.textContent='$invalid';b.disabled=false;}
    else{s.textContent='$err';b.disabled=false;}
  }catch(_){s.textContent='$err';b.disabled=false;}
});
</script>
</body>
</html>"""
    }

    internal data class HttpRequest(
        val method: String,
        val path: String,
        val query: String,
        val body: String,
    )

    internal companion object {
        private const val TAG = "RemoteLinkServer"
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val MAX_BODY_BYTES = 8 * 1024
        private const val MAX_INPUT_CHARS = 2048

        private fun generateToken(): String {
            val bytes = ByteArray(12)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        internal fun tokensEqual(a: String?, b: String): Boolean {
            if (a == null) return false
            return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
        }

        internal fun resolveSubmittedUrl(rawInput: String, searchTemplate: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_INPUT_CHARS) return null
            if (trimmed.any { it.isISOControl() }) return null
            val lower = trimmed.lowercase(Locale.ROOT)
            if (hasDisallowedExplicitScheme(lower)) return null
            val candidate = AddressInput.resolve(trimmed, searchTemplate)
            val uri = runCatching { candidate.toUri() }.getOrNull() ?: return null
            return candidate.takeIf { ExternalWebNavigation.isAllowed(uri) }
        }

        private fun hasDisallowedExplicitScheme(lower: String): Boolean {
            if (lower.startsWith("http://") || lower.startsWith("https://")) return false
            val colon = lower.indexOf(':')
            if (colon <= 0) return false
            val beforeSlash = lower.indexOf('/').let { it < 0 || colon < it } &&
                lower.indexOf('?').let { it < 0 || colon < it } &&
                lower.indexOf('#').let { it < 0 || colon < it }
            if (!beforeSlash) return false
            val schemePart = lower.substring(0, colon)
            val looksLikeScheme = schemePart[0] in 'a'..'z' &&
                schemePart.all { it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' }
            if (!looksLikeScheme) return false
            // Allow host:port (e.g. localhost:8080 or 192.168.1.5:3000) without mistaking port for a scheme.
            val afterColon = lower.substring(colon + 1)
            val isPort = afterColon.isNotEmpty() && afterColon.takeWhile { it != '/' && it != '?' && it != '#' }
                .let { it.isNotEmpty() && it.all { ch -> ch in '0'..'9' } }
            return !isPort
        }

        internal fun findLocalIpv4Address(): String? {
            val candidates = ArrayList<String>()
            val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val active = runCatching { iface.isUp && !iface.isLoopback && !iface.isVirtual }.getOrDefault(false)
                if (!active) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && addr.isSiteLocalAddress && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let(candidates::add)
                    }
                }
            }
            return candidates.firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") }
                ?: candidates.firstOrNull()
        }

        private fun readHttpRequest(input: InputStream): HttpRequest? {
            val headerBytes = ByteArrayOutputStream()
            var state = 0
            while (headerBytes.size() < MAX_HEADER_BYTES) {
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
                val n = input.read(bodyBytes, readTotal, contentLength - readTotal)
                if (n < 0) return null
                readTotal += n
            }
            return HttpRequest(method, path, query, String(bodyBytes, Charsets.UTF_8))
        }

        private fun queryParam(query: String, key: String): String? =
            parseFormUrlEncoded(query)[key]

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

        private fun writeResponse(output: OutputStream, status: Int, reason: String, contentType: String, body: String) {
            val payload = body.toByteArray(Charsets.UTF_8)
            val headers = "HTTP/1.1 $status $reason\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${payload.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n"
            output.write(headers.toByteArray(Charsets.ISO_8859_1))
            output.write(payload)
            output.flush()
        }

        private fun htmlEscape(text: String): String = buildString(text.length) {
            for (ch in text) when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }

        private fun jsEscape(text: String): String = buildString(text.length) {
            for (ch in text) when (ch) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '<' -> append("\\u003C")
                else -> append(ch)
            }
        }
    }
}
