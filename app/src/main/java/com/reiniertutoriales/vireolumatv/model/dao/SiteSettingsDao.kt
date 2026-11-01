package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import java.util.concurrent.atomic.AtomicLong

/**
 * Independent site fields must not overwrite one another through stale cached SiteSettings rows.
 * Every write through this DAO advances [generation] once it has finished, so a lookup taken at an
 * earlier generation, a row or the absence of one, is known to be out of date.
 */
@Dao
abstract class SiteSettingsDao {
    /** The number of finished writes; a lookup is current while this has not moved since it began. */
    fun generation(): Long = writes.get()

    @Query("SELECT * FROM site_settings WHERE host = :name LIMIT 1")
    abstract fun findByHostName(name: String): SiteSettings?

    @Query("SELECT * FROM site_settings WHERE host = :name LIMIT 1")
    protected abstract suspend fun load(name: String): SiteSettings

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun addIfMissing(item: SiteSettings): Long

    @Query("UPDATE site_settings SET icon = :favicon WHERE host = :host")
    protected abstract suspend fun writeFavicon(host: String, favicon: String?)

    @Query("UPDATE site_settings SET popup_policy = :level WHERE host = :host")
    protected abstract suspend fun writePopupLevel(host: String, level: Int)

    @Transaction
    protected open suspend fun ensureHostRow(host: String): SiteSettings {
        addIfMissing(SiteSettings(host))
        return load(host)
    }

    @Transaction
    protected open suspend fun storeFaviconRow(host: String, favicon: String?) {
        addIfMissing(SiteSettings(host))
        writeFavicon(host, favicon)
    }

    @Transaction
    protected open suspend fun storePopupLevelRow(host: String, level: Int) {
        addIfMissing(SiteSettings(host))
        writePopupLevel(host, level)
    }

    suspend fun ensureHost(host: String): SiteSettings = ensureHostRow(host).also { writes.incrementAndGet() }

    suspend fun storeFavicon(host: String, favicon: String?) {
        storeFaviconRow(host, favicon)
        writes.incrementAndGet()
    }

    /** Returns the [generation] that includes this write. */
    suspend fun storePopupLevel(host: String, level: Int): Long {
        storePopupLevelRow(host, level)
        return writes.incrementAndGet()
    }

    private companion object {
        /** Process-wide: the database is one per process, and test doubles share the same rule. */
        val writes = AtomicLong()
    }
}
