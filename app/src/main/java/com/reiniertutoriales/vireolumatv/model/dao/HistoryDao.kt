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

    /** Trimming needs a threshold decision, not a count of the entire table. */
    @Query("SELECT EXISTS(SELECT 1 FROM history LIMIT 1 OFFSET :threshold)")
    suspend fun exceeds(threshold: Int): Boolean

    /** The [limit] most recent visits. */
    @Query("SELECT * FROM history ORDER BY time DESC, id DESC LIMIT :limit")
    suspend fun last(limit: Int = 1): List<HistoryItem>

    /** Continue strictly after a row, even if earlier rows were inserted or removed. */
    @Query("SELECT * FROM history WHERE :beforeTime IS NULL OR time < :beforeTime OR (time = :beforeTime AND id < :beforeId) ORDER BY time DESC, id DESC LIMIT 100")
    suspend fun page(beforeTime: Long? = null, beforeId: Long = 0): List<HistoryItem>

    /**
     * Up to 100 visits whose title or address matches the LIKE [pattern], newest first. Backslash
     * escapes, so a pattern from HistoryModel.likePattern matches "%" and "_" literally.
     */
    @Query("SELECT * FROM history WHERE (title LIKE :pattern ESCAPE '\\') OR (url LIKE :pattern ESCAPE '\\') ORDER BY time DESC, id DESC LIMIT 100")
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
