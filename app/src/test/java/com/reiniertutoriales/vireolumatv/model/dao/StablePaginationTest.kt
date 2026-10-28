package com.reiniertutoriales.vireolumatv.model.dao

import android.app.Application
import androidx.room.Room
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class StablePaginationTest {
    @Test fun visitsWithEqualTimestampsHaveStablePagesAndSearchOrder() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val dao = db.historyDao()
            val ids = (1..102).map { n -> dao.insert(HistoryItem().apply {
                time = 1_000L
                title = "Visit $n"
                url = "https://example.test/$n"
            }) }
            assertEquals(ids.reversed().take(100), dao.allByLimitOffset(0).map { it.id })
            assertEquals(ids.reversed().drop(100), dao.allByLimitOffset(100).map { it.id })
            assertEquals(ids.last(), dao.last().single().id)
            assertEquals(ids.reversed().take(100), dao.search("%example.test%").map { it.id })
        } finally {
            db.close()
        }
    }

    @Test fun downloadsWithEqualTimestampsKeepTheSameOrderAcrossPages() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val dao = db.downloadDao()
            val ids = (1..102).map { n -> dao.insert(Download().apply {
                time = 1_000L
                filename = "file-$n"
                filepath = "/tmp/file-$n"
                url = "https://example.test/$n"
            }) }
            assertEquals(ids.reversed().take(100), dao.allByLimitOffset(0).map { it.id })
            assertEquals(ids.reversed().drop(100), dao.allByLimitOffset(100).map { it.id })
        } finally {
            db.close()
        }
    }
}
