package com.reiniertutoriales.vireolumatv.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.utils.Utils
import com.reiniertutoriales.vireolumatv.webengine.WebEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset


/**
 * Created by PDT on 24.08.2016.
 *
 * Class to store state of tab with webView
 */
@Entity(tableName = "tabs")
data class WebTabState(@PrimaryKey(autoGenerate = true)
                       var id: Long = 0,
                       var url: String = "",
                       var title: String = "",
                       var selected: Boolean = false,
                       var thumbnailHash: String? = null,
                       var faviconHash: String? = null,
                       var incognito: Boolean = false,
                       var position: Int = 0,
                       @Deprecated("This field is not used anymore")
                       @ColumnInfo(name = "wv_state", typeAffinity = ColumnInfo.BLOB)
                       var wvState: ByteArray? = null,
                       @ColumnInfo(name = "wv_state_file")
                       var wvStateFileName: String? = null,
                       var adblock: Boolean? = null,
                       var scale: Float? = null) {
    companion object {
        val TAG: String = WebTabState::class.java.simpleName

        const val TAB_THUMBNAILS_DIR = "tabthumbs"
        const val TAB_WVSTATES_DIR = "wvstates"
        const val GECKO_SESSION_STATE_HASH_PREFIX = "gecko:"
    }

    @Ignore
    var thumbnail: Bitmap? = null
    @Ignore
    private val thumbnailToken = java.util.UUID.randomUUID().toString()
    @Ignore
    var savedState: Any? = null
    @delegate:Ignore
    val webEngine by lazy { WebEngineFactory.createWebEngine(this) }
    @Ignore
    @Volatile
    var closed: Boolean = false
    @Ignore
    var rendererLost: Boolean = false
    @Ignore
    val persistenceRevision = java.util.concurrent.atomic.AtomicLong(0)
    @Ignore
    var lastLoadingUrl: String? = null //this is last url appeared in WebViewClient.shouldOverrideUrlLoading callback
    @Ignore
    var blockedAds = 0
    @Ignore
    var blockedPopups = 0
    @Ignore
    var cachedHostConfig: HostConfig? = null

    constructor(context: Context, json: JSONObject) : this() {
        try {
            url = json.getString("url")
            title = json.getString("title")
            selected = json.getBoolean("selected")
            if (json.has("thumbnail")) {
                thumbnailHash = json.getString("thumbnail")
            }
            if (json.has("wv_state")) {
                val state = Utils.convertJsonToBundle(json.getJSONObject("wv_state"))
                if (state != null && !state.isEmpty) {
                    savedState = state
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

    private suspend fun saveThumbnail(context: Context) {
        val thumbnail = this.thumbnail
        val thumbnailHash = this.thumbnailHash
        val url = url
        if (thumbnail == null) return
        withContext(Dispatchers.IO) {
            synchronized(this@WebTabState) {
                val tabsThumbsDir = File(context.cacheDir.absolutePath + File.separator + TAB_THUMBNAILS_DIR)
                if (tabsThumbsDir.exists() || tabsThumbsDir.mkdir()) {
                    try {
                        val hash = Utils.MD5_Hash(url.toByteArray(Charset.defaultCharset()))
                            ?.let { (if (incognito) "private-preview-" else "preview-") + thumbnailToken + "-" + it }
                        if (hash != null) {
                            if (thumbnailHash != null && thumbnailHash != hash &&
                                (thumbnailHash.startsWith("preview-") || thumbnailHash.startsWith("private-preview-"))) {
                                removeThumbnailFile()
                            }
                            val file = File(getThumbnailPath(hash))
                            var fos: FileOutputStream? = null
                            try {
                                fos = FileOutputStream(file)
                                thumbnail.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                fos?.close()
                            }

                            this@WebTabState.thumbnailHash = hash
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun getThumbnailPath(hash: String) =
        AppContext.get().cacheDir.absolutePath + File.separator + TAB_THUMBNAILS_DIR + File.separator + hash + ".png"

    private fun getWVStatePath(hash: String): String {
        return if (hash.startsWith(GECKO_SESSION_STATE_HASH_PREFIX)) {
            AppContext.get().filesDir.absolutePath + File.separator + TAB_WVSTATES_DIR + File.separator + hash.substring(
                GECKO_SESSION_STATE_HASH_PREFIX.length)
        } else {
            AppContext.get().filesDir.absolutePath + File.separator + TAB_WVSTATES_DIR + File.separator + hash
        }
    }

    fun removeFiles() {
        if (thumbnailHash != null) {
            removeThumbnailFile()
        }
        wvStateFileName?.apply {
            File(getWVStatePath(this)).delete()
            wvStateFileName = null
        }
    }

    private fun removeThumbnailFile() {
        if (thumbnailHash == null) return
        val thumbnailFile = File(getThumbnailPath(thumbnailHash!!))
        thumbnailFile.delete()
        thumbnailHash = null
    }

    fun restoreWebView(): Boolean {
        if (rendererLost) return false
        return try {
            var state = savedState
            if (state == null) {
                val stateFileName = wvStateFileName ?: return false
                if (stateFileName.startsWith(GECKO_SESSION_STATE_HASH_PREFIX)) return false
                val stateBytes = File(getWVStatePath(stateFileName)).readBytes()
                state = webEngine.stateFromBytes(stateBytes) ?: return false
            }
            val restored = webEngine.restoreState(state)
            // Keep a usable snapshot only after the engine accepts it. Persisted files are
            // preserved until the normal atomic save replaces them; failure loads the saved URL.
            savedState = if (restored) state else null
            restored
        } catch (e: Exception) {
            savedState = null
            Log.w(TAG, "Could not restore tab state; loading saved URL", e)
            false
        }
    }

    fun saveWebViewStateToFile() {
        val state = savedState
        var stateFileName = wvStateFileName
        if (stateFileName != null && stateFileName.startsWith(GECKO_SESSION_STATE_HASH_PREFIX)) {
            File(getWVStatePath(stateFileName)).delete()
            stateFileName = null
        }
        if (state == null) return
        // Private tabs must not overwrite a legacy file shared with a normal tab.
        val prefix = if (incognito) "private-tab-" else "tab-"
        // Old content hashes may be shared by duplicate tabs; never overwrite their shared file.
        if (stateFileName?.startsWith(prefix) != true) stateFileName = null
        val stateBytes = when (state) {
            is Bundle -> {
                Utils.bundleToBytes(state) ?: return
            }
            else -> {
                state.toString().toByteArray(Charsets.UTF_8)
            }
        }
        if (stateFileName == null) {
            stateFileName = prefix + java.util.UUID.randomUUID().toString()
        }
        try {
            val statesDir = File(AppContext.get().filesDir.absolutePath + File.separator + TAB_WVSTATES_DIR)
            if (statesDir.exists() || statesDir.mkdir()) {
                val file = android.util.AtomicFile(File(getWVStatePath(stateFileName)))
                var output: FileOutputStream? = null
                try {
                    output = file.startWrite()
                    output.write(stateBytes)
                    file.finishWrite(output)
                    wvStateFileName = stateFileName
                } catch (error: Exception) {
                    file.failWrite(output)
                    throw error
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun trimMemory() {
        // Keep the last captured state until saveTab() has persisted it. Clearing it here can race
        // with the asynchronous save started when switching tabs after the engine is discarded.
        webEngine.trimMemory()
    }

    fun onPause() {
        webEngine.let {
            savedState = it.saveState()
        }
    }

    suspend fun updateThumbnail(context: Context, thumbnail: Bitmap) {
        this.thumbnail = thumbnail
        val url = url
        var hash = Utils.MD5_Hash(url.toByteArray(Charset.defaultCharset()))
        if (hash != null) {
            hash += hashCode()//to make thumbnails from different tabs unique even with same url
            if (hash != thumbnailHash) {
                saveThumbnail(context)
            }
        }
    }

    /** Decode a bounded preview without retaining it on a background tab. Call on IO. */
    fun loadThumbnail(): Bitmap? {
        val hash = thumbnailHash ?: return null
        val file = File(getThumbnailPath(hash))
        if (!file.exists() || file.length() > 2 * 1024 * 1024) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while ((options.outWidth - 1) / sample + 1 > 480 || (options.outHeight - 1) / sample + 1 > 480) {
            sample *= 2
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }
}
