package com.reiniertutoriales.vireolumatv.singleton

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Every install starts at schema 19. A database built from the exported 19.json, as an installed
 * app has it, must open with today's code: version 20 drops the favorites table and keeps every
 * other table's rows. A schema change without a version bump and migration fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppDatabaseSchemaTest {
    @Test fun installedVersion19DatabaseLosesOnlyFavorites() = runBlocking {
        val schema = JSONObject(File("schemas/com.reiniertutoriales.vireolumatv.singleton.AppDatabase/19.json")
            .readText()).getJSONObject("database")
        val context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath("schema-19-test.db").apply { parentFile?.mkdirs(); delete() }
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
            sqlite.execSQL("INSERT INTO history (time, title, url) VALUES (1, 'Kept', 'https://kept.test')")
            sqlite.execSQL("INSERT INTO favorites (TITLE, URL, HOME_PAGE_BOOKMARK, I_ORDER, VALID_UNTIL, USEFUL) VALUES ('Legacy', 'https://legacy.test', 0, 0, 123456789, 0)")
            sqlite.execSQL("INSERT INTO tabs (url, title, selected, incognito, position) VALUES ('https://tab.test', 'Tab', 1, 0, 0)")
            sqlite.execSQL("INSERT INTO downloads (time, filename, filepath, url, size, bytes_received) VALUES (1, 'kept.apk', '/d/kept.apk', 'https://d.test/kept.apk', 10, 10)")
            sqlite.execSQL("INSERT INTO hosts (host_name, popup_block_level) VALUES ('site.test', 1)")
            sqlite.version = schema.getInt("version")
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.name)
            .addMigrations(AppDatabase.MIGRATION_19_20).allowMainThreadQueries().build()
        try {
            assertEquals(listOf("Kept"), db.visitDao().getAll().map(HistoryItem::title))
            assertEquals(listOf("https://tab.test"), db.tabDao().getAll().map { it.url })
            assertEquals(listOf("kept.apk"), db.downloadDao().getAll().map { it.filename })
            assertEquals(1, db.siteSettingsDao().findByHostName("site.test")?.popupBlockLevel)
            val sqlite = db.openHelper.readableDatabase
            assertEquals(20, sqlite.version)
            sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'favorites'").use {
                assertFalse("Favorites and their rows are deleted", it.moveToFirst())
            }
        } finally {
            db.close()
        }
    }
}
