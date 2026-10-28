package com.reiniertutoriales.vireolumatv.activity.history

import android.app.Application
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.model.dao.HistoryDao
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class HistoryRequestTest {
    @Test fun aNewSearchReplacesAnInFlightPageInsteadOfBeingDropped() {
        val waiting = CompletableDeferred<List<HistoryItem>>()
        val dao = object : HistoryDao {
            override suspend fun page(beforeTime: Long?, beforeId: Long) = waiting.await()
            override suspend fun search(pattern: String) = listOf(HistoryItem().apply { title = pattern })
            override suspend fun getAll() = emptyList<HistoryItem>()
            override suspend fun exceeds(threshold: Int) = false
            override suspend fun last(limit: Int) = emptyList<HistoryItem>()
            override suspend fun insert(item: HistoryItem) = 0L
            override suspend fun updateTitle(id: Long, title: String) {}
            override suspend fun delete(vararg item: HistoryItem) {}
            override suspend fun deleteWhereTimeLessThan(time: Long) {}
        }
        val model = HistoryModel { dao }
        try {
            val previous = model.loadItems()
            ShadowLooper.idleMainLooper()
            assertTrue(previous.isActive)
            model.searchQuery = "new%"
            model.loadItems()
            ShadowLooper.idleMainLooper()
            assertTrue(previous.isCancelled)
            assertEquals("%new\\%%", model.lastLoadedItems.value.single().title)
            waiting.complete(listOf(HistoryItem().apply { title = "Stale" }))
            ShadowLooper.idleMainLooper()
            assertEquals("%new\\%%", model.lastLoadedItems.value.single().title)
        } finally { model.clear() }
    }
}
