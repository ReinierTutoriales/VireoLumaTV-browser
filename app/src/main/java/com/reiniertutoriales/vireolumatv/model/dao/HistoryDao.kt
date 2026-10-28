package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.reiniertutoriales.vireolumatv.model.HistoryItem

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history")
    suspend fun getAll(): List<HistoryItem>

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    /** The [limit] most recent visits. */
    @Query("SELECT * FROM history ORDER BY time DESC LIMIT :limit")
    suspend fun last(limit: Int = 1): List<HistoryItem>

    /** The next page of 100, newest first. */
    @Query("SELECT * FROM history ORDER BY time DESC LIMIT 100 OFFSET :offset")
    suspend fun allByLimitOffset(offset: Long): List<HistoryItem>

    /**
     * Up to 100 visits whose title or address matches the LIKE [pattern], newest first. Backslash
     * escapes, so a pattern from HistoryModel.likePattern matches "%" and "_" literally.
     */
    @Query("SELECT * FROM history WHERE (title LIKE :pattern ESCAPE '\\') OR (url LIKE :pattern ESCAPE '\\') ORDER BY time DESC LIMIT 100")
    suspend fun search(pattern: String): List<HistoryItem>

    @Insert
    suspend fun insert(item: HistoryItem): Long

    @Query("UPDATE history SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Delete
    suspend fun delete(vararg item: HistoryItem)

    @Query("DELETE FROM history WHERE time < :time")
    suspend fun deleteWhereTimeLessThan(time: Long)
}
