package com.reiniertutoriales.vireolumatv.singleton

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Every install starts at schema 19. A database built from the exported 19.json, as an installed
 * app has it, must open with today's code and keep its rows; a schema change without a version
 * bump and migration fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppDatabaseSchemaTest {
    @Test fun installedVersion19DatabaseOpensAndKeepsItsRows() = runBlocking {
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
            sqlite.version = schema.getInt("version")
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.name).allowMainThreadQueries().build()
        try {
            assertEquals(listOf("Kept"), db.historyDao().getAll().map(HistoryItem::title))
            // The retired Date converter stored milliseconds in this unchanged INTEGER column.
            db.openHelper.writableDatabase.execSQL("UPDATE favorites SET PARENT = 0")
            val bookmark = db.favoritesDao().getAll().single()
            assertEquals(123456789L, bookmark.validUntil)
            db.favoritesDao().update(bookmark.copyForEdit().apply { title = "Edited" })
            assertEquals(123456789L, db.favoritesDao().getAll().single().validUntil)
        } finally {
            db.close()
        }
    }
}
