package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.utils.BoundedReader
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.adblock.AdblockCache
import com.reiniertutoriales.vireolumatv.adblock.AdblockFilterListValidator
import com.reiniertutoriales.vireolumatv.adblock.AdblockListDownloader
import com.reiniertutoriales.vireolumatv.R
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
import java.util.*

class AdblockModel @JvmOverloads constructor(
    private val engine: ContentBlockerEngine = BraveAdBlockEngine(),
    autoLoad: Boolean = true
) : ActiveModel() {
    companion object {
        const val TAG: String = "AdblockModel"

        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24 * 7 //7 days
        private const val PARTIAL_UPDATE_RETRY_MINUTES = 60 * 24 //1 day
        private const val EASY_PRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
        private const val EASY_LIST_SPANISH_URL = "https://easylist-downloads.adblockplus.org/easylistspanish.txt"
    }

    private data class FilterList(
        val name: String,
        val url: String,
        val cacheFileName: String,
        val requiresAdblockHeader: Boolean,
        val mirrors: List<String> = emptyList()
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

    private data class DecisionKey(val url: String, val type: String?, val baseHost: String)
    private class InstalledClient(val blocker: ContentBlocker, val source: String) {
        val lock = Any()
        val decisions = object : LruCache<DecisionKey, Boolean>(64 * 1024) {
            override fun sizeOf(key: DecisionKey, value: Boolean): Int =
                64 + 2 * (key.url.length + (key.type?.length ?: 0) + key.baseHost.length)
        }
    }
    // Publishing a new generation must not make Main wait for a WebView request/native matcher.
    @Volatile private var installedClient: InstalledClient? = null
    private fun installClient(value: ContentBlocker, source: String) {
        val current = installedClient
        if (current == null || current.blocker !== value || current.source != source) {
            installedClient = InstalledClient(value, source)
        }
    }
    enum class UpdateResult { IDLE, UPDATED, PARTIAL, CACHED, ERROR }
    val updateResult = ObservableValue(UpdateResult.IDLE)
    val clientLoading = ObservableValue(false)
    val config = AppContext.provideConfig()

    private val sourceObserver: (String) -> Unit = { loadAdBlockList(true) }

    init {
        if (autoLoad) {
            config.adBlockListURL.subscribe(sourceObserver, notifyOnSubscribe = false)
            loadAdBlockList(false)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun loadAdBlockList(forceReload: Boolean): Job = modelScope.launch {
        if (clientLoading.value) return@launch
        val configuredUrl = config.adBlockListURL.value
        val checkDate = Calendar.getInstance()
        checkDate.timeInMillis = config.adBlockListLastUpdate
        checkDate.add(Calendar.MINUTE, AUTO_UPDATE_INTERVAL_MINUTES)
        val now = Calendar.getInstance()
        val retryAt = config.adBlockListNextRetry
        val needUpdate = forceReload || if (retryAt != 0L) now.timeInMillis >= retryAt else checkDate.before(now)
        clientLoading.value = true
        var loadedClient: ContentBlocker? = null
        var downloadAttempted = false
        var updated = false
        var partialUpdate = false
        var downloadedAny = false
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
                            installClient(cached, configuredUrl)
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
                    downloadedAny = resolvedLists.any { it.source == FilterListSource.DOWNLOAD }
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
                installClient(it, configuredUrl)
            }
            if (updated && downloadedAny) config.adBlockListLastUpdate = now.timeInMillis
            if (downloadAttempted) {
                config.adBlockListNextRetry = if (!updated || partialUpdate)
                    now.timeInMillis + PARTIAL_UPDATE_RETRY_MINUTES * 60_000L else 0L
            }
            updateResult.value = when {
                updated && downloadedAny && !partialUpdate -> UpdateResult.UPDATED
                updated && downloadedAny -> UpdateResult.PARTIAL
                hasCurrentClient() -> UpdateResult.CACHED
                else -> UpdateResult.ERROR
            }
            if (downloadAttempted && !hasCurrentClient()) {
                Toast.makeText(VireoLumaTVApp.instance, R.string.adblock_update_error, Toast.LENGTH_SHORT).show()
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
                requiresAdblockHeader = true,
                mirrors = listOf("https://easylist-downloads.adblockplus.org/easylist.txt")
            ),
            FilterList(
                "EasyPrivacy",
                EASY_PRIVACY_URL,
                "adblock_list_easyprivacy.txt",
                requiresAdblockHeader = true,
                mirrors = listOf("https://easylist-downloads.adblockplus.org/easyprivacy.txt")
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
                if (!AdblockCache.writeText(cacheFile, downloadedText)) {
                    Log.w(TAG, "Valid download available, but text cache could not be saved: ${filterList.name}")
                }
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

    private fun downloadFilterList(filterList: FilterList): String = AdblockListDownloader.download(
        listOf(filterList.url) + filterList.mirrors, filterList.requiresAdblockHeader
    )

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

    private fun deserializeCachedList(serializedFile: File): ContentBlocker? {
        return try {
            engine.deserialize(serializedFile)
        } catch (e: Exception) {
            Log.w(TAG, "Can not restore compiled adblock cache", e)
            null
        }
    }

    private fun hasCurrentClient(source: String? = null): Boolean {
        val current = installedClient ?: return false
        return source == null || current.source == source
    }

    override fun onClear() {
        config.adBlockListURL.unsubscribe(sourceObserver)
        installedClient = null
        super.onClear()
    }

    fun isAd(url: Uri, type: String?, baseUri: Uri): Boolean {
        val baseHost = baseUri.host ?: return false
        val result = try {
            val current = installedClient ?: return false
            synchronized(current.lock) {
                val activeClient = current.blocker
                val decisions = current.decisions
                val text = url.toString()
                val key = if (text.length <= 2048 && baseHost.length <= 255)
                    DecisionKey(text, type, baseHost) else null
                key?.let { decisions.get(it) }?.let { return@synchronized it }
                val blocked = activeClient.shouldBlock(url, type, baseHost)
                if (key != null) decisions.put(key, blocked)
                blocked
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
        return result
    }
}
