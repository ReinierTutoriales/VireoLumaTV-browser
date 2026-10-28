package com.reiniertutoriales.vireolumatv.activity.history

import androidx.recyclerview.widget.AsyncDifferConfig
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.HistoryItem
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
 * Behavior the history list relies on. Diffing runs on a direct executor here so each
 * submitted list is applied deterministically; production uses the background default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class HistoryAdapterTest {
    private val day = 1_700_000_000_000L
    private val nextDay = day + 2 * 24 * 60 * 60 * 1000L

    private fun adapter() = HistoryRows({}, { false }, AsyncDifferConfig.Builder(HistoryRows.DIFF_CALLBACK)
        .setBackgroundThreadExecutor(Executor { it.run() })
        .build())

    private fun HistoryRows.settle() = also { ShadowLooper.idleMainLooper() }

    private fun item(time: Long, url: String, id: Long = time) = HistoryItem().apply {
        this.id = id
        this.time = time
        this.url = url
    }

    private fun HistoryRows.rows() = currentList.filter { !it.isDateHeader }.map { it.url }

    @Test
    fun addItemsInsertsOneHeaderPerDayAndCountsOnlyRealItems() {
        val adapter = adapter().apply { addItems(listOf(item(day, "a"), item(day + 1000, "b"), item(nextDay, "c"))) }.settle()

        assertEquals(5, adapter.itemCount)
        assertEquals(3L, adapter.realCount)
        assertTrue(adapter.currentList[0].isDateHeader)
        assertFalse(adapter.currentList[1].isDateHeader)
        assertTrue(adapter.currentList[3].isDateHeader)
    }

    @Test
    fun addingNothingChangesNothing() {
        val adapter = adapter().apply { addItems(emptyList()) }.settle()
        assertEquals(0, adapter.itemCount)
        assertEquals(0L, adapter.realCount)
    }

    @Test
    fun selectionIsReturnedInListOrder() {
        val adapter = adapter().apply { addItems(listOf(item(day, "a", 1), item(day + 1, "b", 2), item(day + 2, "c", 3))) }.settle()
        adapter.toggleSelection(adapter.currentList.first { it.url == "c" })
        adapter.toggleSelection(adapter.currentList.first { it.url == "a" })

        assertEquals(listOf("a", "c"), adapter.selectedItems.map { it.url })
    }

    @Test
    fun leavingMultiselectClearsSelection() {
        val adapter = adapter().apply { addItems(listOf(item(day, "a"))) }.settle()
        adapter.isMultiselectMode = true
        adapter.toggleSelection(adapter.currentList.first { !it.isDateHeader })

        adapter.isMultiselectMode = false

        assertTrue(adapter.selectedItems.isEmpty())
    }

    @Test
    fun removeAndEraseUpdateTheList() {
        val a = item(day, "a", 1)
        val b = item(day + 1, "b", 2)
        val adapter = adapter().apply { addItems(listOf(a, b)) }.settle()

        adapter.remove(a)
        assertEquals(listOf("b"), adapter.settle().rows())
        adapter.remove(listOf(b))
        assertEquals(0, adapter.settle().itemCount)
        adapter.addItems(listOf(item(day, "c", 3)))
        adapter.settle().erase()
        assertEquals(0, adapter.settle().itemCount)
    }

    @Test
    fun removingEveryItemOfADayRemovesItsHeader() {
        val a = item(day, "a", 1)
        val adapter = adapter().apply { addItems(listOf(a, item(nextDay, "b", 2))) }.settle()

        adapter.remove(a)

        assertEquals(2, adapter.settle().itemCount)
        assertTrue(adapter.currentList[0].isDateHeader)
        assertEquals(listOf("b"), adapter.rows())
    }

    @Test
    fun realCountFollowsWhatIsOnScreenSoPagingOffsetMatchesTheDatabase() {
        val a = item(day, "a", 1)
        val adapter = adapter().apply { addItems(listOf(a, item(day + 1, "b", 2))) }.settle()

        adapter.remove(a)

        adapter.settle()
        assertEquals("Offset for the next page must equal the stored rows still listed", 1L, adapter.realCount)
    }
}
