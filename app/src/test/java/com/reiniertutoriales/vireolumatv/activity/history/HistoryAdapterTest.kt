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

/** Behavior the history list relies on; pins it before the list implementation changes. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class HistoryAdapterTest {
    private val day = 1_700_000_000_000L
    private val nextDay = day + 2 * 24 * 60 * 60 * 1000L

    private fun item(time: Long, url: String) = HistoryItem().apply {
        this.time = time
        this.url = url
    }

    @Test
    fun addItemsInsertsOneHeaderPerDayAndCountsOnlyRealItems() {
        val adapter = HistoryAdapter()
        adapter.addItems(listOf(item(day, "a"), item(day + 1000, "b"), item(nextDay, "c")))

        assertEquals(5, adapter.count)
        assertEquals(3L, adapter.realCount)
        assertTrue(adapter.items[0].isDateHeader)
        assertFalse(adapter.items[1].isDateHeader)
        assertTrue(adapter.items[3].isDateHeader)
    }

    @Test
    fun addingNothingChangesNothing() {
        val adapter = HistoryAdapter()
        adapter.addItems(emptyList())
        assertEquals(0, adapter.count)
        assertEquals(0L, adapter.realCount)
    }

    @Test
    fun selectionIsReturnedInListOrder() {
        val adapter = HistoryAdapter()
        adapter.addItems(listOf(item(day, "a"), item(day + 1, "b"), item(day + 2, "c")))
        adapter.items.first { it.url == "c" }.selected = true
        adapter.items.first { it.url == "a" }.selected = true

        assertEquals(listOf("a", "c"), adapter.selectedItems.map { it.url })
    }

    @Test
    fun leavingMultiselectClearsSelection() {
        val adapter = HistoryAdapter()
        adapter.addItems(listOf(item(day, "a")))
        adapter.isMultiselectMode = true
        adapter.items.first { !it.isDateHeader }.selected = true

        adapter.isMultiselectMode = false

        assertTrue(adapter.selectedItems.isEmpty())
    }

    @Test
    fun removeAndEraseUpdateTheList() {
        val adapter = HistoryAdapter()
        val a = item(day, "a")
        val b = item(day + 1, "b")
        adapter.addItems(listOf(a, b))

        adapter.remove(a)
        assertEquals(listOf("b"), adapter.items.filter { !it.isDateHeader }.map { it.url })

        adapter.remove(listOf(b))
        assertEquals(1, adapter.count)
        adapter.erase()
        assertEquals(0, adapter.count)
    }
}
