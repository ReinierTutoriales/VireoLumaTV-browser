package com.reiniertutoriales.vireolumatv.model.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.reiniertutoriales.vireolumatv.model.Download

/** Stored downloads. Database operations suspend on Room's query dispatcher. */
@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM download_entries")
    abstract suspend fun getAll(): List<Download>

    /** Continue strictly after a row, even if earlier rows were inserted or removed. */
    @Query("SELECT * FROM download_entries WHERE :beforeTime IS NULL OR started_at < :beforeTime OR (started_at = :beforeTime AND id < :beforeId) ORDER BY started_at DESC, id DESC LIMIT 100")
    abstract suspend fun page(beforeTime: Long? = null, beforeId: Long = 0): List<Download>

    @Insert
    abstract suspend fun insert(download: Download): Long

    @Query("UPDATE download_entries SET file_name = :name, location = :path, total_bytes = :size, received_bytes = :received WHERE id = :id")
    protected abstract suspend fun writeTransfer(id: Long, name: String, path: String, size: Long, received: Long)

    /** Worker progress updates transfer fields; the original address and creation time stay fixed. */
    @Transaction
    open suspend fun update(vararg downloads: Download) {
        downloads.forEach { writeTransfer(it.id, it.filename, it.filepath, it.size, it.bytesReceived) }
    }

    @Delete
    abstract suspend fun delete(download: Download)
}
