package com.reiniertutoriales.vireolumatv.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One bookmark, the "favorites" table of schema 19. The upper-case column names are what that
 * schema stores. The home-page columns ([homePageBookmark], [order], [destUrl], [description],
 * [validUntil], [useful]) keep the values older versions wrote; the browser no longer sets them.
 */
@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["PARENT"], name = "favorites_parent_idx"),
        Index(value = ["HOME_PAGE_BOOKMARK"], name = "favorites_home_page_bookmark_idx"),
    ],
)
class Favorite {
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "ID") var id: Long = 0
    @ColumnInfo(name = "TITLE") var title: String? = null
    @ColumnInfo(name = "URL") var url: String? = null
    @ColumnInfo(name = "PARENT") var parent: Long? = 0
    var favicon: String? = null
    @ColumnInfo(name = "HOME_PAGE_BOOKMARK") var homePageBookmark = false
    @ColumnInfo(name = "I_ORDER") var order = 0
    @ColumnInfo(name = "DEST_URL") var destUrl: String? = null
    @ColumnInfo(name = "DESCRIPTION") var description: String? = null
    @ColumnInfo(name = "VALID_UNTIL") var validUntil: Long? = null
    @ColumnInfo(name = "USEFUL") var useful = false

    /** A detached edit keeps the displayed row and legacy columns intact until the write succeeds. */
    fun copyForEdit() = Favorite().also { copy ->
        copy.id = id
        copy.title = title
        copy.url = url
        copy.parent = parent
        copy.favicon = favicon
        copy.homePageBookmark = homePageBookmark
        copy.order = order
        copy.destUrl = destUrl
        copy.description = description
        copy.validUntil = validUntil
        copy.useful = useful
    }

    /** A folder has no address. */
    val isFolder: Boolean get() = url == null
}
