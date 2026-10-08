package com.reiniertutoriales.vireolumatv.adblock

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AdblockCacheTest {
    private val engine = object : ContentBlockerEngine {
        override val cacheFileName = "compiled.dat"
        override fun compile(filterLists: List<String>): ContentBlocker? = null
        override fun deserialize(file: File): ContentBlocker? = null
    }

    @Test fun subscriptionsWithCollidingJavaHashesNeverShareCaches() {
        val first = "https://example.test/Aa"
        val second = "https://example.test/BB"
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(AdblockCache.fileFor(File("."), engine, first), AdblockCache.fileFor(File("."), engine, second))
    }

    @Test fun partialFailedAndSuccessfulWritesPreserveOrReplaceTheGoodCache() {
        val directory = Files.createTempDirectory("adblock-cache-test").toFile()
        val file = File(directory, "rules.dat").apply { writeText("good") }
        fun blocker(success: Boolean, throws: Boolean = false) = object : ContentBlocker {
            override fun shouldBlock(url: Uri, type: String?, baseHost: String) = false
            override fun serialize(file: File): Boolean {
                file.writeText("new")
                if (throws) throw java.io.IOException("disk full")
                return success
            }
        }
        try {
            assertFalse(AdblockCache.write(file, blocker(false)))
            assertEquals("good", file.readText())
            assertFalse(AdblockCache.write(file, blocker(false, true)))
            assertEquals("good", file.readText())
            assertTrue(AdblockCache.write(file, blocker(true)))
            assertEquals("new", file.readText())
            assertEquals(listOf("rules.dat"), directory.listFiles()!!.map { it.name })
            assertFalse(AdblockCache.write(File(directory, "missing/rules.dat"), blocker(true)))
        } finally { directory.deleteRecursively() }
    }
    @Test fun concurrentWritersUseSeparateTemporaryFilesAndPublishCompleteRules() {
        val directory = Files.createTempDirectory("adblock-concurrent-test").toFile()
        val file = File(directory, "rules.dat")
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        val writers = listOf("first", "second").map { content ->
            Thread {
                try {
                    val blocker = object : ContentBlocker {
                        override fun shouldBlock(url: Uri, type: String?, baseHost: String) = false
                        override fun serialize(file: File): Boolean {
                            file.writeText(content)
                            ready.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                            assertEquals(content, file.readText())
                            return true
                        }
                    }
                    assertTrue(AdblockCache.write(file, blocker))
                } catch (failure: Throwable) { error.set(failure) }
            }.apply { start() }
        }
        try {
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            release.countDown()
            writers.forEach { it.join(5000); assertFalse(it.isAlive) }
            error.get()?.let { throw it }
            assertTrue(file.readText() in listOf("first", "second"))
            assertTrue(AdblockCache.writeText(file, "||ads.test^"))
            assertEquals("||ads.test^", file.readText())
            assertEquals(listOf("rules.dat"), directory.listFiles()!!.map { it.name })
        } finally {
            release.countDown()
            writers.forEach { it.join(5000) }
            directory.deleteRecursively()
        }
    }

}
