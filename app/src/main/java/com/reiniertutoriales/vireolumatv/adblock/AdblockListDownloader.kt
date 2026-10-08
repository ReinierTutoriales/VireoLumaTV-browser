package com.reiniertutoriales.vireolumatv.adblock

import com.reiniertutoriales.vireolumatv.utils.BoundedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A rejected primary must not disable blocking when a verified list mirror is available. */
internal object AdblockListDownloader {
    private const val MAX_TEXT_CHARS = 8 * 1024 * 1024

    /** HTTP cache validators of the copy saved on disk, sent only to the URL that produced it. */
    data class Validators(val url: String, val etag: String?, val lastModified: String?)

    sealed class Result {
        /** [url] is the endpoint that answered (primary or mirror); sub-lists come from there. */
        class Downloaded(val text: String, val validators: Validators?, val url: String) : Result()
        /** HTTP 304: the saved copy is still the current list. */
        object NotModified : Result()
    }

    fun download(urls: List<String>, requiresAdblockHeader: Boolean): String {
        return when (val result = fetch(urls, requiresAdblockHeader, null)) {
            is Result.Downloaded -> result.text
            Result.NotModified -> throw IOException("Unexpected HTTP 304 without validators")
        }
    }

    fun fetch(urls: List<String>, requiresAdblockHeader: Boolean, cached: Validators?): Result {
        var failure: Exception? = null
        for (url in urls.distinct()) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "VireoLumaTV/1.0 (filter list updater)")
                connection.setRequestProperty("Accept", "text/plain")
                // No Accept-Encoding: HttpURLConnection then requests gzip and decompresses it
                // transparently, which cuts the ~3.7 MB default lists to under 1 MB on the wire.
                val conditional = cached != null && cached.url == url
                if (conditional) {
                    cached!!.etag?.let { connection.setRequestProperty("If-None-Match", it) }
                    cached.lastModified?.let { connection.setRequestProperty("If-Modified-Since", it) }
                }
                try {
                    val code = connection.responseCode
                    if (code == HttpURLConnection.HTTP_NOT_MODIFIED && conditional) return Result.NotModified
                    if (code !in 200..299) {
                        throw IOException("Filter server returned HTTP $code")
                    }
                    val text = BoundedReader(connection.inputStream.bufferedReader(Charsets.UTF_8),
                        MAX_TEXT_CHARS).use { it.readText() }
                    if (!AdblockFilterListValidator.isValid(text, requiresAdblockHeader)) {
                        throw IOException("Server did not return a valid filter list")
                    }
                    val etag = connection.getHeaderField("ETag")
                    val lastModified = connection.getHeaderField("Last-Modified")
                    val validators = if (etag != null || lastModified != null)
                        Validators(url, etag, lastModified) else null
                    return Result.Downloaded(text, validators, url)
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
