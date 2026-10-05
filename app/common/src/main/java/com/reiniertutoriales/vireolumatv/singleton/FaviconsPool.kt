package com.reiniertutoriales.vireolumatv.singleton

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import android.util.LruCache
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.model.HostConfig
import com.reiniertutoriales.vireolumatv.utils.FaviconExtractor
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
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

object FaviconsPool {
    const val FAVICONS_DIR = "favicons"
    const val FAVICON_PREFERRED_SIDE_SIZE = 120
    private const val FAVICON_CONNECT_TIMEOUT_MS = 5_000
    private const val FAVICON_READ_TIMEOUT_MS = 10_000
    private const val MAX_FAVICON_BYTES = 2 * 1024 * 1024
    private const val MAX_ICON_SIDE = 256
    private const val MAX_ICON_ATTEMPTS = 3
    private const val FAILED_LOOKUP_COOLDOWN_MS = 60_000L
    private val failedLookups = LruCache<String, Long>(128)
    private val requests = Semaphore(2)
    private val persistenceMutex = Mutex()
    // Fixed-size locks deduplicate a host without an ever-growing host/job map.
    private val hostLocks = Array(32) { Mutex() }
    private val TAG: String = FaviconsPool::class.java.simpleName

    val faviconExtractor = FaviconExtractor()
    var databaseDelegate: DatabaseDelegate = object : DatabaseDelegate {}

    interface DatabaseDelegate {
        fun findByHostName(host: String): HostConfig? = null
        suspend fun update(hostConfig: HostConfig) {}
        suspend fun insert(newHostConfig: HostConfig) {}
    }

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    suspend fun get(urlOrHost: String, knownIconSrc: String? = null): Bitmap? {
        val incognitoMode = AppContext.provideConfig().incognitoMode
        if (!urlOrHost.startsWith("http://", true) && !urlOrHost.startsWith("https://", true)) {
            //host passed?
            if (urlOrHost.contains("://")) {
                //not http or https
                return null
            }
            //try https first
            val httpsResult = get("https://$urlOrHost")
            if (httpsResult != null) {
                return httpsResult
            }
            return get("http://$urlOrHost")
        }
        val host = try { URL(urlOrHost).host.lowercase(Locale.ROOT) } catch (_: Exception) { return null }
        cache.get(host)?.let { return it }
        val origin = try { URL(urlOrHost).let { "${it.protocol.lowercase(Locale.ROOT)}://${it.authority.lowercase(Locale.ROOT)}" } }
            catch (_: Exception) { return null }
        // Do not occupy a global network permit while waiting for another lookup of this host.
        return hostLocks[(host.hashCode() and Int.MAX_VALUE) % hostLocks.size].withLock {
            cache.get(host)?.let { return@withLock it }
            failedLookups.get(origin)?.let { failedAt ->
                if (SystemClock.elapsedRealtime() - failedAt < FAILED_LOOKUP_COOLDOWN_MS) return@withLock null
                failedLookups.remove(origin)
            }
            val result = requests.withPermit { getHttp(urlOrHost, incognitoMode, knownIconSrc) }
            if (result == null) failedLookups.put(origin, SystemClock.elapsedRealtime())
            result
        }
    }

    private suspend fun getHttp(urlOrHost: String, incognitoMode: Boolean, knownIconSrc: String?): Bitmap? {
        try {
            val urlObj = URL(urlOrHost)
            val host = urlObj.host.lowercase(Locale.ROOT)
            if (host.isNotEmpty()) {
                val hostBitmap = cache.get(host)
                if (hostBitmap != null) {
                    return hostBitmap
                }
                val hostConfig = if (incognitoMode) null else withContext(Dispatchers.IO) { databaseDelegate.findByHostName(host) }
                if (hostConfig != null) {
                    val faviconFileName = hostConfig.favicon
                    if (faviconFileName != null) {
                        Log.d(TAG, "get: favicon found in db for $host")
                        val bitmap = withContext(Dispatchers.IO) {
                            val favIconsDir =
                                File(favIconsDir())
                            if (!favIconsDir.exists() && !favIconsDir.mkdir()) return@withContext null
                            val faviconFile = File(favIconsDir, faviconFileName)
                            if (faviconFile.exists()) {
                                if (faviconFile.length() <= MAX_FAVICON_BYTES) {
                                    faviconFile.inputStream().use { input ->
                                        input.readUpTo(MAX_FAVICON_BYTES)?.let { decodeIcon(it) }
                                    }
                                } else null
                            } else {
                                null
                            }
                        }
                        if (bitmap != null) {
                            Log.d(TAG, "get: favicon loaded from file for $host")
                            cache.put(host, bitmap)
                            return bitmap
                        }
                    }
                } else {
                    Log.d(TAG, "get: favicon not found in db for $host")
                }

                val discovered = try {
                    val known = knownIconSrc?.takeIf { it.length <= 2048 &&
                        runCatching { URL(it).protocol in listOf("http", "https") }.getOrDefault(false) }
                    if (known != null) arrayListOf(FaviconExtractor.IconInfo(known))
                    else withContext(Dispatchers.IO) { faviconExtractor.extractFavIconsFromURL(urlObj) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                    ArrayList()
                }
                // A page can list hundreds of duplicates or SVGs that BitmapFactory cannot decode.
                val favicons = ArrayList(discovered.filter { icon ->
                    runCatching { URL(icon.src).protocol.lowercase(Locale.ROOT) in listOf("http", "https") }.getOrDefault(false) &&
                        !icon.type.equals("image/svg+xml", true)
                }.distinctBy { it.src }.sortedBy {
                    kotlin.math.abs(it.width.toLong() - FAVICON_PREFERRED_SIDE_SIZE) +
                        kotlin.math.abs(it.height.toLong() - FAVICON_PREFERRED_SIDE_SIZE)
                }.take(MAX_ICON_ATTEMPTS))
                Log.d(TAG, "get: bounded favicon attempts: ${favicons.size}")
                while (favicons.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val icon = favicons.first()
                    val bitmap = try {
                        downloadIcon(icon)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        e.printStackTrace()
                        null
                    }
                    if (bitmap != null) {
                        Log.d(TAG, "get: favicon downloaded for $host")
                        cache.put(host, bitmap)
                        if (!incognitoMode) {
                            saveFavicon(host, bitmap, hostConfig)
                        }
                        return bitmap
                    } else {
                        Log.d(TAG, "get: favicon download failed for ${icon.src}")
                    }
                    favicons.remove(icon)
                }
                // A missing decorative icon must not start another renderer or execute the page.
                // The caller displays its existing placeholder if HTTP discovery found no icon.
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
        return null
    }

    fun peek(host: String): Bitmap? = cache.get(host.lowercase(Locale.ROOT))

    fun trimMemory() {
        cache.evictAll() // Keep failure cooldowns so memory pressure cannot trigger a retry storm.
    }

    fun clear() {
        cache.evictAll()
        failedLookups.evictAll()
    }

    fun favIconsDir(): String {
        return AppContext.get().cacheDir.absolutePath + File.separator + FAVICONS_DIR
    }

    private suspend fun saveFavicon(host: String, bitmap: Bitmap, hostConfig: HostConfig?) = withContext(Dispatchers.IO) {
        if (AppContext.provideConfig().incognitoMode) return@withContext
        val favIconsDir = File(favIconsDir())
        if (!favIconsDir.exists() && !favIconsDir.mkdir()) return@withContext
        // Hash-code collisions must not let unrelated sites share an icon file.
        val faviconFileName = MessageDigest.getInstance("SHA-256")
            .digest(host.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ".png"
        persistenceMutex.withLock {
            currentCoroutineContext().ensureActive()
            val file = AtomicFile(File(favIconsDir, faviconFileName))
            var output: java.io.FileOutputStream? = null
            try {
                output = file.startWrite()
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            if (hostConfig != null) {
                hostConfig.favicon = faviconFileName
                databaseDelegate.update(hostConfig)
            } else {
                val newHostConfig = HostConfig(host)
                newHostConfig.favicon = faviconFileName
                databaseDelegate.insert(newHostConfig)
            }
        }
    }

    private suspend fun downloadIcon(iconInfo: FaviconExtractor.IconInfo): Bitmap? = withContext(Dispatchers.IO) {
        val iconBytes = readIconBytes(iconInfo.src) ?: return@withContext null
        currentCoroutineContext().ensureActive()
        decodeIcon(iconBytes)
    }

    private fun decodeIcon(iconBytes: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return null
        var sampleSize = 1
        while ((width - 1) / sampleSize + 1 > MAX_ICON_SIDE || (height - 1) / sampleSize + 1 > MAX_ICON_SIDE) {
            sampleSize *= 2
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        return BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size, options)
    }

    private fun readIconBytes(iconSrc: String): ByteArray? {
        val connection = URL(iconSrc).openConnection().apply {
            connectTimeout = FAVICON_CONNECT_TIMEOUT_MS
            readTimeout = FAVICON_READ_TIMEOUT_MS
        }
        try {
            connection.getInputStream().use { input -> return input.readUpTo(MAX_FAVICON_BYTES) }
        } finally {
            (connection as? java.net.HttpURLConnection)?.disconnect()
        }
    }

    private fun InputStream.readUpTo(maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count == -1) {
                return output.toByteArray()
            }
            total += count
            if (total > maxBytes) {
                return null
            }
            output.write(buffer, 0, count)
        }
    }

}
