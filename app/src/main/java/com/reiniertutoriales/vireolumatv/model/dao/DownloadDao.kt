package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.reiniertutoriales.vireolumatv.model.Download

/** Stored downloads. [insert] and [update] block: they run on the download worker threads. */
@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads")
    suspend fun getAll(): List<Download>

    /** The next page of 100, newest first. */
    @Query("SELECT * FROM downloads ORDER BY time DESC LIMIT 100 OFFSET :offset")
    suspend fun allByLimitOffset(offset: Long): List<Download>

    @Insert
    fun insert(download: Download): Long

    @Update
    fun update(vararg downloads: Download)

    @Delete
    suspend fun delete(download: Download)
}
