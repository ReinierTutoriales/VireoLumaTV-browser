package com.reiniertutoriales.vireolumatv.service.downloads

import java.io.InputStream

/** Reads the existing bridge String without copying its complete base64 payload. */
internal class BlobPayloadInputStream(private val data: String) : InputStream() {
    private var position: Int

    init {
        val comma = data.indexOf(',')
        position = if (data.startsWith("data:", ignoreCase = true)) {
            require(comma >= 0 && data.substring(0, comma).endsWith(";base64", ignoreCase = true)) {
                "Expected a base64 data URL"
            }
            comma + 1
        } else 0 // Also support the legacy raw-base64 task input.
    }

    override fun read(): Int {
        if (position >= data.length) return -1
        val value = data[position++].code
        require(value <= 127) { "Non-ASCII base64 payload" }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        if (length == 0) return 0
        if (position >= data.length) return -1
        val count = minOf(length, data.length - position)
        for (index in 0 until count) buffer[offset + index] = read().toByte()
        return count
    }
}
