package com.reiniertutoriales.vireolumatv.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-site choices, the "hosts" table of schema 19; one row per host name. */
@Entity(tableName = "hosts", indices = [Index(value = ["host_name"], name = "hosts_name_idx", unique = true)])
class SiteSettings(@ColumnInfo(name = "host_name") val hostName: String) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
    /** One of the POPUP_BLOCK_* levels; null means [DEFAULT_POPUP_POLICY]. */
    @ColumnInfo(name = "popup_block_level") var popupBlockLevel: Int? = null
    var favicon: String? = null

    companion object {
        const val POPUPS_ALLOWED = 0
        const val POPUPS_ASK = 1
        const val POPUPS_BLOCK_AUTOMATIC = 2
        const val POPUPS_BLOCK_ALL = 3
        const val DEFAULT_POPUP_POLICY = POPUPS_BLOCK_AUTOMATIC
    }
}

/**
 * What the database said about [host] at [generation] (see SiteSettingsDao.generation): its row,
 * or null for "looked up, no row", which is different from never having looked.
 */
class SiteSettingsLookup(val host: String, val settings: SiteSettings?, val generation: Long)
