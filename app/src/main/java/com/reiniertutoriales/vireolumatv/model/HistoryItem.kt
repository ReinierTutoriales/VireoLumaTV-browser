package com.reiniertutoriales.vireolumatv.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/** One visited page, the "history" table of schema 19. */
@Entity(
    tableName = "history",
    indices = [
        Index(value = ["time"], name = "history_time_idx"),
        Index(value = ["title"], name = "history_title_idx"),
        Index(value = ["url"], name = "history_url_idx"),
    ],
)
class HistoryItem {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
    @ColumnInfo(name = "time") var time: Long = 0
    @ColumnInfo(name = "title") var title: String = ""
    @ColumnInfo(name = "url") var url: String = ""
    @ColumnInfo(name = "favicon") var favicon: String? = null
    /** A column of schema 19 that is no longer written; private visits are never stored. */
    @ColumnInfo(name = "incognito") var incognito: Boolean? = null

    /** A day heading row in the history list, not a visit. */
    @Ignore var isDayHeader = false
    /** Ticked in the history list's selection mode. */
    @Ignore var selected = false
    /** Already written to the database. */
    @Ignore var saved = false

    companion object {
        fun dayHeader(time: Long) = HistoryItem().apply {
            this.time = time
            isDayHeader = true
        }
    }
}
