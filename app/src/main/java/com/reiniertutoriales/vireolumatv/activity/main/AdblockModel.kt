package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.utils.BoundedReader
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.adblock.AdblockCache
import com.reiniertutoriales.vireolumatv.adblock.AdblockFilterListValidator
import com.reiniertutoriales.vireolumatv.adblock.BraveAdBlockEngine
import com.reiniertutoriales.vireolumatv.adblock.ContentBlocker
import com.reiniertutoriales.vireolumatv.adblock.ContentBlockerEngine
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.*

class AdblockModel @JvmOverloads constructor(
    private val engine: ContentBlockerEngine = BraveAdBlockEngine(),
    autoLoad: Boolean = true
) : ActiveModel() {
    companion object {
        const val TAG: String = "AdblockModel"

        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24 * 30 //30 days
        private const val PARTIAL_UPDATE_RETRY_MINUTES = 60 * 24 //1 day
        private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 10_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 15_000
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

    private val clientLock = Any()
    private var client: ContentBlocker? = null
    private var clientSource: String? = null
    val clientLoading = ObservableValue(false)
    val config = AppContext.provideConfig()

    init {
        if (autoLoad) loadAdBlockList(false)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun loadAdBlockList(forceReload: Boolean): Job = modelScope.launch {
        if (clientLoading.value) return@launch
        val configuredUrl = config.adBlockListURL.value
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
                val serializedFile = AdblockCache.fileFor(VireoLumaTVApp.instance.filesDir, engine, configuredUrl)
                val filterLists = getConfiguredFilterLists(configuredUrl)
                // Protect initial page requests while expired lists download in the background.
                // A refresh of an already active blocker does not allocate another cached native client.
                if (!hasCurrentClient(configuredUrl)) {
                    loadedClient = deserializeCachedList(serializedFile)
                    loadedClient?.let { cached ->
                        if (config.adBlockListURL.value == configuredUrl) {
                            synchronized(clientLock) {
                                client = cached
                                clientSource = configuredUrl
                            }
                        }
                    }
                }
                if (!needUpdate && loadedClient != null) {
                    Log.i(TAG, "Loaded cached adblock list")
                    return@ioContext
                }
                if (!needUpdate && hasCurrentClient(configuredUrl)) return@ioContext
                downloadAttempted = true
                try {
                    val resolvedLists = resolveFilterLists(filterLists)
                    if (resolvedLists.isEmpty()) {
                        Log.w(TAG, "No usable adblock filter list text available")
                    } else {
                        if (resolvedLists.all { it.source == FilterListSource.CACHE }) {
                            val cachedClient = loadedClient ?: if (hasCurrentClient(configuredUrl)) null else deserializeCachedList(serializedFile)
                            if (cachedClient != null) {
                                loadedClient = cachedClient
                                updated = true
                                partialUpdate = true
                                Log.i(TAG, "Using serialized adblock list because all downloads fell back to cached text")
                                return@ioContext
                            }
                            Log.w(TAG, "Serialized adblock list unavailable; compiling cached filter list text")
                        }
                        // Failed downloads must not recompile the same rules while a working client exists.
                        if (resolvedLists.all { it.source == FilterListSource.CACHE } && hasCurrentClient(configuredUrl)) {
                            updated = true
                            partialUpdate = true
                            return@ioContext
                        }
                        val combinedFilterList = buildCombinedFilterList(resolvedLists)
                        val freshClient = engine.compile(combinedFilterList)
                        if (freshClient != null) {
                            val cacheWritten = AdblockCache.write(serializedFile, freshClient)
                            if (!cacheWritten) Log.w(TAG, "Compiled rules active but cache write failed; will retry")
                            loadedClient = freshClient
                            updated = true
                            partialUpdate = !cacheWritten || resolvedLists.size < filterLists.size ||
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Can not prepare adblock filter lists", e)
                }
                //update failed: keep blocking with the last serialized list that worked
                loadedClient = loadedClient ?: if (hasCurrentClient(configuredUrl)) null else deserializeCachedList(serializedFile)
                if (loadedClient != null) {
                    Log.i(TAG, "Using cached adblock list after update failure")
                } else if (hasCurrentClient()) {
                    Log.i(TAG, "Keeping the active blocker after update failure")
                } else {
                    Log.w(TAG, "No usable adblock list available")
                }
            }
            if (config.adBlockListURL.value != configuredUrl) return@launch
            //if nothing could be loaded keep the current client (if any) instead of replacing it with an empty one
            loadedClient?.let {
                synchronized(clientLock) {
                    this@AdblockModel.client = it
                    clientSource = configuredUrl
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
                Toast.makeText(VireoLumaTVApp.instance, "Error loading ad-blocker list", Toast.LENGTH_SHORT).show()
            }
        } finally {
            clientLoading.value = false
            if (config.adBlockListURL.value != configuredUrl) loadAdBlockList(true)
        }
    }

    private fun getConfiguredFilterLists(configuredUrl: String): List<FilterList> {
        if (configuredUrl != Config.DEFAULT_ADBLOCK_LIST_URL) {
            val customCacheSuffix = AdblockCache.sourceKey(configuredUrl)
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
            val cacheFile = File(VireoLumaTVApp.instance.filesDir, filterList.cacheFileName)
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
        return BoundedReader(connection.inputStream.bufferedReader(), 8 * 1024 * 1024).use { it.readText() }
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
            val cachedText = BoundedReader(cacheFile.bufferedReader(), 8 * 1024 * 1024).use { it.readText() }
            if (isValidFilterList(filterList, cachedText)) cachedText else null
        } catch (e: Exception) {
            Log.w(TAG, "Can not read cached adblock list text: ${filterList.name}", e)
            null
        }
    }

    private fun isValidFilterList(filterList: FilterList, content: String): Boolean {
        return AdblockFilterListValidator.isValid(
            content = content,
            requiresAdblockHeader = filterList.requiresAdblockHeader
        )
    }

    private fun buildCombinedFilterList(resolvedLists: List<ResolvedFilterList>): String {
        require(resolvedLists.sumOf { it.content.length.toLong() } <= 16L * 1024 * 1024) {
            "Combined adblock text exceeds the low-memory budget"
        }
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
        return try {
            engine.deserialize(serializedFile)
        } catch (e: Exception) {
            Log.w(TAG, "Can not restore compiled adblock cache", e)
            null
        }
    }

    private fun hasCurrentClient(source: String? = null): Boolean {
        return synchronized(clientLock) {
            client != null && (source == null || clientSource == source)
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
