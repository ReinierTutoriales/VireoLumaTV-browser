package com.reiniertutoriales.vireolumatv.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.InputStream

/**
 * One download. The stored columns are the "downloads" table of schema 19; the other fields only
 * live while the download runs or while a list shows it. [size] is the byte total once known, or
 * [SIZE_FAILED] / [SIZE_CANCELLED] after a failure or cancellation.
 */
@Entity(
    tableName = "downloads",
    indices = [Index(value = ["time"], name = "downloads_time_idx"), Index(value = ["filename"], name = "downloads_filename_idx")],
)
class Download() {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
    var time: Long = 0
    var filename: String = ""
    /** A file path before Android 11, a MediaStore URI from Android 11. */
    var filepath: String = ""
    var url: String = ""
    @Volatile var size: Long = 0
    @Volatile @ColumnInfo(name = "bytes_received") var bytesReceived: Long = 0

    @Ignore var afterDownload = AfterDownload.NOP
    @Ignore @Volatile var cancelled = false
    /** Private downloads are never stored, so they all keep id 0. */
    @Ignore var incognito = false
    /** A day heading row in the download list, not a download. */
    @Ignore var isDayHeader = false
    @Ignore var mimeType: String? = null
    @Ignore var referer: String? = null
    @Ignore var userAgent: String? = null
    /** A page blob handed over as a base64 data URL; released once written. */
    @Ignore var base64BlobData: String? = null
    /** Content the page already opened; closed once written. */
    @Ignore var stream: InputStream? = null

    constructor(
        url: String, filename: String, filepath: String?, afterDownload: AfterDownload,
        mimeType: String?, referer: String?, userAgent: String?, base64BlobData: String?,
        stream: InputStream?, size: Long = 0L,
    ) : this() {
        this.url = url
        this.filename = filename
        this.filepath = filepath.orEmpty()
        this.afterDownload = afterDownload
        this.mimeType = mimeType
        this.referer = referer
        this.userAgent = userAgent
        this.base64BlobData = base64BlobData
        this.stream = stream
        this.size = size
    }

    /** The same download: the same object, or the same stored row (a history copy of a running one). */
    fun matches(other: Download?): Boolean = other != null && (this === other || (id != 0L && id == other.id))

    enum class AfterDownload { NOP, INSTALL }

    companion object {
        const val SIZE_FAILED = -2L
        const val SIZE_CANCELLED = -3L

        fun dayHeader(time: Long) = Download().apply {
            this.time = time
            isDayHeader = true
        }
    }
}
