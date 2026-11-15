package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {
    @Test
    fun shortPayloadUsesVersionOneAndDrawsFinderAndTimingPatterns() {
        val qr = QrCode.encode("a")

        assertEquals(21, qr.size)
        assertFinderPattern(qr, 0, 0)
        assertFinderPattern(qr, qr.size - 7, 0)
        assertFinderPattern(qr, 0, qr.size - 7)
        assertTrue(qr[8, qr.size - 8])

        for (i in 8 until qr.size - 8) {
            assertEquals(i % 2 == 0, qr[i, 6])
            assertEquals(i % 2 == 0, qr[6, i])
        }
    }

    @Test
    fun localServerUrlWithTokenEncodesCleanly() {
        val url = "http://192.168.1.50:54321/?t=0123456789abcdef01234567"
        val qr = QrCode.encode(url)

        assertTrue(qr.size >= 29)
        assertEquals(0, (qr.size - 21) % 4)
        assertFinderPattern(qr, 0, 0)
        assertFinderPattern(qr, qr.size - 7, 0)
        assertFinderPattern(qr, 0, qr.size - 7)
    }

    @Test
    fun utf8PayloadEncodesWithinByteCapacity() {
        val qr = QrCode.encode("https://ejemplo.com/búsqueda?q=áéíóú")

        assertTrue(qr.size in 21..57)
        assertEquals(0, (qr.size - 21) % 4)
    }

    @Test
    fun emptyOrOversizedPayloadIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            QrCode.encode("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            QrCode.encode("x".repeat(214))
        }
    }

    private fun assertFinderPattern(qr: QrCode, left: Int, top: Int) {
        for (dy in 0..6) {
            for (dx in 0..6) {
                val expected =
                    dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx in 2..4 && dy in 2..4)
                assertEquals("($left+$dx, $top+$dy)", expected, qr[left + dx, top + dy])
            }
        }
        if (left == 0 && top == 0) {
            for (i in 0..7) {
                assertFalse(qr[7, i])
                assertFalse(qr[i, 7])
            }
        }
    }
}
