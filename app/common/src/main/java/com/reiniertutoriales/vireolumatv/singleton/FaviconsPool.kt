package com.reiniertutoriales.vireolumatv.singleton

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.model.HostConfig
import com.reiniertutoriales.vireolumatv.utils.FaviconExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL

object FaviconsPool {
    const val FAVICONS_DIR = "favicons"
    const val FAVICON_PREFERRED_SIDE_SIZE = 120
    private const val FAVICON_CONNECT_TIMEOUT_MS = 5_000
    private const val FAVICON_READ_TIMEOUT_MS = 10_000
    private const val MAX_FAVICON_BYTES = 2 * 1024 * 1024
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

    suspend fun get(urlOrHost: String): Bitmap? {
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
        try {
            val urlObj = URL(urlOrHost)
            val host = urlObj.host
            if (host != null) {
                val hostBitmap = cache.get(host)
                if (hostBitmap != null) {
                    return hostBitmap
                }
                val hostConfig = if (incognitoMode) null else databaseDelegate.findByHostName(host)
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
                                BitmapFactory.decodeFile(faviconFile.absolutePath)
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

                val favicons = try {
                    withContext(Dispatchers.IO) { faviconExtractor.extractFavIconsFromURL(urlObj) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ArrayList()
                }
                Log.d(TAG, "get: favicons found: ${favicons.size}")
                while (favicons.isNotEmpty()) {
                    val icon = chooseNearestSizeIcon(favicons, FAVICON_PREFERRED_SIDE_SIZE, FAVICON_PREFERRED_SIDE_SIZE)!!
                    val bitmap = try {
                        downloadIcon(icon)
                    } catch (e: Exception) {
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
            e.printStackTrace()
        }
        return null
    }

    fun clear() {
        cache.evictAll()
    }

    fun favIconsDir(): String {
        return AppContext.get().cacheDir.absolutePath + File.separator + FAVICONS_DIR
    }

    private suspend fun saveFavicon(host: String, bitmap: Bitmap, hostConfig: HostConfig?) = withContext(Dispatchers.IO) {
        if (AppContext.provideConfig().incognitoMode) return@withContext
        val favIconsDir = File(favIconsDir())
        if (!favIconsDir.exists() && !favIconsDir.mkdir()) return@withContext
        val faviconFileName = host.hashCode().toString() + ".png"
        val faviconFile = File(favIconsDir, faviconFileName)
        if (faviconFile.exists()) {
            faviconFile.delete()
        }
        faviconFile.createNewFile()
        faviconFile.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
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

    private suspend fun downloadIcon(iconInfo: FaviconExtractor.IconInfo): Bitmap? = withContext(Dispatchers.IO) {
        val iconBytes = readIconBytes(iconInfo.src) ?: return@withContext null
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) {
            return@withContext null
        }
        var sampleSize = 1
        while ((width - 1) / sampleSize + 1 > 512 || (height - 1) / sampleSize + 1 > 512) {
            sampleSize *= 2
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        return@withContext BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size, options)
    }

    private fun readIconBytes(iconSrc: String): ByteArray? {
        val connection = URL(iconSrc).openConnection().apply {
            connectTimeout = FAVICON_CONNECT_TIMEOUT_MS
            readTimeout = FAVICON_READ_TIMEOUT_MS
        }
        connection.getInputStream().use { input ->
            return input.readUpTo(MAX_FAVICON_BYTES)
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

    private fun chooseNearestSizeIcon(icons: List<FaviconExtractor.IconInfo>, w: Int, h: Int): FaviconExtractor.IconInfo? {
        var nearestIcon: FaviconExtractor.IconInfo? = null
        var nearestDiff = Int.MAX_VALUE
        for (icon in icons) {
            val diff = Math.abs(icon.width - w) + Math.abs(icon.height - h)
            if (diff < nearestDiff) {
                nearestDiff = diff
                nearestIcon = icon
            }
        }
        return nearestIcon
    }
}