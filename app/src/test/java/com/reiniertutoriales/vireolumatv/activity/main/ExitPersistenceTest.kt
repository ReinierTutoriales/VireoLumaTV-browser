package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Bundle
import android.os.Looper
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class ExitPersistenceTest {
    @Test fun exitReturnsBeforeDiskWriteAndPersistsAfterModelIsCleared() = runBlocking {
        val model = TabsModel()
        val mutex = TabsModel::class.java.getDeclaredField("saveMutex").let {
            it.isAccessible = true
            it.get(model) as Mutex
        }
        val tab = WebTabState(url = "https://exit.test", selected = true).apply {
            savedState = Bundle().apply { putString("history", "kept after exit") }
        }
        mutex.lock() // Hold the IO transaction so completion cannot race the assertion.
        val pending = model.saveTabBeforeExit(tab)
        try {
            assertFalse("onPause must return without waiting for disk", pending.isCompleted)
            assertEquals(0L, tab.id)
            model.clear()
        } finally { mutex.unlock() }
        withTimeout(10_000) {
            while (!pending.isCompleted) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(5)
            }
        }
        assertTrue(tab.id > 0)
        assertNotNull(tab.wvStateFileName)
        assertEquals(tab.id, AppDatabase.db.tabsDao().getAll(false).single { it.url == tab.url }.id)
        AppDatabase.db.tabsDao().delete(tab)
        tab.removeFiles()
    }
}
