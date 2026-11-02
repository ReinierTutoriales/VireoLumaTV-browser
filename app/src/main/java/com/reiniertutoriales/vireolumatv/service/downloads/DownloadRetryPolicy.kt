package com.reiniertutoriales.vireolumatv.service.downloads

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Null means the server requested a wait too long for an automatic retry. */
internal object DownloadRetryPolicy {
    fun delayMillis(attempt: Int, retryAfter: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
        val backoff = 3_000L * (1L shl (attempt - 1).coerceIn(0, 4))
        val value = retryAfter?.trim()?.takeIf { it.isNotEmpty() } ?: return backoff
        val serverDelay = if (value.all { it in '0'..'9' }) {
            val seconds = value.toLongOrNull() ?: return null
            if (seconds > 60) return null
            seconds * 1_000L
        } else {
            val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
                isLenient = false
            }
            val position = ParsePosition(0)
            val date = parser.parse(value, position)
            if (date == null || position.index != value.length) return backoff
            // Compare against the deadline instead of subtracting timestamps:
            // subtraction can overflow for malformed or extreme HTTP dates.
            val deadline = date.time
            when {
                deadline <= nowMillis -> 0L
                nowMillis < 0L && deadline > Long.MAX_VALUE + nowMillis -> return null
                else -> deadline - nowMillis
            }
        }
        return maxOf(backoff, serverDelay).takeIf { it <= 60_000L }
    }
}
