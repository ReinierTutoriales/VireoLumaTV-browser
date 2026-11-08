package com.reiniertutoriales.vireolumatv.activity.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.utils.withDayHeaders

/**
 * The visits on screen, with a date header before each day, as Compose state. [rowCount] counts
 * the stored entries listed, which the model uses as the next-page boundary.
 */
class HistoryList {
    private val realItems = ArrayList<HistoryItem>()

    /** Headers and visits in display order. */
    var rows by mutableStateOf<List<HistoryItem>>(emptyList())
        private set

    /** Changes when a selection changes; the rows read it so check marks redraw. */
    var revision by mutableIntStateOf(0)
        private set

    val rowCount: Long get() = realItems.size.toLong()

    val selectedItems: List<HistoryItem> get() = realItems.filter { it.selected }

    var selectionMode by mutableStateOf(false)
        private set

    fun setSelectionMode(on: Boolean) {
        if (selectionMode == on) return
        selectionMode = on
        if (!on) realItems.forEach { it.selected = false }
        revision++
    }

    fun addItems(items: List<HistoryItem>) {
        if (items.isEmpty()) return
        realItems.addAll(items)
        update()
    }

    fun remove(item: HistoryItem) {
        realItems.remove(item)
        update()
    }

    fun remove(items: List<HistoryItem>) {
        realItems.removeAll(items.toSet())
        update()
    }

    fun erase() {
        realItems.clear()
        update()
    }

    fun toggleSelection(item: HistoryItem) {
        item.selected = !item.selected
        revision++
    }

    private fun update() {
        rows = withDayHeaders(realItems, { it.time }, HistoryItem::dayHeader)
    }
}
