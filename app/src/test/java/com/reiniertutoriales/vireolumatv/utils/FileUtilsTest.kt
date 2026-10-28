package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileUtilsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun deleteDirectoryRemovesNestedTree() {
        val root = tmp.newFolder("webview")
        File(root, "a/b").apply { mkdirs(); File(this, "c.db").writeText("x") }

        assertTrue(deleteDirectory(root))
        assertFalse(root.exists())
    }

    @Test
    fun deleteDirectoryReturnsFalseForMissingPath() {
        assertFalse(deleteDirectory(File(tmp.root, "absent")))
    }
}
