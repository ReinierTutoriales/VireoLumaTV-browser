package com.reiniertutoriales.vireolumatv.singleton

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Installs from before 2.0.13 have a schema 19 database. Built from the exported 19.json with rows
 * in every table, it must open with today's code at schema 20 and show the same tabs, visits,
 * downloads, site choices and bookmarks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppDatabaseSchemaTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun installedVersion19(name: String, rows: SQLiteDatabase.() -> Unit): File {
        val schema = JSONObject(File("schemas/com.reiniertutoriales.vireolumatv.singleton.AppDatabase/19.json")
            .readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            sqlite.rows()
            sqlite.version = schema.getInt("version")
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder(context, AppDatabase::class.java, file.name)
        .addMigrations(SchemaMigrations.FROM_19_TO_20).allowMainThreadQueries().build()

    @Test fun everyRowTheBrowserShowsMovesToSchema20() = runBlocking {
        val file = installedVersion19("schema-19-to-20.db") {
            execSQL("INSERT INTO tabs (id, url, title, selected, thumbnailHash, faviconHash, incognito, position, " +
                "wv_state, wv_state_file, adblock, scale) VALUES (7, 'https://tab.test/', 'Tab', 1, 'p', 'i', 0, 3, " +
                "X'00', 'state-7', 0, 1.5)")
            execSQL("INSERT INTO tabs (id, url, title, selected, incognito, position) VALUES (8, 'https://private.test/', 'P', 0, 1, 0)")
            execSQL("INSERT INTO history (id, time, title, url, favicon, incognito) VALUES (4, 100, 'Visit', 'https://visit.test/', 'v.png', 0)")
            execSQL("INSERT INTO downloads (id, time, filename, filepath, url, size, bytes_received) " +
                "VALUES (5, 200, 'file.zip', '/sdcard/Download/file.zip', 'https://dl.test/file.zip', 10, 10)")
            execSQL("INSERT INTO hosts (id, host_name, popup_block_level, favicon) VALUES (6, 'site.test', 2, 's.png')")
            execSQL("INSERT INTO favorites (ID, TITLE, URL, PARENT, HOME_PAGE_BOOKMARK, I_ORDER, USEFUL) VALUES (1, 'Mine', 'https://mine.test/', 0, 0, 0, 0)")
            execSQL("INSERT INTO favorites (ID, TITLE, URL, PARENT, HOME_PAGE_BOOKMARK, I_ORDER, USEFUL) VALUES (2, 'In a folder', 'https://folder.test/', 9, 0, 0, 0)")
            execSQL("INSERT INTO favorites (ID, TITLE, URL, PARENT, HOME_PAGE_BOOKMARK, I_ORDER, USEFUL) VALUES (3, 'Old home tile', 'https://tile.test/', 0, 1, 0, 0)")
            execSQL("INSERT INTO favorites (ID, TITLE, URL, PARENT, HOME_PAGE_BOOKMARK, I_ORDER, USEFUL) VALUES (9, 'Folder', NULL, 0, 0, 0, 0)")
        }
        val db = open(file)
        try {
            val tab = db.tabDao().getAll(incognito = false).single()
            assertEquals(7L, tab.id)
            assertEquals("https://tab.test/", tab.url)
            assertEquals("Tab", tab.title)
            assertTrue(tab.selected)
            assertEquals("p", tab.thumbnailHash)
            assertEquals("i", tab.faviconHash)
            assertEquals(3, tab.position)
            assertEquals("state-7", tab.wvStateFileName)
            assertEquals(false, tab.adblock)
            assertEquals(1.5f, tab.scale)
            assertEquals(listOf(8L), db.tabDao().getAll(incognito = true).map { it.id })

            val visit = db.visitDao().getAll().single()
            assertEquals(listOf(4L, 100L), listOf(visit.id, visit.time))
            assertEquals(listOf("Visit", "https://visit.test/", "v.png"), listOf(visit.title, visit.url, visit.favicon))

            val download = db.downloadDao().getAll().single()
            assertEquals(listOf(5L, 200L, 10L, 10L), listOf(download.id, download.time, download.size, download.bytesReceived))
            assertEquals(listOf("file.zip", "/sdcard/Download/file.zip", "https://dl.test/file.zip"),
                listOf(download.filename, download.filepath, download.url))

            val site = db.siteSettingsDao().findByHostName("site.test")!!
            assertEquals(6L, site.id)
            assertEquals(2, site.popupBlockLevel)
            assertEquals("s.png", site.favicon)

            // Bookmarks, including one older versions filed in a folder; never the home tiles or folders.
            assertEquals(listOf("In a folder" to "https://folder.test/", "Mine" to "https://mine.test/"),
                db.favoriteDao().getAll().map { it.title to it.url })

            val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
                buildList { while (it.moveToNext()) add(it.getString(0)) }
            }
            for (old in listOf("tabs", "history", "downloads", "hosts", "favorites")) assertFalse(old, old in tables)
        } finally {
            db.close()
        }
    }

    @Test fun anEmptyInstallMovesToo() = runBlocking {
        val db = open(installedVersion19("schema-19-empty.db") {})
        try {
            assertTrue(db.favoriteDao().getAll().isEmpty())
            assertNull(db.siteSettingsDao().findByHostName("none.test"))
            assertTrue(db.favoriteDao().insertIfAbsent(com.reiniertutoriales.vireolumatv.model.Favorite().apply {
                title = "New"; url = "https://new.test/" }))
        } finally {
            db.close()
        }
    }
}
