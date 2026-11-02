package com.reiniertutoriales.vireolumatv.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Per-site choices, the "site_settings" table; one row per host name. */
@Entity(tableName = "site_settings", indices = [Index(value = ["host"], name = "site_settings_host_idx", unique = true)])
class SiteSettings(@ColumnInfo(name = "host") val hostName: String) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
    /** One of the POPUP_BLOCK_* levels; null means [DEFAULT_POPUP_POLICY]. */
    @ColumnInfo(name = "popup_policy") var popupBlockLevel: Int? = null
    @ColumnInfo(name = "icon") var favicon: String? = null

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
