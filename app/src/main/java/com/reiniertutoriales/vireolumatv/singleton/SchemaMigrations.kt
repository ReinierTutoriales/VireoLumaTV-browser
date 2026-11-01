package com.reiniertutoriales.vireolumatv.singleton

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 20 is the browser's own: new tables with their own names replace the five tables every
 * earlier install had (schema 19). Each row the browser shows is copied over in one transaction:
 * open tabs, visits, downloads, per-site choices and bookmarks. Not copied: columns no version of
 * this browser reads (a tab's in-row WebView state, a visit's private flag) and the home-page
 * tiles older versions kept among the favorites, which were never shown as bookmarks.
 */
internal object SchemaMigrations {
    val FROM_19_TO_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            create(db)
            db.execSQL("""INSERT INTO open_tabs (id, address, title, is_shown, preview_key, icon_key, is_private,
                sort_order, state_file, blocks_ads, zoom)
                SELECT id, url, title, selected, thumbnailHash, faviconHash, incognito, position, wv_state_file,
                adblock, scale FROM tabs""")
            db.execSQL("""INSERT INTO visits (id, visited_at, title, address, icon)
                SELECT id, time, title, url, favicon FROM history""")
            db.execSQL("""INSERT INTO download_entries (id, started_at, file_name, location, address, total_bytes,
                received_bytes)
                SELECT id, time, filename, filepath, url, size, bytes_received FROM downloads""")
            db.execSQL("""INSERT INTO site_settings (id, host, popup_policy, icon)
                SELECT id, host_name, popup_block_level, favicon FROM hosts""")
            db.execSQL("""INSERT INTO bookmarks (id, title, address)
                SELECT ID, TITLE, URL FROM favorites WHERE URL IS NOT NULL AND HOME_PAGE_BOOKMARK = 0""")
            for (old in listOf("tabs", "history", "downloads", "hosts", "favorites")) db.execSQL("DROP TABLE `$old`")
        }
    }

    /** Schema 20 exactly as exported in app/schemas/.../20.json. */
    private fun create(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `download_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`started_at` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `location` TEXT NOT NULL, `address` TEXT NOT NULL, " +
            "`total_bytes` INTEGER NOT NULL, `received_bytes` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `download_entries_started_idx` ON `download_entries` (`started_at`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`title` TEXT, `address` TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `bookmarks_address_idx` ON `bookmarks` (`address`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `visits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`visited_at` INTEGER NOT NULL, `title` TEXT NOT NULL, `address` TEXT NOT NULL, `icon` TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `visits_time_idx` ON `visits` (`visited_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `visits_title_idx` ON `visits` (`title`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `visits_address_idx` ON `visits` (`address`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `open_tabs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`address` TEXT NOT NULL, `title` TEXT NOT NULL, `is_shown` INTEGER NOT NULL, `preview_key` TEXT, " +
            "`icon_key` TEXT, `is_private` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, `state_file` TEXT, " +
            "`blocks_ads` INTEGER, `zoom` REAL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `site_settings` (`host` TEXT NOT NULL, " +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `popup_policy` INTEGER, `icon` TEXT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `site_settings_host_idx` ON `site_settings` (`host`)")
    }
}
