package com.reiniertutoriales.vireolumatv.singleton

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import android.util.LruCache
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.utils.IconFinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

/**
 * Site icons by host. Looked up in memory, then in the icon file a host's row names, then on the
 * site itself; a found icon is kept in memory and, outside private mode, saved for next time.
 * At most [PARALLEL_LOOKUPS] sites are asked at once, one lookup per host runs at a time, and a
 * site that had no usable icon is not asked again for [RETRY_AFTER_MS].
 */
object Favicons {
    /** Where a host's icon file is recorded (the hosts table). */
    interface Store {
        fun find(host: String): SiteSettings? = null
        suspend fun update(row: SiteSettings) {}
        suspend fun insert(row: SiteSettings) {}
    }

    var store: Store = object : Store {}

    val directory: File get() = File(AppContext.context.cacheDir, "favicons")

    private const val TAG = "Favicons"
    private const val PREFERRED_SIDE = 120
    private const val MAX_SIDE = 256
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_CANDIDATES = 3
    private const val MAX_ICON_URL = 2048
    private const val PARALLEL_LOOKUPS = 2
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val RETRY_AFTER_MS = 60_000L

    private val memory = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failedAt = LruCache<String, Long>(128)
    private val network = Semaphore(PARALLEL_LOOKUPS)
    private val writing = Mutex()
    // A fixed set of locks keeps one lookup per host without a map that grows with every site.
    private val hostLocks = Array(32) { Mutex() }
    private val extractor = IconFinder()

    /** The icon of [address], a web address or a bare host (tried over HTTPS, then HTTP). */
    suspend fun get(address: String): Bitmap? {
        val scheme = address.substringBefore("://", "").lowercase(Locale.ROOT)
        if (scheme.isEmpty() && !address.contains("://")) return get("https://$address") ?: get("http://$address")
        if (scheme != "http" && scheme != "https") return null
        val url = runCatching { URL(address) }.getOrNull() ?: return null
        val host = url.host.lowercase(Locale.ROOT).ifEmpty { return null }
        memory.get(host)?.let { return it }
        val origin = "$scheme://${url.authority.lowercase(Locale.ROOT)}"
        // Wait for another lookup of this host before taking a network permit.
        return hostLocks[(host.hashCode() and Int.MAX_VALUE) % hostLocks.size].withLock {
            memory.get(host) ?: lookUp(url, host, origin)
        }
    }

    /** The icon already in memory for [host], without any lookup. */
    fun cached(host: String): Bitmap? = memory.get(host.lowercase(Locale.ROOT))

    /** Frees memory; the retry pauses stay, so memory pressure cannot start a burst of lookups. */
    fun trimMemory() = memory.evictAll()

    fun clear() {
        memory.evictAll()
        failedAt.evictAll()
    }

    private suspend fun lookUp(url: URL, host: String, origin: String): Bitmap? {
        failedAt.get(origin)?.let { if (SystemClock.elapsedRealtime() - it < RETRY_AFTER_MS) return null }
        val privateMode = AppContext.config.privateBrowsing
        val icon = try {
            val row = if (privateMode) null else withContext(Dispatchers.IO) { store.find(host) }
            stored(row) ?: network.withPermit { fromSite(url) }?.also { if (!privateMode) save(host, it, row) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "No icon for $host", error)
            null
        }
        if (icon == null) failedAt.put(origin, SystemClock.elapsedRealtime()) else memory.put(host, icon)
        return icon
    }

    private suspend fun stored(row: SiteSettings?): Bitmap? {
        val name = row?.favicon ?: return null
        return withContext(Dispatchers.IO) {
            val file = File(directory, name)
            if (!file.isFile || file.length() > MAX_BYTES) null else file.inputStream().use { it.readAtMost(MAX_BYTES) }?.let(::decode)
        }
    }

    /** The first decodable of the site's few candidates closest to [PREFERRED_SIDE]. */
    private suspend fun fromSite(url: URL): Bitmap? {
        // A page can list hundreds of duplicates, or SVGs that cannot be decoded here.
        val candidates = withContext(Dispatchers.IO) { extractor.iconsFromAddress(url) }
            .filter { it.src.length <= MAX_ICON_URL && isWeb(it.src) && !it.type.equals("image/svg+xml", ignoreCase = true) }
            .distinctBy { it.src }
            .sortedBy { abs(it.width.toLong() - PREFERRED_SIDE) + abs(it.height.toLong() - PREFERRED_SIDE) }
            .take(MAX_CANDIDATES)
        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            val bytes = try {
                withContext(Dispatchers.IO) { download(candidate.src) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Icon download failed", error)
                null
            }
            bytes?.let(::decode)?.let { return it }
        }
        return null
    }

    private fun isWeb(address: String) =
        runCatching { URL(address).protocol.lowercase(Locale.ROOT) in setOf("http", "https") }.getOrDefault(false)

    private fun download(address: String): ByteArray? {
        val connection = URL(address).openConnection()
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        try {
            return connection.getInputStream().use { it.readAtMost(MAX_BYTES) }
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }

    /** Decodes at most [MAX_SIDE] px a side, halving the resolution as needed. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while ((bounds.outWidth - 1) / sample + 1 > MAX_SIDE || (bounds.outHeight - 1) / sample + 1 > MAX_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * Writes the icon atomically under a hash of [host] (so no two sites share a file) and records it.
     * A failure only loses the saved copy, never the icon being shown.
     */
    private suspend fun save(host: String, icon: Bitmap, row: SiteSettings?) = withContext(Dispatchers.IO) {
        try {
            write(host, icon, row)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Icon of $host not saved", error)
        }
    }

    private suspend fun write(host: String, icon: Bitmap, row: SiteSettings?) {
        val dir = directory
        if (!dir.isDirectory && !dir.mkdirs()) return
        val name = MessageDigest.getInstance("SHA-256").digest(host.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".png"
        writing.withLock {
            currentCoroutineContext().ensureActive()
            val file = AtomicFile(File(dir, name))
            val output = file.startWrite()
            try {
                check(icon.compress(Bitmap.CompressFormat.PNG, 100, output))
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            if (row != null) {
                row.favicon = name
                store.update(row)
            } else {
                store.insert(SiteSettings(host).apply { favicon = name })
            }
        }
    }

    /** All of the stream, or null when it is longer than [limit] bytes. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count == -1) return output.toByteArray()
            if (output.size() + count > limit) return null
            output.write(buffer, 0, count)
        }
    }
}
