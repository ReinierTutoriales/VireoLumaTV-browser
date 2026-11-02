package com.reiniertutoriales.vireolumatv.webengine.webview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class BlobSpoolTest {
    @get:Rule val folder = TemporaryFolder()
    private val dir get() = File(folder.root, "blob-spool")

    @Test fun bytesArriveOnDiskAndTheFileIsGoneOnceRead() {
        val spool = BlobSpool.open(dir)
        spool.write(byteArrayOf(1, 2))
        spool.write(byteArrayOf(3))
        val input = spool.finish()
        assertArrayEquals(byteArrayOf(1, 2, 3), input.readBytes())
        assertEquals(1, dir.listFiles()!!.size)
        input.close()
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun abortDeletesTheFileAndRefusesFurtherWrites() {
        val spool = BlobSpool.open(dir)
        spool.write(byteArrayOf(1))
        spool.abort()
        spool.abort()
        assertEquals(0, dir.listFiles()!!.size)
        assertTrue(runCatching { spool.write(byteArrayOf(2)) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { spool.finish() }.exceptionOrNull() is IOException)
    }

    @Test fun openingRemovesOnlyFilesLeftBehindLongAgo() {
        dir.mkdirs()
        val now = System.currentTimeMillis()
        val stale = File(dir, "old.part").apply { writeText("x"); setLastModified(now - BlobSpool.STALE_MS - 1_000) }
        val recent = File(dir, "recent.part").apply { writeText("x"); setLastModified(now - 1_000) }
        BlobSpool.open(dir, now).abort()
        assertFalse(stale.exists())
        assertTrue(recent.exists())
    }
}
