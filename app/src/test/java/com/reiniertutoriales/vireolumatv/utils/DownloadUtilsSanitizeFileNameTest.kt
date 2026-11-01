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
    fun removesInvisibleFormatCharactersThatDisguiseTheExtension() {
        // U+202E would show "invoice\u202Efdp.apk" as "invoiceapk.pdf".
        assertEquals("invoice_fdp.apk", FileNames.sanitizeFileName("invoice\u202Efdp.apk"))
        for (c in listOf('\u200E', '\u200F', '\u061C', '\u202A', '\u202B', '\u202C', '\u202D',
                '\u2066', '\u2067', '\u2068', '\u2069', '\uFEFF', '\u00AD')) {
            assertEquals("U+%04X".format(c.code), "a_b.txt", FileNames.sanitizeFileName("a${c}b.txt"))
        }
        // Joiners belong inside Persian and Indic words.
        assertEquals("می\u200Cخواهم.pdf", FileNames.sanitizeFileName("می\u200Cخواهم.pdf"))
        assertEquals("क्\u200Dष.txt", FileNames.sanitizeFileName("क्\u200Dष.txt"))
    }

    @Test
    fun keepsRegularNamesUnchanged() {
        assertEquals("normal file (1).mp4", FileNames.sanitizeFileName("normal file (1).mp4"))
        assertEquals("vídeo ñ 日本.mkv", FileNames.sanitizeFileName("vídeo ñ 日本.mkv"))
    }
}
