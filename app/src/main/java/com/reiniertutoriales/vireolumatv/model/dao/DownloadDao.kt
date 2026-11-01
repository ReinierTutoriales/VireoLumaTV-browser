package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.reiniertutoriales.vireolumatv.model.Download

/** Stored downloads. [insert] and [update] block: they run on the download worker threads. */
@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM download_entries")
    abstract suspend fun getAll(): List<Download>

    /** Continue strictly after a row, even if earlier rows were inserted or removed. */
    @Query("SELECT * FROM download_entries WHERE :beforeTime IS NULL OR started_at < :beforeTime OR (started_at = :beforeTime AND id < :beforeId) ORDER BY started_at DESC, id DESC LIMIT 100")
    abstract suspend fun page(beforeTime: Long? = null, beforeId: Long = 0): List<Download>

    @Insert
    abstract fun insert(download: Download): Long

    @Query("UPDATE download_entries SET file_name = :name, location = :path, total_bytes = :size, received_bytes = :received WHERE id = :id")
    protected abstract fun writeTransfer(id: Long, name: String, path: String, size: Long, received: Long)

    /** Worker progress updates transfer fields; the original address and creation time stay fixed. */
    @Transaction
    open fun update(vararg downloads: Download) {
        downloads.forEach { writeTransfer(it.id, it.filename, it.filepath, it.size, it.bytesReceived) }
    }

    @Delete
    abstract suspend fun delete(download: Download)
}
