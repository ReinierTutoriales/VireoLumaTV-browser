package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT COUNT(*) FROM favorites WHERE URL = :url AND parent=0 AND home_page_bookmark=0")
    protected abstract suspend fun countVisible(url: String): Int

    /** Adds [item] unless a visible bookmark already has its address; true when it was added. */
    @Transaction
    open suspend fun insertIfAbsent(item: Favorite): Boolean {
        val url = item.url ?: return false
        if (countVisible(url) > 0) return false
        insert(item)
        return true
    }
}
