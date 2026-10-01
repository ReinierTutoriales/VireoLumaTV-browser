package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadUtilsSanitizeFileNameTest {
    @Test
    fun rejectsEmptyAndDotOnlyNames() {
        listOf(null, "", "   ", ".", "..", "...", "/", "dir/").forEach {
            assertNull("input: $it", DownloadUtils.sanitizeFileName(it))
        }
    }

    @Test
    fun stripsDirectoryTraversal() {
        assertEquals("evil.apk", DownloadUtils.sanitizeFileName("../../Android/data/x/evil.apk"))
        assertEquals("evil.apk", DownloadUtils.sanitizeFileName("..\\..\\evil.apk"))
        assertEquals("a.jpg", DownloadUtils.sanitizeFileName("/sdcard/DCIM/a.jpg"))
    }

    @Test
    fun replacesReservedAndControlCharacters() {
        assertEquals("a_b_c_.txt", DownloadUtils.sanitizeFileName("a:b*c?.txt"))
        assertEquals("bad_name_.bin", DownloadUtils.sanitizeFileName("bad\u0000name\n.bin"))
        assertEquals("x.zip", DownloadUtils.sanitizeFileName(" ..x.zip "))
    }

    @Test
    fun keepsRegularNamesUnchanged() {
        assertEquals("normal file (1).mp4", DownloadUtils.sanitizeFileName("normal file (1).mp4"))
        assertEquals("vídeo ñ 日本.mkv", DownloadUtils.sanitizeFileName("vídeo ñ 日本.mkv"))
    }
}
