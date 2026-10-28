package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.reiniertutoriales.vireolumatv.model.WebTabState

/** Open tabs; normal and private tabs are stored apart by their incognito flag. */
@Dao
interface TabsDao {
    @Query("SELECT * FROM tabs WHERE incognito=:incognito ORDER BY position ASC")
    suspend fun getAll(incognito: Boolean = false): List<WebTabState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WebTabState): Long

    @Update
    suspend fun update(item: WebTabState)

    @Delete
    suspend fun delete(item: WebTabState)

    @Query("DELETE FROM tabs WHERE incognito = :incognito")
    suspend fun deleteAll(incognito: Boolean = false)

    @Query("UPDATE tabs SET selected = 0 WHERE incognito = :incognito")
    suspend fun unselectAll(incognito: Boolean = false)

    @Query("UPDATE tabs SET position = :position WHERE id = :id")
    suspend fun updatePosition(position: Int, id: Long)

    /**
     * Stores [item] and returns its id. A copy is written, taken before the first query, so a
     * change to the live tab meanwhile cannot mix two states; a selected tab unselects the others.
     */
    @Transaction
    suspend fun save(item: WebTabState): Long {
        val snapshot = item.copy()
        if (snapshot.selected) unselectAll(snapshot.incognito)
        if (snapshot.id == 0L) return insert(snapshot)
        update(snapshot)
        return snapshot.id
    }

    @Transaction
    suspend fun updatePositions(tabs: List<WebTabState>) = tabs.forEach { updatePosition(it.position, it.id) }
}
