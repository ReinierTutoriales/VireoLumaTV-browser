package com.phlox.tvwebbrowser.activity.main

import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.adblock.BraveAdBlockEngine
import com.phlox.tvwebbrowser.adblock.ContentBlocker
import com.phlox.tvwebbrowser.adblock.ContentBlockerEngine
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

        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24 * 30 //30 days
        private const val PARTIAL_UPDATE_RETRY_MINUTES = 60 * 24 //1 day
        private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 10_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 15_000
        private const val MIN_DEFAULT_FILTER_LINES = 100
        private const val MIN_CUSTOM_FILTER_LINES = 1
        private const val EASY_PRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
        private const val EASY_LIST_SPANISH_URL = "https://easylist-downloads.adblockplus.org/easylistspanish.txt"
    }

    private data class FilterList(
        val name: String,
        val url: String,
        val cacheFileName: String,
        val requiresAdblockHeader: Boolean
    )

    private enum class FilterListSource {
        DOWNLOAD,
        CACHE
    }

    private data class ResolvedFilterList(
        val filterList: FilterList,
        val content: String,
        val source: FilterListSource
    )

    private val engine: ContentBlockerEngine = BraveAdBlockEngine()
    private val clientLock = Any()
    private var client: ContentBlocker? = null
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
        var loadedClient: ContentBlocker? = null
        var downloadAttempted = false
        var updated = false
        var partialUpdate = false
        try {
            withContext(Dispatchers.IO) ioContext@ {
                val serializedFile = File(TVBro.instance.filesDir, engine.cacheFileName)
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
                    val resolvedLists = resolveFilterLists(filterLists)
                    if (resolvedLists.isEmpty()) {
                        Log.w(TAG, "No usable adblock filter list text available")
                    } else {
                        if (resolvedLists.all { it.source == FilterListSource.CACHE }) {
                            val cachedClient = deserializeCachedList(serializedFile)
                            if (cachedClient != null) {
                                loadedClient = cachedClient
                                updated = true
                                partialUpdate = true
                                Log.i(TAG, "Using serialized adblock list because all downloads fell back to cached text")
                                return@ioContext
                            }
                            Log.w(TAG, "Serialized adblock list unavailable; compiling cached filter list text")
                        }
                        val combinedFilterList = buildCombinedFilterList(resolvedLists)
                        val freshClient = engine.compile(combinedFilterList)
                        if (freshClient != null) {
                            freshClient.serialize(serializedFile)
                            loadedClient = freshClient
                            updated = true
                            partialUpdate = resolvedLists.size < filterLists.size ||
                                    resolvedLists.any { it.source != FilterListSource.DOWNLOAD }
                            Log.i(
                                TAG,
                                "Compiled adblock lists. Downloaded: ${resolvedLists.namesFrom(FilterListSource.DOWNLOAD)}; " +
                                        "cached: ${resolvedLists.namesFrom(FilterListSource.CACHE)}"
                            )
                            return@ioContext
                        }
                        Log.w(TAG, "Adblock engine rejected filter lists: ${resolvedLists.joinToString { it.filterList.name }}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Can not prepare adblock filter lists", e)
                }
                //update failed: keep blocking with the last serialized list that worked
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
            if (updated) {
                if (partialUpdate) {
                    scheduleRetryAfterPartialUpdate(now.timeInMillis)
                } else {
                    config.adBlockListLastUpdate = now.timeInMillis
                }
            }
            if (downloadAttempted && loadedClient == null && !hasCurrentClient()) {
                Toast.makeText(TVBro.instance, "Error loading ad-blocker list", Toast.LENGTH_SHORT).show()
            }
        } finally {
            clientLoading.value = false
        }
    }

    private fun getConfiguredFilterLists(): List<FilterList> {
        val configuredUrl = config.adBlockListURL.value
        if (configuredUrl != Config.DEFAULT_ADBLOCK_LIST_URL) {
            val customCacheSuffix = configuredUrl.hashCode().toString().replace("-", "m")
            return listOf(
                FilterList(
                    "Custom",
                    configuredUrl,
                    "adblock_list_custom_$customCacheSuffix.txt",
                    requiresAdblockHeader = false
                )
            )
        }
        return listOf(
            FilterList(
                "EasyList",
                Config.DEFAULT_ADBLOCK_LIST_URL,
                "adblock_list_easylist.txt",
                requiresAdblockHeader = true
            ),
            FilterList(
                "EasyPrivacy",
                EASY_PRIVACY_URL,
                "adblock_list_easyprivacy.txt",
                requiresAdblockHeader = true
            ),
            FilterList(
                "EasyList Spanish",
                EASY_LIST_SPANISH_URL,
                "adblock_list_easylist_spanish.txt",
                requiresAdblockHeader = true
            )
        )
    }

    private fun resolveFilterLists(filterLists: List<FilterList>): List<ResolvedFilterList> {
        return filterLists.mapNotNull { filterList ->
            val cacheFile = File(TVBro.instance.filesDir, filterList.cacheFileName)
            try {
                Log.i(TAG, "Downloading adblock list: ${filterList.name}")
                val downloadedText = downloadFilterList(filterList)
                if (!isValidFilterList(filterList, downloadedText)) {
                    throw IllegalArgumentException("Invalid adblock list content: ${filterList.name}")
                }
                writeCacheFileAtomically(cacheFile, downloadedText)
                Log.i(TAG, "Downloaded valid adblock list: ${filterList.name}")
                ResolvedFilterList(filterList, downloadedText, FilterListSource.DOWNLOAD)
            } catch (e: Exception) {
                Log.w(TAG, "Can not download valid adblock list: ${filterList.name}", e)
                val cachedText = readCachedFilterList(filterList, cacheFile)
                if (cachedText != null) {
                    Log.i(TAG, "Using cached adblock list text: ${filterList.name}")
                    ResolvedFilterList(filterList, cachedText, FilterListSource.CACHE)
                } else {
                    Log.w(TAG, "No usable adblock list text: ${filterList.name}")
                    null
                }
            }
        }
    }

    private fun downloadFilterList(filterList: FilterList): String {
        val connection = URL(filterList.url).openConnection().apply {
            connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MS
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun writeCacheFileAtomically(cacheFile: File, text: String) {
        val tmpFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        tmpFile.writeText(text)
        if (!tmpFile.renameTo(cacheFile)) {
            tmpFile.delete()
            throw IllegalStateException("Can not replace adblock list cache: ${cacheFile.name}")
        }
    }

    private fun readCachedFilterList(filterList: FilterList, cacheFile: File): String? {
        if (!cacheFile.exists()) return null
        return try {
            val cachedText = cacheFile.readText()
            if (isValidFilterList(filterList, cachedText)) cachedText else null
        } catch (e: Exception) {
            Log.w(TAG, "Can not read cached adblock list text: ${filterList.name}", e)
            null
        }
    }

    private fun isValidFilterList(filterList: FilterList, content: String): Boolean {
        val normalizedContent = content.removePrefix("\uFEFF")
        val trimmedStart = normalizedContent.trimStart()
        if (looksLikeHtml(trimmedStart)) return false
        val lineCount = normalizedContent.lineSequence().count()
        if (filterList.requiresAdblockHeader) {
            val firstNonBlankLine = normalizedContent.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
            return firstNonBlankLine.startsWith("[Adblock Plus") && lineCount >= MIN_DEFAULT_FILTER_LINES
        }
        return normalizedContent.isNotBlank() && lineCount >= MIN_CUSTOM_FILTER_LINES
    }

    private fun looksLikeHtml(trimmedStart: String): Boolean {
        val lowerStart = trimmedStart.lowercase(Locale.US)
        return lowerStart.startsWith("<!doctype html") || lowerStart.startsWith("<html")
    }

    private fun buildCombinedFilterList(resolvedLists: List<ResolvedFilterList>): String {
        return buildString {
            resolvedLists.forEach { resolvedList ->
                appendLine("! ${resolvedList.filterList.name}")
                appendLine(resolvedList.content)
            }
        }
    }

    private fun List<ResolvedFilterList>.namesFrom(source: FilterListSource): String {
        return filter { it.source == source }
            .joinToString { it.filterList.name }
            .ifBlank { "none" }
    }

    private fun scheduleRetryAfterPartialUpdate(nowMillis: Long) {
        val autoUpdateIntervalMillis = AUTO_UPDATE_INTERVAL_MINUTES * 60_000L
        val partialRetryMillis = PARTIAL_UPDATE_RETRY_MINUTES * 60_000L
        config.adBlockListLastUpdate = nowMillis - autoUpdateIntervalMillis + partialRetryMillis
    }

    private fun deserializeCachedList(serializedFile: File): ContentBlocker? {
        return engine.deserialize(serializedFile)
    }

    private fun hasCurrentClient(): Boolean {
        return synchronized(clientLock) {
            client != null
        }
    }

    fun isAd(url: Uri, type: String?, baseUri: Uri): Boolean {
        val baseHost = baseUri.host ?: return false
        val result = try {
            synchronized(clientLock) {
                client?.shouldBlock(url, type, baseHost) ?: false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
        return result
    }
}
