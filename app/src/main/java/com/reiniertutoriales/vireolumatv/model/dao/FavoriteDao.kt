package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.reiniertutoriales.vireolumatv.model.Favorite

@Dao
abstract class FavoriteDao {
    /** Top-level bookmarks, newest first; rows older versions kept for their home page are left out. */
    @Query("SELECT * FROM favorites WHERE parent=0 AND home_page_bookmark=0 ORDER BY id DESC")
    abstract suspend fun getAll(): List<Favorite>

    @Insert
    abstract suspend fun insert(item: Favorite): Long

    @Query("UPDATE favorites SET TITLE = :title, URL = :url WHERE ID = :id")
    protected abstract suspend fun writeAddress(id: Long, title: String?, url: String?)

    /** Editing a visible bookmark cannot overwrite legacy columns through an old row snapshot. */
    suspend fun update(item: Favorite) = writeAddress(item.id, item.title, item.url)

    @Delete
    abstract suspend fun delete(item: Favorite)
}
