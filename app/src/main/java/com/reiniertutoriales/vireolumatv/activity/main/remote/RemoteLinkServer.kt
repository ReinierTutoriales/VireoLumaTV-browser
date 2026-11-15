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
import kotlinx.coroutines.withTimeoutOrNull
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
import org.json.JSONObject
import java.util.Locale

/**
 * Local HTTP server for receiving links from a paired phone on the same Wi-Fi/LAN while the app
 * is open. Requires a 96-bit cryptographic token (checked in constant time) and enforces thread
 * and request-time limits against slow or abusive LAN clients.
 */
internal class RemoteLinkServer(
    private val scope: CoroutineScope,
    private val searchTemplate: String,
    private val labels: Labels,
    val token: String = generateToken(),
    private val preferredPort: Int = 0,
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
        val shortcutTitle: String = "",
        val shortcutSteps: String = "",
        val copyUrl: String = "",
        val copyToken: String = "",
        val copied: String = "",
    )

    private val serverDispatcher = Dispatchers.IO.limitedParallelism(2)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    @Volatile private var closed = false

    val boundPort: Int?
        get() = serverSocket?.localPort

    val url: String?
        get() {
            val host = hostAddress ?: return null
            val port = serverSocket?.localPort ?: return null
            return "http://$host:$port/?t=$token"
        }

    val openEndpointUrl: String?
        get() {
            val host = hostAddress ?: return null
            val port = serverSocket?.localPort ?: return null
            return "http://$host:$port/open"
        }

    fun start(): String? {
        if (closed || hostAddress == null) return null
        if (serverSocket != null) return url
        val socket = try {
            if (preferredPort in 1..65535) {
                runCatching { ServerSocket(preferredPort) }.getOrElse { ServerSocket(0) }
            } else {
                ServerSocket(0)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not bind local server", error)
            return null
        }
        serverSocket = socket
        acceptJob = scope.launch(serverDispatcher) {
            while (isActive && !closed && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                launch(serverDispatcher) {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                        handleClient(client)
                    } ?: runCatching { client.close() }
                }
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
                val request = readHttpRequest(client, input) ?: return
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
                        val params = parseOpenPayload(request.contentType, request.body)
                        val providedToken = params["t"] ?: request.bearerToken
                        if (!tokensEqual(providedToken, token)) {
                            writeResponse(output, 403, "Forbidden", "application/json; charset=utf-8", """{"ok":false,"error":"forbidden"}""")
                            return
                        }
                        val resolved = resolveSubmittedUrl(params["url"].orEmpty(), searchTemplate)
                        if (resolved == null) {
                            writeResponse(output, 400, "Bad Request", "application/json; charset=utf-8", """{"ok":false,"error":"invalid_url"}""")
                            return
                        }
                        writeResponse(output, 200, "OK", "application/json; charset=utf-8", """{"ok":true}""")
                        deliverLink(resolved)
                    }
                    else -> writeResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not found")
                }
            }.onFailure { error ->
                if (!closed) Log.d(TAG, "Remote client request ended", error)
            }
        }
    }

    private fun deliverLink(resolvedUrl: String) {
        if (closed) return
        scope.launch(mainDispatcher) { onLinkReceived(resolvedUrl) }
    }

    private fun buildPageHtml(): String {
        val title = htmlEscape(labels.title)
        val hint = htmlEscape(labels.hint)
        val submit = htmlEscape(labels.submit)
        val sent = jsEscape(labels.sent)
        val invalid = jsEscape(labels.invalidUrl)
        val err = jsEscape(labels.error)
        val endpoint = htmlEscape(openEndpointUrl.orEmpty())
        val shortcutTitle = htmlEscape(labels.shortcutTitle)
        val shortcutSteps = htmlEscape(labels.shortcutSteps).replace("\n", "<br>")
        val copyUrl = htmlEscape(labels.copyUrl)
        val copyToken = htmlEscape(labels.copyToken)
        val copied = jsEscape(labels.copied)
        val lang = htmlEscape(Locale.getDefault().toLanguageTag().ifBlank { "en" })
        val shortcutSection = if (shortcutTitle.isNotEmpty() && endpoint.isNotEmpty()) {
            """<section class="card">
<h2>$shortcutTitle</h2>
<p class="steps">$shortcutSteps</p>
<div class="row"><button type="button" class="sec" onclick="cp('$endpoint',this)">$copyUrl</button><button type="button" class="sec" onclick="cp('$token',this)">$copyToken</button></div>
<code class="mono">$endpoint</code>
</section>"""
        } else ""
        return """<!DOCTYPE html>
<html lang="$lang">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>$title</title>
<style>
*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0b0d10;color:#f3f5f7;font-family:system-ui,-apple-system,sans-serif;padding:20px}
main{width:100%;max-width:460px;background:#151921;border:1px solid #262c38;border-radius:16px;padding:24px}
h1{margin:0 0 16px;font-size:20px;font-weight:600}
h2{margin:0 0 8px;font-size:15px;font-weight:600;color:#d6e0ee}
input{width:100%;padding:14px;border-radius:10px;border:1px solid #353d4d;background:#0b0d10;color:#fff;font-size:16px;margin-bottom:12px}
button{width:100%;padding:14px;border:0;border-radius:10px;background:#2f80ed;color:#fff;font-size:16px;font-weight:600;cursor:pointer}
button:disabled{opacity:.6}
.sec{background:#232a37;font-size:14px;padding:10px}
.row{display:flex;gap:8px;margin-top:12px}
.card{margin-top:20px;padding-top:16px;border-top:1px solid #262c38}
.steps{margin:0;font-size:13px;line-height:1.5;color:#b8c4d4}
.mono{display:block;margin-top:10px;padding:8px 10px;border-radius:8px;background:#0b0d10;color:#9fb0c7;font-size:12px;word-break:break-all}
p#s{margin:14px 0 0;min-height:20px;font-size:14px;color:#9fb0c7}
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
$shortcutSection
</main>
<script>
const f=document.getElementById('f'),u=document.getElementById('u'),b=document.getElementById('b'),s=document.getElementById('s');
async function cp(text,btn){const prev=btn.textContent;try{await navigator.clipboard.writeText(text);btn.textContent='$copied'||prev;}catch(_){}setTimeout(()=>{btn.textContent=prev;},1500);}
f.addEventListener('submit',async e=>{
  e.preventDefault();
  const val=u.value.trim();
  if(!val)return;
  b.disabled=true;s.textContent='';
  try{
    const r=await fetch('/open',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({t:'$token',url:val})});
    if(r.ok){s.textContent='$sent';u.value='';b.disabled=false;}
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
        val contentType: String,
        val bearerToken: String?,
        val body: String,
    )

    internal companion object {
        private const val TAG = "RemoteLinkServer"
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val MAX_BODY_BYTES = 8 * 1024
        private const val MAX_INPUT_CHARS = 2048

        internal fun generateToken(): String {
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
            var contentType = ""
            var bearerToken: String? = null
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
                    name.equals("Content-Type", ignoreCase = true) -> contentType = value
                    name.equals("Authorization", ignoreCase = true) &&
                        value.startsWith("Bearer ", ignoreCase = true) -> {
                        bearerToken = value.substring(7).trim()
                    }
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
            return HttpRequest(method, path, query, contentType, bearerToken, String(bodyBytes, Charsets.UTF_8))
        }

        internal fun parseOpenPayload(contentType: String, body: String): Map<String, String> =
            if (contentType.lowercase(Locale.ROOT).contains("application/json")) {
                parseJsonBody(body)
            } else {
                parseFormUrlEncoded(body)
            }

        private fun parseJsonBody(body: String): Map<String, String> = runCatching {
            val obj = JSONObject(body)
            buildMap {
                obj.opt("t")?.let { if (it !== JSONObject.NULL) put("t", it.toString()) }
                obj.opt("url")?.let { if (it !== JSONObject.NULL) put("url", it.toString()) }
            }
        }.getOrDefault(emptyMap())

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
