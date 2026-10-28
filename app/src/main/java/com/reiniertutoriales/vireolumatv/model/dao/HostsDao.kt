package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.reiniertutoriales.vireolumatv.model.HostConfig

@Dao
interface HostsDao {
    /** Blocking: page loads ask for it from a background thread. */
    @Query("SELECT * FROM hosts WHERE host_name = :name")
    fun findByHostName(name: String): HostConfig?

    /** A second row for the same host replaces the first. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HostConfig): Long

    @Update
    suspend fun update(item: HostConfig)
}
