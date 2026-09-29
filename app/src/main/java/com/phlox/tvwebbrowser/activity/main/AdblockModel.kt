package com.phlox.tvwebbrowser.activity.main

import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.brave.adblock.AdBlockClient
import com.brave.adblock.AdBlockClient.FilterOption
import com.brave.adblock.Utils
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.*

class AdblockModel : ActiveModel() {
    companion object {
        val TAG: String = AdblockModel::class.java.simpleName

        const val SERIALIZED_LIST_FILE = "adblock_ser.dat"
        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24 * 30 //30 days
        private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 10_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 15_000
        private const val EASY_PRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
        private const val EASY_LIST_SPANISH_URL = "https://easylist-downloads.adblockplus.org/easylistspanish.txt"
    }

    private data class FilterList(
        val name: String,
        val url: String
    )

    private val clientLock = Any()
    private var client: AdBlockClient? = null
    val clientLoading = ObservableValue(false)
    val config = AppContext.provideConfig()

    init {
        loadAdBlockList(false)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun loadAdBlockList(forceReload: Boolean) = modelScope.launch {
        if (clientLoading.value) return@launch
        val checkDate = Calendar.getInstance()
        checkDate.timeInMillis = config.adBlockListLastUpdate
        checkDate.add(Calendar.MINUTE, AUTO_UPDATE_INTERVAL_MINUTES)
        val now = Calendar.getInstance()
        val needUpdate = forceReload || checkDate.before(now)
        clientLoading.value = true
        var loadedClient: AdBlockClient? = null
        var downloadAttempted = false
        var updated = false
        try {
            withContext(Dispatchers.IO) ioContext@ {
                val serializedFile = File(TVBro.instance.filesDir, SERIALIZED_LIST_FILE)
                val filterLists = getConfiguredFilterLists()
                if (!needUpdate) {
                    loadedClient = deserializeCachedList(serializedFile)
                    if (loadedClient != null) {
                        Log.i(TAG, "Loaded cached adblock list")
                        return@ioContext
                    }
                    Log.w(TAG, "Cached adblock list is missing or invalid")
                }
                downloadAttempted = true
                try {
                    val combinedFilterList = buildString {
                        filterLists.forEach { filterList ->
                            Log.i(TAG, "Downloading adblock list: ${filterList.name}")
                            appendLine("! ${filterList.name}")
                            appendLine(downloadFilterList(filterList))
                        }
                    }
                    val freshClient = AdBlockClient()
                    if (freshClient.parse(combinedFilterList)) {
                        //only a successfully parsed complete list set may replace the cached one
                        freshClient.serialize(serializedFile.absolutePath)
                        loadedClient = freshClient
                        updated = true
                        Log.i(TAG, "Downloaded and parsed adblock lists: ${filterLists.joinToString { it.name }}")
                        return@ioContext
                    }
                    Log.w(TAG, "Downloaded adblock lists could not be parsed: ${filterLists.joinToString { it.name }}")
                } catch (e: Exception) {
                    Log.w(TAG, "Can not download complete adblock list set", e)
                }
                //update failed: keep blocking with the last list that worked
                loadedClient = deserializeCachedList(serializedFile)
                if (loadedClient != null) {
                    Log.i(TAG, "Using cached adblock list after update failure")
                } else {
                    Log.w(TAG, "No usable adblock list available")
                }
            }
            //if nothing could be loaded keep the current client (if any) instead of replacing it with an empty one
            loadedClient?.let {
                synchronized(clientLock) {
                    this@AdblockModel.client = it
                }
            }
            //advance the update date only after a successful download, so a failed update is retried on next load
            if (updated) {
                config.adBlockListLastUpdate = now.timeInMillis
            }
            if (downloadAttempted && !updated) {
                Toast.makeText(TVBro.instance, "Error loading ad-blocker list", Toast.LENGTH_SHORT).show()
            }
        } finally {
            clientLoading.value = false
        }
    }

    private fun getConfiguredFilterLists(): List<FilterList> {
        val configuredUrl = config.adBlockListURL.value
        if (configuredUrl != Config.DEFAULT_ADBLOCK_LIST_URL) {
            return listOf(FilterList("Custom", configuredUrl))
        }
        return listOf(
            FilterList("EasyList", Config.DEFAULT_ADBLOCK_LIST_URL),
            FilterList("EasyPrivacy", EASY_PRIVACY_URL),
            FilterList("EasyList Spanish", EASY_LIST_SPANISH_URL)
        )
    }

    private fun downloadFilterList(filterList: FilterList): String {
        val connection = URL(filterList.url).openConnection().apply {
            connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MS
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun deserializeCachedList(serializedFile: File): AdBlockClient? {
        if (!serializedFile.exists()) return null
        val cachedClient = AdBlockClient()
        return if (cachedClient.deserialize(serializedFile.absolutePath)) cachedClient else null
    }

    fun isAd(url: Uri, type: String?, baseUri: Uri): Boolean {
        val baseHost = baseUri.host ?: return false
        val filterOption = try {
            mapRequestToFilterOption(url, type)
        } catch (e: Exception) {
            return false
        }
        val result = try {
            synchronized(clientLock) {
                client?.matches(url.toString(), filterOption, baseHost) ?: false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
        return result
    }

    private fun mapRequestToFilterOption(url: Uri?, type: String?): FilterOption? {
        if (type != null) {
            if (type == "image" || type.contains("image/")) {
                return FilterOption.IMAGE
            }
            if (type == "style" || type.contains("/css")) {
                return FilterOption.CSS
            }
            if (type == "script" || type.contains("javascript")) {
                return FilterOption.SCRIPT
            }
            if (type.contains("video/")) {
                return FilterOption.OBJECT
            }
        }
        if (url != null) {
            if (Utils.uriHasExtension(url, "css")) {
                return FilterOption.CSS
            }
            if (Utils.uriHasExtension(url, "js")) {
                return FilterOption.SCRIPT
            }
            if (Utils.uriHasExtension(
                    url,
                    "png",
                    "jpg",
                    "jpeg",
                    "webp",
                    "svg",
                    "gif",
                    "bmp",
                    "tiff"
                )
            ) {
                return FilterOption.IMAGE
            }
            if (Utils.uriHasExtension(url, "mp4", "mov", "avi")) {
                return FilterOption.OBJECT
            }
        }
        return FilterOption.UNKNOWN
    }
}