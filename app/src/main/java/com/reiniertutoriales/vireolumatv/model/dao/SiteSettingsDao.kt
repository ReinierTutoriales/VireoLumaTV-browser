package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.reiniertutoriales.vireolumatv.model.SiteSettings

/** Independent site fields must not overwrite one another through stale cached SiteSettings rows. */
@Dao
abstract class SiteSettingsDao {
    @Query("SELECT * FROM hosts WHERE host_name = :name LIMIT 1")
    abstract fun findByHostName(name: String): SiteSettings?

    @Query("SELECT * FROM hosts WHERE host_name = :name LIMIT 1")
    protected abstract suspend fun load(name: String): SiteSettings

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun addIfMissing(item: SiteSettings): Long

    @Query("UPDATE hosts SET favicon = :favicon WHERE host_name = :host")
    protected abstract suspend fun writeFavicon(host: String, favicon: String?)

    @Query("UPDATE hosts SET popup_block_level = :level WHERE host_name = :host")
    protected abstract suspend fun writePopupLevel(host: String, level: Int)

    @Transaction
    open suspend fun ensureHost(host: String): SiteSettings {
        addIfMissing(SiteSettings(host))
        return load(host)
    }

    @Transaction
    open suspend fun storeFavicon(host: String, favicon: String?) {
        addIfMissing(SiteSettings(host))
        writeFavicon(host, favicon)
    }

    @Transaction
    open suspend fun storePopupLevel(host: String, level: Int) {
        addIfMissing(SiteSettings(host))
        writePopupLevel(host, level)
    }
}
