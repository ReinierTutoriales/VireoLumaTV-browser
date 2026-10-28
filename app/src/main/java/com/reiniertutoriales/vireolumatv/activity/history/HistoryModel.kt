package com.reiniertutoriales.vireolumatv.activity.history

import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.model.dao.HistoryDao
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** History pages use the last delivered row, not the number of rows still visible. */
class HistoryModel(private val history: () -> HistoryDao = { AppDatabase.db.historyDao() }) : ActiveModel() {
    val lastLoadedItems = ObservableValue<List<HistoryItem>>(emptyList())
    var searchQuery = ""
    private var request: Job? = null
    private var beforeTime: Long? = null
    private var beforeId = 0L
    private var exhausted = false

    fun loadItems(append: Boolean = false): Job {
        if (append && (request?.isActive == true || exhausted)) return request ?: Job().apply { complete() }
        if (!append) {
            request?.cancel()
            beforeTime = null
            beforeId = 0
            exhausted = false
            lastLoadedItems.value = emptyList()
        }
        val query = searchQuery
        return modelScope.launch {
            val dao = history()
            val rows = if (query.isEmpty()) dao.page(beforeTime, beforeId) else dao.search(likePattern(query))
            rows.lastOrNull()?.let { beforeTime = it.time; beforeId = it.id }
            exhausted = query.isNotEmpty() || rows.size < 100
            lastLoadedItems.value = rows
        }.also { request = it }
    }

    companion object {
        internal fun likePattern(query: String): String = buildString {
            append('%')
            query.forEach { char ->
                if (char == '%' || char == '_' || char == '\\') append('\\')
                append(char)
            }
            append('%')
        }
    }
}
