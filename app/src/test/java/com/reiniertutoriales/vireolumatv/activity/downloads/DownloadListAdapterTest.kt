package com.reiniertutoriales.vireolumatv.activity.downloads

import androidx.recyclerview.widget.AsyncDifferConfig
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor

/**
 * Behavior the downloads list relies on. Diffing runs on a direct executor here so each
 * submitted list is applied deterministically; production uses the background default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class DownloadListAdapterTest {
    private val day = 1_700_000_000_000L
    private val nextDay = day + 2 * 24 * 60 * 60 * 1000L

    private fun adapter() = DownloadListAdapter({}, { _, _ -> false },
        AsyncDifferConfig.Builder(DownloadListAdapter.DIFF_CALLBACK)
            .setBackgroundThreadExecutor(Executor { it.run() })
            .build())

    private fun DownloadListAdapter.settle() = also { ShadowLooper.idleMainLooper() }

    private fun download(id: Long, time: Long, name: String, size: Long = 0L) = Download().apply {
        this.id = id
        this.time = time
        this.filename = name
        this.filepath = "/downloads/$name"
        this.url = "https://example.test/$name"
        this.size = size
    }

    private fun DownloadListAdapter.rows() = currentList.filter { !it.isDateHeader }.map { it.filename }

    @Test
    fun addItemsInsertsOneHeaderPerDayAndCountsOnlyRealItems() {
        val adapter = adapter().apply { addItems(listOf(download(1, day, "a"), download(2, day + 1000, "b"), download(3, nextDay, "c"))) }.settle()

        assertEquals(5, adapter.itemCount)
        assertEquals(3L, adapter.realCount)
        assertTrue(adapter.currentList[0].isDateHeader)
        assertTrue(adapter.currentList[3].isDateHeader)
    }

    @Test
    fun liveItemIsAddedOnlyOnce() {
        val adapter = adapter().apply {
            addLiveItem(download(1, day, "a"))
            addLiveItem(download(1, day, "a"))
        }.settle()

        assertEquals(1L, adapter.realCount)
    }

    @Test
    fun removeDropsTheItemItsCountAndAnEmptyHeader() {
        val a = download(1, day, "a")
        val adapter = adapter().apply { addItems(listOf(a, download(2, nextDay, "b"))) }.settle()

        adapter.remove(a)

        assertEquals(1L, adapter.settle().realCount)
        assertEquals(listOf("b"), adapter.rows())
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun progressUpdateChangesOnlyTheMatchingRow() {
        val a = download(1, day, "a", size = 100)
        val adapter = adapter().apply { addItems(listOf(a, download(2, day + 1, "b"))) }.settle()

        val update = download(1, day, "a", size = 100).apply { bytesReceived = 50 }
        adapter.updateDownload(update)

        assertEquals(50L, a.bytesReceived)
        assertEquals(listOf("a", "b"), adapter.rows())
    }

    @Test
    fun serverSizeSmallerThanReceivedBytesIsIgnoredForFutureUpdates() {
        val a = download(1, day, "a", size = 100)
        val adapter = adapter().apply { addItems(listOf(a)) }.settle()

        adapter.updateDownload(download(1, day, "a", size = 100).apply { bytesReceived = 150 })

        assertEquals(0L, a.size)
        assertFalse(adapter.settle().realCount == 0L)
    }

    /** Binds [info] into a real row and returns its size text and progress bar. */
    private fun bindRow(info: Download): Pair<String, android.widget.ProgressBar> {
        val adapter = adapter().apply { addItems(listOf(info)) }.settle()
        val parent = android.widget.FrameLayout(VireoLumaTVApp.instance.apply { setTheme(com.reiniertutoriales.vireolumatv.R.style.AppTheme) })
        val holder = adapter.onCreateViewHolder(parent, DownloadListAdapter.VIEW_TYPE_DOWNLOAD_ITEM)
        adapter.onBindViewHolder(holder, 1)
        val row = holder.itemView
        return row.findViewById<android.widget.TextView>(com.reiniertutoriales.vireolumatv.R.id.tvSize).text.toString() to
            row.findViewById(com.reiniertutoriales.vireolumatv.R.id.progressBar)
    }

    @Test
    fun unknownTotalShowsAnIndeterminateBarInsteadOfANegativeProgress() {
        val (_, bar) = bindRow(download(1, day, "a", size = -1L).apply { bytesReceived = 4096 })
        assertTrue(bar.visibility == android.view.View.VISIBLE)
        assertTrue(bar.isIndeterminate)
    }

    @Test
    fun runningDownloadShowsReceivedOverTotal() {
        val (text, bar) = bindRow(download(1, day, "a", size = 4000L).apply { bytesReceived = 1000 })
        assertTrue(text, text.contains(" / "))
        assertFalse(bar.isIndeterminate)
        assertEquals(25, bar.progress)
    }

    @Test
    fun finishedAndBrokenDownloadsHideTheBar() {
        val (_, done) = bindRow(download(1, day, "a", size = 10L).apply { bytesReceived = 10 })
        assertEquals(android.view.View.GONE, done.visibility)
        val (_, broken) = bindRow(download(2, day, "b", size = Download.BROKEN_MARK))
        assertEquals(android.view.View.GONE, broken.visibility)
    }
}
