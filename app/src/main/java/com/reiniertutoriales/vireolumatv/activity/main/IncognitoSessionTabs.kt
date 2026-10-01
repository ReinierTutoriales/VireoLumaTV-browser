package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.model.dao.TabsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class IncognitoSessionTabs {
    private val mutex = Mutex()
    private var sanitized = false

    suspend fun load(dao: TabsDao): List<WebTabState> = mutex.withLock {
        if (!sanitized) {
            val stale = dao.getAll(true)
            val normal = dao.getAll(false)
            val normalThumbnails = normal.mapNotNull { it.thumbnailHash }.toSet()
            val normalStates = normal.mapNotNull { it.wvStateFileName }.toSet()
            withContext(Dispatchers.IO) {
                for (tab in stale) {
                    // Legacy hashes can refer to the same file in both modes.
                    if (tab.thumbnailHash in normalThumbnails) tab.thumbnailHash = null
                    if (tab.wvStateFileName in normalStates) tab.wvStateFileName = null
                    tab.removeFiles()
                }
            }
            dao.deleteAll(true)
            sanitized = true
        }
        dao.getAll(true)
    }
}
