package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.*
import com.reiniertutoriales.vireolumatv.model.WebTabState

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

    @Transaction
    suspend fun save(item: WebTabState): Long {
        // Freeze selection and mode before the first suspending query.
        val snapshot = item.copy()
        if (snapshot.selected) {
            unselectAll(snapshot.incognito)
        }
        return if (snapshot.id != 0L) {
            update(snapshot)
            snapshot.id
        } else {
            insert(snapshot)
        }
    }

    @Transaction
    suspend fun updatePositions(tabs: List<WebTabState>) {
        tabs.forEach { updatePosition(it.position, it.id) }
    }
}