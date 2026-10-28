package com.reiniertutoriales.vireolumatv.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.AtomicFile
import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.utils.BundleBytes
import com.reiniertutoriales.vireolumatv.utils.md5Name
import com.reiniertutoriales.vireolumatv.webengine.Engines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * One open tab, the "tabs" table of schema 19. The stored part says what the tab shows; the
 * engine and the fields marked @Ignore exist only while the browser runs. Navigation history
 * lives in a state file under [TAB_WVSTATES_DIR] named by [wvStateFileName], and the preview in
 * [TAB_THUMBNAILS_DIR].
 *
 * A tab is equal only to itself: two tabs on the same address are still two tabs, so list
 * lookups and removals never mistake one for another. [copy] makes a detached snapshot for saving.
 */
@Entity(tableName = "tabs")
data class WebTabState(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var url: String = "",
    var title: String = "",
    var selected: Boolean = false,
    var thumbnailHash: String? = null,
    var faviconHash: String? = null,
    var incognito: Boolean = false,
    var position: Int = 0,
    /** A column of schema 19 that is no longer read; state lives in a file. */
    @ColumnInfo(name = "wv_state", typeAffinity = ColumnInfo.BLOB) var wvState: ByteArray? = null,
    @ColumnInfo(name = "wv_state_file") var wvStateFileName: String? = null,
    /** This tab's ad blocking, or null to follow the setting. */
    var adblock: Boolean? = null,
    var scale: Float? = null,
) {
    @Ignore var thumbnail: Bitmap? = null
    /** Engine state taken when the tab was last paused, kept until it is written to its file. */
    @Ignore var savedState: Any? = null
    @delegate:Ignore val engine by lazy { Engines.create(this) }
    @Ignore @Volatile var closed = false
    /** The renderer died: its saved history may be what crashed it, so the address is loaded afresh. */
    @Ignore var rendererLost = false
    /** Bumped for each save, so a slower earlier save cannot overwrite a later one. */
    @Ignore val persistenceRevision = AtomicLong(0)
    /** The last address WebView asked to load in this tab. */
    @Ignore var lastLoadingUrl: String? = null
    @Ignore var blockedAds = 0
    @Ignore var blockedPopups = 0
    @Ignore var cachedHostConfig: HostConfig? = null
    /** Set while a page-opened window is screened against popup ad rules. */
    @Ignore @Volatile var popupGuard: PopupGuard? = null
    /** The tab that opened this popup, kept alive while it shows so closing it resumes the page. */
    @Ignore var opener: WebTabState? = null
    /** A media element of the current page (any frame) started playing. */
    @Ignore @Volatile var mediaStarted = false
    /** Makes this run's preview names unique, so a new preview never reuses an open file. */
    @Ignore private val previewToken = UUID.randomUUID().toString()

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    /** Restores the engine from the kept or stored state; false means the address must be loaded. */
    fun restoreWebView(): Boolean {
        if (rendererLost) return false
        return try {
            val state = savedState ?: stateFile()?.takeIf { it.exists() }?.readBytes()?.let(engine::readHistory)
                ?: return false
            val restored = engine.restoreHistory(state)
            // Keep the snapshot only once the engine accepted it; the file stays until the next save replaces it.
            savedState = if (restored) state else null
            restored
        } catch (error: Exception) {
            savedState = null
            Log.w(TAG, "Could not restore tab state; loading the saved address", error)
            false
        }
    }

    /**
     * Writes [savedState] to this tab's own state file, atomically. A name from an older version,
     * which duplicate tabs may share, is replaced by a new one instead of being overwritten.
     */
    fun saveWebViewStateToFile() {
        val state = savedState ?: return
        val prefix = if (incognito) "private-tab-" else "tab-"
        val name = wvStateFileName?.takeIf { it.startsWith(prefix) } ?: prefix + UUID.randomUUID()
        val bytes = if (state is Bundle) BundleBytes.encode(state) else state.toString().toByteArray(Charsets.UTF_8)
        try {
            val directory = File(AppContext.get().filesDir, TAB_WVSTATES_DIR)
            if (!directory.isDirectory && !directory.mkdir()) return
            val file = AtomicFile(File(directory, name))
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            wvStateFileName = name
        } catch (error: Exception) {
            Log.w(TAG, "Could not save tab state", error)
        }
    }

    /** Deletes this tab's preview and state files. */
    fun removeFiles() {
        removeThumbnailFile()
        stateFile()?.delete()
        wvStateFileName = null
    }

    fun trimMemory() {
        // [savedState] stays until saveTab() has written it: a save may still be on its way.
        engine.releaseHiddenView()
    }

    fun onPause() {
        savedState = engine.saveHistory()
    }

    /** Stores [thumbnail] as this tab's preview, replacing the previous preview file. */
    suspend fun updateThumbnail(context: Context, thumbnail: Bitmap) {
        this.thumbnail = thumbnail
        val prefix = if (incognito) "private-preview-" else "preview-"
        val name = prefix + previewToken + "-" + md5Name(url.toByteArray())
        withContext(Dispatchers.IO) {
            synchronized(this@WebTabState) {
                val directory = File(context.cacheDir, TAB_THUMBNAILS_DIR)
                if (!directory.isDirectory && !directory.mkdir()) return@withContext
                // Older versions named previews by address alone, possibly shared by duplicate tabs: keep those.
                if (thumbnailHash != name && thumbnailHash?.let { it.startsWith("preview-") || it.startsWith("private-preview-") } == true) {
                    removeThumbnailFile()
                }
                try {
                    // JPEG encodes an RGB_565 preview several times faster than PNG on TV CPUs; BitmapFactory
                    // reads either, so the ".png" name of older versions is kept.
                    File(directory, "$name.png").outputStream().use { thumbnail.compress(Bitmap.CompressFormat.JPEG, PREVIEW_QUALITY, it) }
                    thumbnailHash = name
                } catch (error: Exception) {
                    Log.w(TAG, "Could not save the tab preview", error)
                }
            }
        }
    }

    /** Decodes the stored preview at most [PREVIEW_MAX_SIDE] px wide or high, without keeping it. Call on IO. */
    fun loadThumbnail(): Bitmap? {
        val file = thumbnailFile() ?: return null
        if (!file.exists() || file.length() > PREVIEW_MAX_BYTES) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while ((options.outWidth - 1) / sample + 1 > PREVIEW_MAX_SIDE || (options.outHeight - 1) / sample + 1 > PREVIEW_MAX_SIDE) {
            sample *= 2
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun stateFile(): File? = wvStateFileName?.let { File(File(AppContext.get().filesDir, TAB_WVSTATES_DIR), it) }

    private fun thumbnailFile(): File? = thumbnailHash?.let { File(File(AppContext.get().cacheDir, TAB_THUMBNAILS_DIR), "$it.png") }

    private fun removeThumbnailFile() {
        thumbnailFile()?.delete()
        thumbnailHash = null
    }

    companion object {
        private const val TAG = "WebTabState"
        const val TAB_THUMBNAILS_DIR = "tabthumbs"
        const val TAB_WVSTATES_DIR = "wvstates"
        private const val PREVIEW_QUALITY = 85
        private const val PREVIEW_MAX_SIDE = 480
        private const val PREVIEW_MAX_BYTES = 2L * 1024 * 1024
    }
}
