package com.reiniertutoriales.vireolumatv.model.util

import androidx.room.TypeConverter
import java.util.Date

/** Room stores a [Date] as its epoch milliseconds. */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let(::Date)

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
