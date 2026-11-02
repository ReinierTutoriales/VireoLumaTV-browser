package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.reiniertutoriales.vireolumatv.model.Favorite

@Dao
abstract class FavoriteDao {
    /** Every bookmark, newest first. */
    @Query("SELECT * FROM bookmarks ORDER BY id DESC")
    abstract suspend fun getAll(): List<Favorite>

    @Insert
    abstract suspend fun insert(item: Favorite): Long

    @Query("UPDATE bookmarks SET title = :title, address = :url WHERE id = :id")
    protected abstract suspend fun writeAddress(id: Long, title: String?, url: String?)

    /** Writes the name and address of an edited bookmark. */
    suspend fun update(item: Favorite) = writeAddress(item.id, item.title, item.url)

    @Delete
    abstract suspend fun delete(item: Favorite)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE address = :url")
    protected abstract suspend fun countWithAddress(url: String): Int

    /** Adds [item] unless a bookmark already has its address; true when it was added. */
    @Transaction
    open suspend fun insertIfAbsent(item: Favorite): Boolean {
        val url = item.url ?: return false
        if (countWithAddress(url) > 0) return false
        insert(item)
        return true
    }
}
