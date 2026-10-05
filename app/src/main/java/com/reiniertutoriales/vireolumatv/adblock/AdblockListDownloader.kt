package com.reiniertutoriales.vireolumatv.adblock

import com.reiniertutoriales.vireolumatv.utils.BoundedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A rejected primary must not disable blocking when a verified list mirror is available. */
internal object AdblockListDownloader {
    private const val MAX_TEXT_CHARS = 8 * 1024 * 1024

    fun download(urls: List<String>, requiresAdblockHeader: Boolean): String {
        var failure: Exception? = null
        for (url in urls.distinct()) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "VireoLumaTV/1.0 (filter list updater)")
                connection.setRequestProperty("Accept", "text/plain")
                connection.setRequestProperty("Accept-Encoding", "identity")
                try {
                    if (connection.responseCode !in 200..299) {
                        throw IOException("Filter server returned HTTP ${connection.responseCode}")
                    }
                    val text = BoundedReader(connection.inputStream.bufferedReader(Charsets.UTF_8),
                        MAX_TEXT_CHARS).use { it.readText() }
                    if (!AdblockFilterListValidator.isValid(text, requiresAdblockHeader)) {
                        throw IOException("Server did not return a valid filter list")
                    }
                    return text
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                failure = e
            }
        }
        throw IOException("No usable filter list endpoint", failure)
    }
}
