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
 * The browser's database, "main.db". Every install of this application ID was created at
 * version 19 (app/schemas/.../19.json), so there is no older version to migrate from. A future
 * schema change must raise the version and add its migration here, tested against 19.json.
 */
@Database(
    entities = [Download::class, Favorite::class, HistoryItem::class, BrowserTab::class, SiteSettings::class],
    version = 19,
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
            Room.databaseBuilder(VireoLumaTVApp.instance, AppDatabase::class.java, FILE_NAME).build()
        }
    }
}
