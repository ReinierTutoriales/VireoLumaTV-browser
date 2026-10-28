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

    /** Continue strictly after a row, even if earlier rows were inserted or removed. */
    @Query("SELECT * FROM downloads WHERE :beforeTime IS NULL OR time < :beforeTime OR (time = :beforeTime AND id < :beforeId) ORDER BY time DESC, id DESC LIMIT 100")
    suspend fun page(beforeTime: Long? = null, beforeId: Long = 0): List<Download>

    @Insert
    fun insert(download: Download): Long

    @Update
    fun update(vararg downloads: Download)

    @Delete
    suspend fun delete(download: Download)
}
