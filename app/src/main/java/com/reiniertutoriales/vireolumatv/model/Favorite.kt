package com.reiniertutoriales.vireolumatv.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** One bookmark, the "bookmarks" table: a name and a web address the user saved. */
@Entity(tableName = "bookmarks", indices = [Index(value = ["address"], name = "bookmarks_address_idx")])
class Favorite {
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long = 0
    @ColumnInfo(name = "title") var title: String? = null
    @ColumnInfo(name = "address") var url: String? = null

    /** A detached copy to edit; the shown row stays as it is until the write succeeds. */
    fun copyForEdit() = Favorite().also { copy ->
        copy.id = id
        copy.title = title
        copy.url = url
    }
}
