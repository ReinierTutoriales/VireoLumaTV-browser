package com.reiniertutoriales.vireolumatv.adblock

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SubListCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val main = "! Title: uBlock filters\n!#include sub.txt\n||main.test^\n"
    private val base = "https://filters.test/ubo/"
    private val prefix = "adblock_list_ubo_test"

    private fun store() = SubListCache(folder.root, prefix)

    private fun downloaded(url: String, text: String, etag: String = "\"v1\"") =
        AdblockListDownloader.Result.Downloaded(text, AdblockListDownloader.Validators(url, etag, null), url)

    @Test fun changedSubListIsFetchedWithItsOwnValidatorsEvenWhenMainIsUnchanged() {
        store().refresh(main, base) { url, cached ->
            assertEquals(base + "sub.txt", url)
            assertNull(cached)
            downloaded(url, "||old.test^\n")
        }
        // The main list answered 304 for the same text: the sub-list must still be checked.
        var seen: AdblockListDownloader.Validators? = null
        val composed = store().refresh(main, base) { url, cached ->
            seen = cached
            downloaded(url, "||new.test^\n", "\"v2\"")
        }
        assertEquals("\"v1\"", seen?.etag)
        assertTrue(composed.contains("||new.test^"))
        assertFalse(composed.contains("||old.test^"))
    }

    @Test fun sameContentKeepsTheComposedTextAndMovesValidatorsOn() {
        val first = store().refresh(main, base) { url, _ -> downloaded(url, "||a.test^\n") }
        val second = store().refresh(main, base) { url, _ -> downloaded(url, "||a.test^\n", "\"v2\"") }
        assertEquals(first, second)
        val validators = File(folder.root, "$prefix.inc.sub.txt.meta").readLines()
        assertEquals("\"v2\"", validators[1])
    }

    @Test fun failedSubListLeavesItsSavedCopyInPlace() {
        store().refresh(main, base) { url, _ -> downloaded(url, "||a.test^\n") }
        assertThrows(IOException::class.java) {
            store().refresh(main, base) { _, _ -> throw IOException("offline") }
        }
        // Nothing was overwritten: a 304 afterwards still composes the last valid copy.
        val composed = store().refresh(main, base) { _, _ -> AdblockListDownloader.Result.NotModified }
        assertTrue(composed.contains("||a.test^"))
    }

    @Test fun nestedIncludesStopAtTheDepthLimit() {
        val texts = mapOf(
            "a.txt" to "!#include b.txt\n||a.test^\n",
            "b.txt" to "!#include c.txt\n||b.test^\n",
            "c.txt" to "!#include d.txt\n||c.test^\n",
            "d.txt" to "||d.test^\n"
        )
        val requested = mutableListOf<String>()
        val composed = store().refresh("!#include a.txt\n||main.test^\n", base) { url, _ ->
            requested += url.removePrefix(base)
            downloaded(url, texts.getValue(url.removePrefix(base)))
        }
        assertEquals(listOf("a.txt", "b.txt", "c.txt"), requested)
        assertTrue(composed.contains("||c.test^"))
        assertFalse(composed.contains("||d.test^"))
    }

    @Test fun cyclesAreExpandedOnce() {
        val texts = mapOf(
            "a.txt" to "!#include b.txt\n||a.test^\n",
            "b.txt" to "!#include a.txt\n||b.test^\n"
        )
        val requested = mutableListOf<String>()
        val composed = store().refresh("!#include a.txt\n", base) { url, _ ->
            requested += url.removePrefix(base)
            downloaded(url, texts.getValue(url.removePrefix(base)))
        }
        assertEquals(listOf("a.txt", "b.txt"), requested)
        assertEquals(1, Regex("\\|\\|a\\.test\\^").findAll(composed).count())
    }

    @Test fun removedIncludeIsPrunedFromDisk() {
        store().refresh(main, base) { url, _ -> downloaded(url, "||a.test^\n") }
        assertTrue(File(folder.root, "$prefix.inc.sub.txt").exists())
        store().refresh("! Title: uBlock filters\n!#include other.txt\n", base) { url, _ ->
            downloaded(url, "||b.test^\n")
        }
        assertFalse(File(folder.root, "$prefix.inc.sub.txt").exists())
        assertFalse(File(folder.root, "$prefix.inc.sub.txt.meta").exists())
        assertTrue(File(folder.root, "$prefix.inc.other.txt").exists())
    }

    @Test(expected = IOException::class)
    fun notModifiedWithoutSavedCopyFails() {
        store().refresh(main, base) { _, _ -> AdblockListDownloader.Result.NotModified }
    }
}
