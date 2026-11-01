package com.reiniertutoriales.vireolumatv.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/** One visited page, the "visits" table; private visits are never stored. */
@Entity(
    tableName = "visits",
    indices = [
        Index(value = ["visited_at"], name = "visits_time_idx"),
        Index(value = ["title"], name = "visits_title_idx"),
        Index(value = ["address"], name = "visits_address_idx"),
    ],
)
class HistoryItem {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
    @ColumnInfo(name = "visited_at") var time: Long = 0
    @ColumnInfo(name = "title") var title: String = ""
    @ColumnInfo(name = "address") var url: String = ""
    @ColumnInfo(name = "icon") var favicon: String? = null

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
