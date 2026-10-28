package com.reiniertutoriales.vireolumatv.model.dao

import android.app.Application
import androidx.room.Room
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class StorageWriteContractTest {
    private fun database() = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries().build()

    @Test fun independentHostFieldsPreserveEachOtherAndTheRowIdentity() = runBlocking {
        val db = database()
        try {
            val hosts = db.hostsDao()
            val original = hosts.ensureHost("site.test")
            hosts.storePopupLevel(original.hostName, 3)
            // A favicon completion based on this older row must not restore its null popup level.
            hosts.storeFavicon(original.hostName, "first.png")
            val stale = requireNotNull(hosts.findByHostName("site.test"))
            hosts.storeFavicon("site.test", "new.png")
            hosts.storePopupLevel(stale.hostName, 1)
            val result = hosts.ensureHost("site.test")
            assertEquals(original.id, result.id)
            assertEquals(1, result.popupBlockLevel)
            assertEquals("new.png", result.favicon)
            hosts.storeFavicon("created-by-icon.test", "icon.png")
            val created = requireNotNull(hosts.findByHostName("created-by-icon.test"))
            assertEquals("icon.png", created.favicon)
            assertNull(created.popupBlockLevel)
        } finally { db.close() }
    }

    @Test fun editingAStaleBookmarkPreservesOtherStoredColumns() = runBlocking {
        val db = database()
        try {
            val bookmarks = db.favoritesDao()
            bookmarks.insert(FavoriteItem().apply { title = "Before"; url = "https://before.test"; validUntil = 123L })
            val stale = bookmarks.getAll().single().copyForEdit()
            db.openHelper.writableDatabase.execSQL("UPDATE favorites SET DESCRIPTION = 'kept', VALID_UNTIL = 456")
            bookmarks.update(stale.apply { title = "After"; url = "https://after.test" })
            val result = bookmarks.getAll().single()
            assertEquals("After", result.title)
            assertEquals("https://after.test", result.url)
            assertEquals("kept", result.description)
            assertEquals(456L, result.validUntil)
        } finally { db.close() }
    }

    @Test fun transferProgressCannotChangeTheOriginalVisitTimeOrAddress() = runBlocking {
        val db = database()
        try {
            val downloads = db.downloadDao()
            val id = downloads.insert(Download().apply { time = 123L; url = "https://original.test" })
            downloads.update(Download().apply {
                this.id = id; time = 999L; url = "https://stale.test"
                filename = "resolved.apk"; filepath = "content://downloads/1"; size = 50; bytesReceived = 25
            })
            val result = downloads.getAll().single()
            assertEquals(123L, result.time)
            assertEquals("https://original.test", result.url)
            assertEquals("resolved.apk", result.filename)
            assertEquals("content://downloads/1", result.filepath)
            assertEquals(50L, result.size)
            assertEquals(25L, result.bytesReceived)
        } finally { db.close() }
    }

    @Test fun historyTrimDecisionUsesStrictlyMoreThanTheThreshold() = runBlocking {
        val db = database()
        try {
            val history = db.historyDao()
            assertFalse(history.exceeds(0))
            history.insert(com.reiniertutoriales.vireolumatv.model.HistoryItem())
            assertTrue(history.exceeds(0))
            assertFalse(history.exceeds(1))
        } finally { db.close() }
    }

    @Test fun reorderedTabsAreSnapshottedBeforeTheFirstWriteSuspends() = runBlocking {
        val tabs = listOf(WebTabState(id = 7, position = 0), WebTabState(id = 9, position = 1))
        val written = mutableListOf<Pair<Long, Int>>()
        val storage = object : TabsDao {
            override suspend fun getAll(incognito: Boolean) = emptyList<WebTabState>()
            override suspend fun insert(item: WebTabState) = 0L
            override suspend fun update(item: WebTabState) {}
            override suspend fun delete(item: WebTabState) {}
            override suspend fun deleteAll(incognito: Boolean) {}
            override suspend fun unselectAll(incognito: Boolean) {}
            override suspend fun updatePosition(position: Int, id: Long) {
                written += id to position
                if (written.size == 1) tabs[1].position = 99
            }
        }
        storage.updatePositions(tabs)
        assertEquals(listOf(7L to 0, 9L to 1), written)
    }
}
