package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.reiniertutoriales.vireolumatv.model.HistoryItem

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits")
    suspend fun getAll(): List<HistoryItem>

    /** Trimming needs a threshold decision, not a count of the entire table. */
    @Query("SELECT EXISTS(SELECT 1 FROM visits LIMIT 1 OFFSET :threshold)")
    suspend fun exceeds(threshold: Int): Boolean

    /** The [limit] most recent visits. */
    @Query("SELECT * FROM visits ORDER BY visited_at DESC, id DESC LIMIT :limit")
    suspend fun last(limit: Int = 1): List<HistoryItem>

    /** Continue strictly after a row, even if earlier rows were inserted or removed. */
    @Query("SELECT * FROM visits WHERE :beforeTime IS NULL OR visited_at < :beforeTime OR (visited_at = :beforeTime AND id < :beforeId) ORDER BY visited_at DESC, id DESC LIMIT 100")
    suspend fun page(beforeTime: Long? = null, beforeId: Long = 0): List<HistoryItem>

    /**
     * Up to 100 visits whose title or address matches the LIKE [pattern], newest first. Backslash
     * escapes, so a pattern from HistoryPages.likePattern matches "%" and "_" literally.
     */
    @Query("SELECT * FROM visits WHERE (title LIKE :pattern ESCAPE '\\') OR (address LIKE :pattern ESCAPE '\\') ORDER BY visited_at DESC, id DESC LIMIT 100")
    suspend fun search(pattern: String): List<HistoryItem>

    @Insert
    suspend fun insert(item: HistoryItem): Long

    @Query("UPDATE visits SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Delete
    suspend fun delete(vararg item: HistoryItem)

    @Query("DELETE FROM visits WHERE visited_at < :time")
    suspend fun deleteWhereTimeLessThan(time: Long)
}
