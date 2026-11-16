package com.reiniertutoriales.vireolumatv.activity.main.cast

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * One lounge screen for one sender app (theme "cl" YouTube or "m" YouTube Music): screen id,
 * lounge token, session and the long-poll that receives remote messages. Ported from
 * yt-cast-receiver's `Session` and `RPCConnection` (MIT, Patrick Kan; see NOTICE.md).
 */
internal class LoungeSession(
    override val theme: String,
    private val store: ScreenStore,
    deviceId: String,
    private val screenName: String,
    private val scope: CoroutineScope,
    private val onMessages: (LoungeSession, List<LoungeMessage>, Boolean) -> Unit,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : CastSession {

    interface ScreenStore {
        var screenId: String
    }

    private val params = LoungeBindParams(
        id = deviceId,
        name = screenName,
        app = SCREEN_APP,
        theme = theme,
        brand = "VireoLumaTV",
        model = "Android TV",
    )
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val sendDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var job: Job? = null
    private var ofs = 0
    private var tokenRefreshAtMs = 0L
    @Volatile private var closed = false
    @Volatile private var pollConnection: HttpURLConnection? = null

    fun start() {
        if (closed || job?.isActive == true) return
        job = scope.launch(ioDispatcher) {
            var backoffMs = 1_500L
            while (isActive && !closed) {
                // poll() returns normally when the token is due: start a fresh session right away.
                val refreshed = runCatching {
                    begin()
                    backoffMs = 1_500L
                    poll()
                }.onFailure { if (!closed) Log.w(TAG, "($theme) session ended", it) }.isSuccess
                if (!isActive || closed) break
                if (refreshed) continue
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    /** Links the phone that sent [code] through DIAL to this screen. */
    suspend fun registerPairingCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val screenId = store.screenId.takeIf { it.isNotBlank() } ?: return@withContext false
        val body = LoungeBindParams.encode(
            listOf(
                "access_type" to "permanent",
                "app" to SCREEN_APP,
                "pairing_code" to code,
                "screen_id" to screenId,
                "screen_name" to screenName,
                "device_id" to params.id,
            ),
        )
        val ok = runCatching { post("$LOUNGE_API/pairing/register_pairing_code", body) != null }.getOrDefault(false)
        if (!ok) Log.w(TAG, "($theme) pairing code not registered")
        ok
    }

    override fun send(messages: List<LoungeMessage>) {
        if (closed || messages.isEmpty()) return
        scope.launch(sendDispatcher) { sendNow(messages) }
    }

    fun close() {
        if (closed) return
        closed = true
        job?.cancel()
        job = null
        runCatching { pollConnection?.disconnect() }
        pollConnection = null
    }

    private fun begin() {
        synchronized(params) {
            params.reset()
            ofs = 0
        }
        val storedId = store.screenId.takeIf { it.isNotBlank() }
        if (storedId == null) store.screenId = generateScreenId()
        val token = runCatching { loungeToken() }.getOrNull()
            ?: if (storedId != null) {
                // The stored screen may have expired: start over with a fresh one.
                store.screenId = generateScreenId()
                loungeToken()
            } else {
                throw IllegalStateException("no lounge token")
            }
        tokenRefreshAtMs = System.currentTimeMillis() +
            token.optLong("refreshIntervalInMillis", 0L).takeIf { it > 0 }.let { it ?: DEFAULT_REFRESH_MS }
        val initQuery = synchronized(params) {
            params.loungeIdToken = token.getString("loungeToken")
            params.initSessionQuery()
        }
        val body = post("$LOUNGE_API/bc/bind?$initQuery", "count=0") ?: throw IllegalStateException("bind failed")
        val messages = LoungeMessage.parseIncoming(body)
        synchronized(params) { messages.forEach { params.updateWith(it) } }
        check(synchronized(params) { params.canPoll() }) { "no SID or gsessionid" }
        val status = messages.filter { it.name == "loungeStatus" }
        if (status.isNotEmpty()) deliver(status, initial = true)
    }

    /** Long-polls for remote messages until the token needs refreshing or the session breaks. */
    private suspend fun poll() {
        var failures = 0
        while (!closed && System.currentTimeMillis() < tokenRefreshAtMs) {
            val query = synchronized(params) { params.rpcQuery() }
            val conn = (URI.create("$LOUNGE_API/bc/bind?$query").toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 70_000
            }
            pollConnection = conn
            try {
                if (conn.responseCode !in 200..299) throw IllegalStateException("rpc HTTP ${conn.responseCode}")
                failures = 0
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (!closed) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        val messages = LoungeMessage.parseIncoming(line)
                        if (messages.isEmpty()) continue
                        synchronized(params) { messages.forEach { params.updateWith(it) } }
                        deliver(messages, initial = false)
                    }
                }
            } catch (error: Exception) {
                if (closed) return
                if (++failures > MAX_RETRIES) throw error
                delay(1_000L)
            } finally {
                if (pollConnection === conn) pollConnection = null
                conn.disconnect()
            }
        }
    }

    private fun deliver(messages: List<LoungeMessage>, initial: Boolean) {
        scope.launch(mainDispatcher) { if (!closed) onMessages(this@LoungeSession, messages, initial) }
    }

    private fun sendNow(messages: List<LoungeMessage>) {
        val (query, body) = synchronized(params) {
            if (!params.canPoll()) return
            val query = params.sendMessageQuery(messages.first().aid)
            val pairs = ArrayList<Pair<String, String>>()
            pairs += "count" to messages.size.toString()
            pairs += "ofs" to ofs.toString()
            messages.forEachIndexed { i, message ->
                pairs += "req${i}__sc" to message.name
                (message.payload as? Map<*, *>)?.forEach { (k, v) ->
                    if (k != null && v != null) pairs += "req${i}_$k" to formatValue(v)
                }
            }
            ofs += messages.size
            query to LoungeBindParams.encode(pairs)
        }
        val ok = runCatching { post("$LOUNGE_API/bc/bind?$query", body) != null }.getOrDefault(false)
        if (!ok && !closed) {
            Log.w(TAG, "($theme) could not send ${messages.joinToString { it.name }}; reconnecting")
            // Forces a new session, as the reference does after a failed send.
            tokenRefreshAtMs = 0L
            runCatching { pollConnection?.disconnect() }
        }
    }

    private fun generateScreenId(): String =
        get("$LOUNGE_API/pairing/generate_screen_id")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("no screen id")

    private fun loungeToken(): JSONObject {
        val body = post(
            "$LOUNGE_API/pairing/get_lounge_token_batch",
            LoungeBindParams.encode(listOf("screen_ids" to store.screenId)),
        ) ?: throw IllegalStateException("token request failed")
        val screen = JSONObject(body).optJSONArray("screens")?.optJSONObject(0)
        return screen?.takeIf { it.optString("loungeToken").isNotEmpty() }
            ?: throw IllegalStateException("no lounge token")
    }

    private fun get(url: String): String? {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun post(url: String, body: String): String? {
        val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
        }
        return try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "($theme) POST ${url.substringAfter("/lounge/").substringBefore('?')} HTTP $code")
                null
            } else {
                conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            }
        } finally {
            conn.disconnect()
        }
    }

    internal companion object {
        private const val TAG = "LoungeSession"
        private const val LOUNGE_API = "https://www.youtube.com/api/lounge"
        private const val SCREEN_APP = "vireolumatv"
        private const val MAX_RETRIES = 3
        private const val DEFAULT_REFRESH_MS = 1_123_200_000L

        /** JS-style numbers: 12 rather than 12.0, so senders read the same values the reference sends. */
        fun formatValue(value: Any): String = when (value) {
            is Double -> if (value % 1.0 == 0.0 && !value.isInfinite()) value.toLong().toString() else value.toString()
            is Float -> formatValue(value.toDouble())
            else -> value.toString()
        }
    }
}
