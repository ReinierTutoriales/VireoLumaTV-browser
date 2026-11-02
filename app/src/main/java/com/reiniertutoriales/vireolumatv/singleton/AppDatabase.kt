package com.reiniertutoriales.vireolumatv.singleton

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.model.dao.DownloadDao
import com.reiniertutoriales.vireolumatv.model.dao.FavoriteDao
import com.reiniertutoriales.vireolumatv.model.dao.VisitDao
import com.reiniertutoriales.vireolumatv.model.dao.SiteSettingsDao
import com.reiniertutoriales.vireolumatv.model.dao.TabDao

/**
 * The browser's database, "main.db", at schema 20 (app/schemas/.../20.json). Installs from before
 * 2.0.13 are at schema 19 and move to 20 through [SchemaMigrations.FROM_19_TO_20], keeping their rows.
 * A later schema change raises the version and adds its migration, tested against the exported JSON.
 */
@Database(
    entities = [Download::class, Favorite::class, HistoryItem::class, BrowserTab::class, SiteSettings::class],
    version = 20,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun visitDao(): VisitDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun tabDao(): TabDao
    abstract fun siteSettingsDao(): SiteSettingsDao

    companion object {
        const val FILE_NAME = "main.db"

        val db: AppDatabase by lazy {
            Room.databaseBuilder(VireoLumaTVApp.instance, AppDatabase::class.java, FILE_NAME)
                .addMigrations(SchemaMigrations.FROM_19_TO_20)
                .build()
        }
    }
}
