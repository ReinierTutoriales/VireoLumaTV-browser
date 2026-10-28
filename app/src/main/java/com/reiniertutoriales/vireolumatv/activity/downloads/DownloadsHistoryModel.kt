package com.reiniertutoriales.vireolumatv.activity.downloads

import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Stored downloads, read a page at a time; [lastLoadedItems] announces each new page. */
class DownloadsHistoryModel : ActiveModel() {
    val allItems = ArrayList<Download>()
    val lastLoadedItems = ObservableValue<List<Download>>(emptyList())
    private var loading = false

    fun loadNextItems() = modelScope.launch(Dispatchers.Main) {
        if (loading) return@launch
        loading = true
        try {
            val page = AppDatabase.db.downloadDao().allByLimitOffset(allItems.size.toLong())
            allItems += page
            lastLoadedItems.value = page
        } finally {
            loading = false // A failed page must not block the next one.
        }
    }
}
