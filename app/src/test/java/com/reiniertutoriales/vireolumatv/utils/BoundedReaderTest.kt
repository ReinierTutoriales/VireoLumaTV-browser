package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BoundedReaderTest {
    @Test fun acceptsExactLimitAcrossSmallReadsAndRejectsOneExtraCharacter() {
        val reader = BoundedReader("abcd".reader(), 4)
        val buffer = CharArray(3)
        assertEquals(3, reader.read(buffer))
        assertEquals(1, reader.read(buffer))
        assertEquals(-1, reader.read(buffer))
        assertEquals(0, reader.read(buffer, 0, 0))
        try {
            BoundedReader("abcde".reader(), 4).use { it.readText() }
            fail("Oversized text accepted")
        } catch (_: IOException) { }
    }
}
