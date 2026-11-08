package com.reiniertutoriales.vireolumatv.activity.downloads

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Behavior the downloads list relies on: day headers, paging count, live rows and progress text. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class DownloadListTest {
    private val day = 1_700_000_000_000L
    private val nextDay = day + 2 * 24 * 60 * 60 * 1000L

    private fun newList() = DownloadList()

    private fun download(id: Long, time: Long, name: String, size: Long = 0L) = Download().apply {
        this.id = id
        this.time = time
        this.filename = name
        this.filepath = "/downloads/$name"
        this.url = "https://example.test/$name"
        this.size = size
    }

    private fun DownloadList.names() = rows.filter { !it.isDayHeader }.map { it.filename }

    @Test
    fun addItemsInsertsOneHeaderPerDayAndCountsOnlyRealItems() {
        val list = newList().apply { addItems(listOf(download(1, day, "a"), download(2, day + 1000, "b"), download(3, nextDay, "c"))) }

        assertEquals(5, list.rows.size)
        assertEquals(3L, list.rowCount)
        assertTrue(list.rows[0].isDayHeader)
        assertTrue(list.rows[3].isDayHeader)
    }

    @Test
    fun liveItemIsAddedOnlyOnce() {
        val list = newList().apply {
            addLiveItem(download(1, day, "a"))
            addLiveItem(download(1, day, "a"))
        }

        assertEquals(1L, list.rowCount)
    }

    @Test
    fun removeDropsTheItemItsCountAndAnEmptyHeader() {
        val a = download(1, day, "a")
        val list = newList().apply { addItems(listOf(a, download(2, nextDay, "b"))) }

        list.remove(a)

        assertEquals(1L, list.rowCount)
        assertEquals(listOf("b"), list.names())
        assertEquals(2, list.rows.size)
    }

    @Test
    fun progressUpdateChangesOnlyTheMatchingRow() {
        val a = download(1, day, "a", size = 100)
        val list = newList().apply { addItems(listOf(a, download(2, day + 1, "b"))) }

        val update = download(1, day, "a", size = 100).apply { bytesReceived = 50 }
        list.updateDownload(update)

        assertEquals(50L, a.bytesReceived)
        assertEquals(listOf("a", "b"), list.names())
    }

    @Test
    fun serverSizeSmallerThanReceivedBytesIsIgnoredForFutureUpdates() {
        val a = download(1, day, "a", size = 100)
        val list = newList().apply { addItems(listOf(a)) }

        list.updateDownload(download(1, day, "a", size = 100).apply { bytesReceived = 150 })

        assertEquals(0L, a.size)
        assertFalse(list.rowCount == 0L)
    }

    private fun progress(info: Download) = DownloadProgress.of(VireoLumaTVApp.instance, info)

    @Test
    fun unknownTotalShowsAnIndeterminateBarInsteadOfANegativeProgress() {
        val shown = progress(download(1, day, "a", size = -1L).apply { bytesReceived = 4096 })
        assertTrue(shown.indeterminate)
        assertEquals(null, shown.fraction)
    }

    @Test
    fun runningDownloadShowsReceivedOverTotal() {
        val shown = progress(download(1, day, "a", size = 4000L).apply { bytesReceived = 1000 })
        assertTrue(shown.text, shown.text.contains(" / "))
        assertFalse(shown.indeterminate)
        assertEquals(0.25f, shown.fraction)
    }

    @Test
    fun finishedAndBrokenDownloadsHideTheBar() {
        val done = progress(download(1, day, "a", size = 10L).apply { bytesReceived = 10 })
        assertEquals(null, done.fraction)
        assertFalse(done.indeterminate)
        val broken = progress(download(2, day, "b", size = Download.SIZE_FAILED))
        assertEquals(null, broken.fraction)
        assertTrue(broken.error)
    }
}
