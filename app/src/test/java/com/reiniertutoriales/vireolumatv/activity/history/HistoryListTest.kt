package com.reiniertutoriales.vireolumatv.activity.history

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Behavior the history list relies on: day headers, paging count, selection and removal. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class HistoryListTest {
    private val day = 1_700_000_000_000L
    private val nextDay = day + 2 * 24 * 60 * 60 * 1000L

    private fun newList() = HistoryList()

    private fun item(time: Long, url: String, id: Long = time) = HistoryItem().apply {
        this.id = id
        this.time = time
        this.url = url
    }

    private fun HistoryList.urls() = rows.filter { !it.isDayHeader }.map { it.url }

    @Test
    fun addItemsInsertsOneHeaderPerDayAndCountsOnlyRealItems() {
        val list = newList().apply { addItems(listOf(item(day, "a"), item(day + 1000, "b"), item(nextDay, "c"))) }

        assertEquals(5, list.rows.size)
        assertEquals(3L, list.rowCount)
        assertTrue(list.rows[0].isDayHeader)
        assertFalse(list.rows[1].isDayHeader)
        assertTrue(list.rows[3].isDayHeader)
    }

    @Test
    fun addingNothingChangesNothing() {
        val list = newList().apply { addItems(emptyList()) }
        assertEquals(0, list.rows.size)
        assertEquals(0L, list.rowCount)
    }

    @Test
    fun selectionIsReturnedInListOrder() {
        val list = newList().apply { addItems(listOf(item(day, "a", 1), item(day + 1, "b", 2), item(day + 2, "c", 3))) }
        list.toggleSelection(list.rows.first { it.url == "c" })
        list.toggleSelection(list.rows.first { it.url == "a" })

        assertEquals(listOf("a", "c"), list.selectedItems.map { it.url })
    }

    @Test
    fun leavingMultiselectClearsSelection() {
        val list = newList().apply { addItems(listOf(item(day, "a"))) }
        list.selectionMode = true
        list.toggleSelection(list.rows.first { !it.isDayHeader })

        list.selectionMode = false

        assertTrue(list.selectedItems.isEmpty())
    }

    @Test
    fun removeAndEraseUpdateTheList() {
        val a = item(day, "a", 1)
        val b = item(day + 1, "b", 2)
        val list = newList().apply { addItems(listOf(a, b)) }

        list.remove(a)
        assertEquals(listOf("b"), list.urls())
        list.remove(listOf(b))
        assertEquals(0, list.rows.size)
        list.addItems(listOf(item(day, "c", 3)))
        list.erase()
        assertEquals(0, list.rows.size)
    }

    @Test
    fun removingEveryItemOfADayRemovesItsHeader() {
        val a = item(day, "a", 1)
        val list = newList().apply { addItems(listOf(a, item(nextDay, "b", 2))) }

        list.remove(a)

        assertEquals(2, list.rows.size)
        assertTrue(list.rows[0].isDayHeader)
        assertEquals(listOf("b"), list.urls())
    }

    @Test
    fun realCountFollowsWhatIsOnScreenSoPagingOffsetMatchesTheDatabase() {
        val a = item(day, "a", 1)
        val list = newList().apply { addItems(listOf(a, item(day + 1, "b", 2))) }

        list.remove(a)

        assertEquals("Offset for the next page must equal the stored rows still listed", 1L, list.rowCount)
    }
}
