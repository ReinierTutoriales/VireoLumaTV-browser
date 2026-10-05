package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import androidx.room.Room
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
class AtomicTabSaveTest {
    @Test fun switchingSelectionKeepsExactlyOneSelectedTabPerModeAndUpdatesExistingRow() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.tabsDao()
            dao.save(WebTabState(url = "https://one.test", selected = true))
            dao.save(WebTabState(url = "https://private.test", selected = true, incognito = true))
            val next = WebTabState(url = "https://two.test", selected = true)
            next.id = dao.save(next)
            next.title = "Updated"
            assertEquals(next.id, dao.save(next))
            val normal = dao.getAll(false)
            assertEquals(2, normal.size)
            assertEquals(next.id, normal.single { it.selected }.id)
            assertEquals("Updated", normal.single { it.selected }.title)
            assertTrue(dao.getAll(true).single().selected)
        } finally { db.close() }
    }

    @Test fun pendingSaveCannotReinsertAnAlreadyClosedTab() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        com.reiniertutoriales.vireolumatv.AppContext.init(app,
            com.reiniertutoriales.vireolumatv.Config(app.getSharedPreferences("closed-tab-test", 0)))
        val model = TabsModel()
        val tab = WebTabState(url = "https://closed.test").apply { closed = true }
        model.saveTab(tab)
        assertEquals(0L, tab.id)
    }

    @Test fun failedInsertRollsBackUnselection() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.tabsDao()
            val original = dao.save(WebTabState(url = "https://original.test", selected = true))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_insert BEFORE INSERT ON tabs BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            var failed = false
            try { dao.save(WebTabState(url = "https://failed.test", selected = true)) }
            catch (_: Exception) { failed = true }
            assertTrue(failed)
            val remaining = dao.getAll(false).single()
            assertEquals(original, remaining.id)
            assertTrue(remaining.selected)
        } finally { db.close() }
    }
}
