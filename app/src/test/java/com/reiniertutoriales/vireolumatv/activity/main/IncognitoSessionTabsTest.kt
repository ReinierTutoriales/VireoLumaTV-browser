package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import androidx.room.Room
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig
import java.io.File

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(application = Application::class, sdk = [28])
class IncognitoSessionTabsTest {
    @Test fun newPrivatePreviewsAndSavedStatesCannotOverwriteNormalFiles() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        AppContext.init(app, Config(app.getSharedPreferences("tab-files-test", 0)))
        val normal = WebTabState(url = "https://same.test")
        val privateTab = WebTabState(url = normal.url, incognito = true)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        normal.updateThumbnail(app, bitmap)
        privateTab.updateThumbnail(app, bitmap)
        assertNotNull(normal.thumbnailHash)
        assertNotEquals(normal.thumbnailHash, privateTab.thumbnailHash)
        assertTrue(privateTab.thumbnailHash!!.startsWith("private-"))
        val state = Bundle().apply { putString("url", normal.url) }
        normal.savedState = state
        normal.saveWebViewStateToFile()
        privateTab.savedState = state
        privateTab.wvStateFileName = normal.wvStateFileName
        privateTab.saveWebViewStateToFile()
        assertNotNull(normal.wvStateFileName)
        assertNotEquals(normal.wvStateFileName, privateTab.wvStateFileName)
        assertTrue(privateTab.wvStateFileName!!.startsWith("private-"))
        privateTab.removeFiles()
        assertTrue(File(app.filesDir, "${WebTabState.TAB_WVSTATES_DIR}/${normal.wvStateFileName}").exists())
        assertTrue(File(app.cacheDir, "${WebTabState.TAB_THUMBNAILS_DIR}/${normal.thumbnailHash}.png").exists())
    }

    @Test fun clearsOldPrivateTabsProtectsSharedFilesAndPreservesCurrentSession() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        AppContext.init(app, Config(app.getSharedPreferences("tabs-test", 0)))
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.tabsDao()
            dao.insert(WebTabState(url = "https://normal.test", thumbnailHash = "shared", wvStateFileName = "shared-state"))
            dao.insert(WebTabState(url = "https://private.test", incognito = true, thumbnailHash = "shared", wvStateFileName = "shared-state"))
            dao.insert(WebTabState(url = "https://old.test", incognito = true, thumbnailHash = "private", wvStateFileName = "private-state"))
            val thumbs = File(app.cacheDir, WebTabState.TAB_THUMBNAILS_DIR).apply { mkdirs() }
            val states = File(app.filesDir, WebTabState.TAB_WVSTATES_DIR).apply { mkdirs() }
            File(thumbs, "shared.png").writeText("normal")
            File(thumbs, "private.png").writeText("private")
            File(states, "shared-state").writeText("normal")
            File(states, "private-state").writeText("private")
            val session = IncognitoSessionTabs()
            assertTrue(session.load(dao).isEmpty())
            assertEquals(1, dao.getAll(false).size)
            assertTrue(File(thumbs, "shared.png").exists())
            assertTrue(File(states, "shared-state").exists())
            assertFalse(File(thumbs, "private.png").exists())
            assertFalse(File(states, "private-state").exists())
            dao.insert(WebTabState(url = "https://active.test", incognito = true))
            assertEquals("https://active.test", session.load(dao).single().url)
        } finally { db.close() }
    }
}
