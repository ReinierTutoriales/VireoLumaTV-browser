package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.reiniertutoriales.vireolumatv.model.Download

/** Stored downloads. [insert] and [update] block: they run on the download worker threads. */
@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM downloads")
    abstract suspend fun getAll(): List<Download>

    /** Continue strictly after a row, even if earlier rows were inserted or removed. */
    @Query("SELECT * FROM downloads WHERE :beforeTime IS NULL OR time < :beforeTime OR (time = :beforeTime AND id < :beforeId) ORDER BY time DESC, id DESC LIMIT 100")
    abstract suspend fun page(beforeTime: Long? = null, beforeId: Long = 0): List<Download>

    @Insert
    abstract fun insert(download: Download): Long

    @Query("UPDATE downloads SET filename = :name, filepath = :path, size = :size, bytes_received = :received WHERE id = :id")
    protected abstract fun writeTransfer(id: Long, name: String, path: String, size: Long, received: Long)

    /** Worker progress updates transfer fields; the original address and creation time stay fixed. */
    fun update(vararg downloads: Download) {
        downloads.forEach { writeTransfer(it.id, it.filename, it.filepath, it.size, it.bytesReceived) }
    }

    @Delete
    abstract suspend fun delete(download: Download)
}
