package com.reiniertutoriales.vireolumatv.singleton

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.model.HostConfig
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.model.dao.DownloadDao
import com.reiniertutoriales.vireolumatv.model.dao.FavoritesDao
import com.reiniertutoriales.vireolumatv.model.dao.HistoryDao
import com.reiniertutoriales.vireolumatv.model.dao.HostsDao
import com.reiniertutoriales.vireolumatv.model.dao.TabsDao
import com.reiniertutoriales.vireolumatv.model.util.Converters

/**
 * The browser's database, "main.db". Every install of this application ID was created at
 * version 19 (app/schemas/.../19.json), so there is no older version to migrate from. A future
 * schema change must raise the version and add its migration here, tested against 19.json.
 */
@Database(
    entities = [Download::class, FavoriteItem::class, HistoryItem::class, WebTabState::class, HostConfig::class],
    version = 19,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun tabsDao(): TabsDao
    abstract fun hostsDao(): HostsDao

    companion object {
        const val FILE_NAME = "main.db"

        val db: AppDatabase by lazy {
            Room.databaseBuilder(VireoLumaTVApp.instance, AppDatabase::class.java, FILE_NAME).build()
        }
    }
}
