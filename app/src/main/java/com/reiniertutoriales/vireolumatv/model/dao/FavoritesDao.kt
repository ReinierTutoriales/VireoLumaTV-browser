package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.reiniertutoriales.vireolumatv.model.FavoriteItem

@Dao
interface FavoritesDao {
    /** Top-level bookmarks, newest first; rows older versions kept for their home page are left out. */
    @Query("SELECT * FROM favorites WHERE parent=0 AND home_page_bookmark=0 ORDER BY id DESC")
    suspend fun getAll(): List<FavoriteItem>

    @Insert
    suspend fun insert(item: FavoriteItem): Long

    @Update
    suspend fun update(item: FavoriteItem)

    @Delete
    suspend fun delete(item: FavoriteItem)
}
