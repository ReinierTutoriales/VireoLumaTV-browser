package com.reiniertutoriales.vireolumatv.activity.history

import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableValue
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryModel: ActiveModel() {
    val lastLoadedItems = ObservableValue<List<HistoryItem>>(ArrayList())
    private var loading = false
    var searchQuery = ""


    fun loadItems(offset: Long = 0) = modelScope.launch(Dispatchers.Main) {
        if (loading) {
            return@launch
        }
        loading = true
        try {
            lastLoadedItems.value = if ("" == searchQuery) {
                AppDatabase.db.historyDao().allByLimitOffset(offset)
            } else {
                val pattern = likePattern(searchQuery)
                AppDatabase.db.historyDao().search(pattern, pattern)
            }
        } finally {
            // An exception must not leave the model stuck in "loading" and every later load ignored.
            loading = false
        }
    }

    companion object {
        /** Contains-match for LIKE. The DAO declares ESCAPE '\\', so typed % _ and \\ match literally. */
        internal fun likePattern(query: String): String =
            "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}