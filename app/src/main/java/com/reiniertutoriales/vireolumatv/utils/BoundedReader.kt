package com.reiniertutoriales.vireolumatv.utils

import java.io.IOException
import java.io.Reader

/** Refuses oversized remote text before readText/JSON parsing can grow without a bound. */
class BoundedReader(private val source: Reader, private var remaining: Int) : Reader() {
    init { require(remaining >= 0) }

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        if (length == 0) return 0
        if (remaining == 0) {
            if (source.read() == -1) return -1
            throw IOException("Text exceeds the configured size limit")
        }
        val count = source.read(buffer, offset, minOf(length, remaining))
        if (count > 0) remaining -= count
        return count
    }

    override fun close() = source.close()
}
