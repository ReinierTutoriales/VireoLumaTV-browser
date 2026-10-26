package com.reiniertutoriales.vireolumatv.adblock

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class SubListCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val main = "! Title: uBlock filters\n!#include sub.txt\n||main.test^\n"
    private val base = "https://filters.test/ubo/"

    @Test fun changedSubListIsFetchedWithItsOwnValidatorsEvenWhenMainIsUnchanged() {
        val store = SubListCache(folder.root, "adblock_list_ubo_test")
        val first = store.refresh(main, base) { url, cached ->
            assertEquals(base + "sub.txt", url)
            assertNull(cached)
            AdblockListDownloader.Result.Downloaded("||old.test^\n",
                AdblockListDownloader.Validators(url, "\"v1\"", null), url)
        }
        assertTrue(first.changed)
        assertTrue(first.text.contains("||old.test^"))

        // Same main list (the server answered 304 for it): the sub-list must still be checked.
        var seen: AdblockListDownloader.Validators? = null
        val second = store.refresh(main, base) { url, cached ->
            seen = cached
            AdblockListDownloader.Result.Downloaded("||new.test^\n",
                AdblockListDownloader.Validators(url, "\"v2\"", null), url)
        }
        assertEquals("\"v1\"", seen?.etag)
        assertTrue(second.changed)
        assertTrue(second.text.contains("||new.test^"))
        assertFalse(second.text.contains("||old.test^"))
    }

    @Test fun unchangedSubListIsNotReportedAsChanged() {
        val store = SubListCache(folder.root, "adblock_list_ubo_test")
        store.refresh(main, base) { url, _ ->
            AdblockListDownloader.Result.Downloaded("||a.test^\n",
                AdblockListDownloader.Validators(url, "\"v1\"", null), url)
        }
        val again = store.refresh(main, base) { _, _ -> AdblockListDownloader.Result.NotModified }
        assertFalse(again.changed)
        assertTrue(again.text.contains("||a.test^"))
    }

    @Test fun cachedCompositionNeedsEverySavedSubList() {
        val store = SubListCache(folder.root, "adblock_list_ubo_test")
        store.refresh(main, base) { url, _ ->
            AdblockListDownloader.Result.Downloaded("||a.test^\n",
                AdblockListDownloader.Validators(url, "\"v1\"", null), url)
        }
        assertEquals(true, store.cached(main)?.contains("||a.test^"))

        // A sub-list without its saved copy must not be composed from a partial cache.
        java.io.File(folder.root, "adblock_list_ubo_test.inc.sub.txt").delete()
        assertNull(store.cached(main))
    }

    @Test(expected = IOException::class)
    fun notModifiedWithoutSavedCopyFails() {
        SubListCache(folder.root, "adblock_list_ubo_test").refresh(main, base) { _, _ ->
            AdblockListDownloader.Result.NotModified
        }
    }
}
