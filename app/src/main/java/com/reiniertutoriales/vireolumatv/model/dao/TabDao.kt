package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.reiniertutoriales.vireolumatv.model.BrowserTab

/** Open tabs; normal and private tabs are stored apart by their incognito flag. */
@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE incognito=:incognito ORDER BY position ASC")
    suspend fun getAll(incognito: Boolean = false): List<BrowserTab>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BrowserTab): Long

    @Update
    suspend fun update(item: BrowserTab)

    @Delete
    suspend fun delete(item: BrowserTab)

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
    suspend fun save(item: BrowserTab): Long {
        val snapshot = item.copy()
        if (snapshot.selected) unselectAll(snapshot.incognito)
        if (snapshot.id == 0L) return insert(snapshot)
        update(snapshot)
        return snapshot.id
    }

    @Transaction
    suspend fun updatePositions(tabs: List<BrowserTab>) {
        val positions = tabs.map { it.id to it.position }
        for ((id, position) in positions) updatePosition(position, id)
    }
}
