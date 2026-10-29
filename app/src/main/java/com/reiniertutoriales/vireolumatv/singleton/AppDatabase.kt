package com.reiniertutoriales.vireolumatv.singleton

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.model.dao.DownloadDao
import com.reiniertutoriales.vireolumatv.model.dao.VisitDao
import com.reiniertutoriales.vireolumatv.model.dao.SiteSettingsDao
import com.reiniertutoriales.vireolumatv.model.dao.TabDao

/**
 * The browser's database, "main.db". Every install of this application ID was created at
 * version 19 (app/schemas/.../19.json). Version 20 removes favorites at the owner's request
 * (2026-10-29): [MIGRATION_19_20] deletes that table and its rows and leaves every other table as
 * it was. A future schema change must raise the version and add its migration here, tested
 * against the exported schemas.
 */
@Database(
    entities = [Download::class, HistoryItem::class, BrowserTab::class, SiteSettings::class],
    version = 20,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun visitDao(): VisitDao
    abstract fun tabDao(): TabDao
    abstract fun siteSettingsDao(): SiteSettingsDao

    companion object {
        const val FILE_NAME = "main.db"

        /** Favorites were removed; their table and rows go with them. */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS favorites")
            }
        }

        val db: AppDatabase by lazy {
            Room.databaseBuilder(VireoLumaTVApp.instance, AppDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_19_20).build()
        }
    }
}
