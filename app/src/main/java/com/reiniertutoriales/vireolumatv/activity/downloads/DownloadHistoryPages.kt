package com.reiniertutoriales.vireolumatv.activity.downloads

import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModel
import com.reiniertutoriales.vireolumatv.utils.observable.LiveValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Stored downloads, read a page at a time; [loadedPage] announces each new page. */
class DownloadHistoryPages : ScreenModel() {
    val allItems = ArrayList<Download>()
    val loadedPage = LiveValue<List<Download>>(emptyList())
    private var loading = false
    private var beforeTime: Long? = null
    private var beforeId = 0L
    private var exhausted = false

    fun loadNextItems() = workScope.launch(Dispatchers.Main) {
        if (loading || exhausted) return@launch
        loading = true
        try {
            val page = AppDatabase.db.downloadDao().page(beforeTime, beforeId)
            page.lastOrNull()?.let { beforeTime = it.time; beforeId = it.id }
            exhausted = page.size < 100
            allItems += page
            loadedPage.value = page
        } finally {
            loading = false // A failed page must not block the next one.
        }
    }
}
