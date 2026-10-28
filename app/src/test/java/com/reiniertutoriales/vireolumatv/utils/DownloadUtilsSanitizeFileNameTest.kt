package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadUtilsSanitizeFileNameTest {
    @Test
    fun rejectsEmptyAndDotOnlyNames() {
        listOf(null, "", "   ", ".", "..", "...", "/", "dir/").forEach {
            assertNull("input: $it", FileNames.sanitizeFileName(it))
        }
    }

    @Test
    fun stripsDirectoryTraversal() {
        assertEquals("evil.apk", FileNames.sanitizeFileName("../../Android/data/x/evil.apk"))
        assertEquals("evil.apk", FileNames.sanitizeFileName("..\\..\\evil.apk"))
        assertEquals("a.jpg", FileNames.sanitizeFileName("/sdcard/DCIM/a.jpg"))
    }

    @Test
    fun replacesReservedAndControlCharacters() {
        assertEquals("a_b_c_.txt", FileNames.sanitizeFileName("a:b*c?.txt"))
        assertEquals("bad_name_.bin", FileNames.sanitizeFileName("bad\u0000name\n.bin"))
        assertEquals("x.zip", FileNames.sanitizeFileName(" ..x.zip "))
    }

    @Test
    fun keepsRegularNamesUnchanged() {
        assertEquals("normal file (1).mp4", FileNames.sanitizeFileName("normal file (1).mp4"))
        assertEquals("vídeo ñ 日本.mkv", FileNames.sanitizeFileName("vídeo ñ 日本.mkv"))
    }
}
