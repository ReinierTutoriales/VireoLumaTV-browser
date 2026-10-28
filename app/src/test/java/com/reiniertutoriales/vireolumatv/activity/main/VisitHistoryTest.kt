package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@RoboConfig(application = VireoLumaTVApp::class, sdk = [28])
class VisitHistoryTest {
    @Before fun emptyHistory() = runBlocking {
        // Private mode skips history; other tests may leave it on in the shared settings.
        com.reiniertutoriales.vireolumatv.AppContext.config.privateBrowsing = false
        withContext(Dispatchers.IO) { AppDatabase.db.clearAllTables() }
    }

    private fun stored(): List<String> = runBlocking { withContext(Dispatchers.IO) { AppDatabase.db.visitDao().getAll().map { "${it.title}|${it.url}" } } }

    /** Lets the settle delay pass and the database write finish. */
    private fun settle(expected: Int) {
        ShadowLooper.idleMainLooper(6, TimeUnit.SECONDS)
        val deadline = System.currentTimeMillis() + 5_000
        while (stored().size < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
        }
    }

    @Test fun aQuickHopIsReplacedAndOnlyWebPagesAreStored() {
        val model = VisitLog()
        model.recordVisit("Redirect", "https://a.test/start", null)
        model.recordVisit("Final", "https://a.test/final", null)
        model.recordVisit("Home", Config.HOME_PAGE_URL, null)
        model.recordVisit("File", "file:///sdcard/x.html", null)
        settle(1)
        assertEquals(listOf("Final|https://a.test/final"), stored())
    }

    @Test fun aLateTitleUpdatesTheStoredVisit() {
        val model = VisitLog()
        model.recordVisit(null, "https://b.test/", null)
        settle(1)
        model.onTabTitle(BrowserTab(url = "https://b.test/", title = "Loaded title"))
        val deadline = System.currentTimeMillis() + 5_000
        while (stored() != listOf("Loaded title|https://b.test/") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
        }
        assertEquals(listOf("Loaded title|https://b.test/"), stored())
    }
}
