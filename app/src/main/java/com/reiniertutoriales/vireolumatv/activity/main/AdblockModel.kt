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
import com.reiniertutoriales.vireolumatv.adblock.CosmeticFilters
import com.reiniertutoriales.vireolumatv.adblock.FilterListPreprocessor
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AdblockModel @JvmOverloads constructor(
    private val engine: ContentBlockerEngine = BraveAdBlockEngine(),
    autoLoad: Boolean = true
) : ActiveModel() {
    companion object {
        const val TAG: String = "AdblockModel"

        // EasyList and EasyPrivacy declare "! Expires: 4 days".
        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24 * 4
        // Some lists failed or could not be cached: retry the same day, not the next one.
        private const val PARTIAL_UPDATE_RETRY_MINUTES = 60 * 6
        // No server reached (TV started before Wi-Fi, DNS down): retry soon. Regaining the network
        // also retries immediately, see updateIfDue().
        private const val FAILURE_RETRY_MINUTES = 60
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
        /** HTTP 304: the server confirmed the saved copy is current. */
        NOT_MODIFIED,
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
        // A broken native matcher can fail for every resource on a busy page.
        val failureLogged = AtomicBoolean(false)
        // ~256 KiB of keys: several hundred URLs, enough for a heavy page plus its media playlist.
        val decisions = object : LruCache<DecisionKey, Boolean>(256 * 1024) {
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
    /** Rules the native engine skips: `$popup` (as a separate client) and site element hiding. */
    private class Auxiliary(val source: String, val popup: ContentBlocker?, val cosmetics: CosmeticFilters) {
        val lock = Any()
    }
    @Volatile private var auxiliary: Auxiliary? = null
    @Volatile private var lastAttemptUnreachable = false

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

    /** Cheap check for callers that run often (Activity resume, network regained). */
    fun updateIfDue(networkRestored: Boolean = false) {
        if (clientLoading.value) return
        val retryNow = networkRestored && lastAttemptUnreachable
        if (retryNow || isUpdateDue(System.currentTimeMillis())) loadAdBlockList(retryNow)
    }

    private fun isUpdateDue(now: Long): Boolean {
        val retryAt = config.adBlockListNextRetry
        return if (retryAt != 0L) now >= retryAt
        else now >= config.adBlockListLastUpdate + AUTO_UPDATE_INTERVAL_MINUTES * 60_000L
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun loadAdBlockList(forceReload: Boolean): Job = modelScope.launch {
        if (clientLoading.value) return@launch
        val configuredUrl = config.adBlockListURL.value
        val now = System.currentTimeMillis()
        val needUpdate = forceReload || isUpdateDue(now)
        clientLoading.value = true
        var loadedClient: ContentBlocker? = null
        var downloadAttempted = false
        var updated = false
        var partialUpdate = false
        var reachedServer = false
        try {
            withContext(Dispatchers.IO) ioContext@ {
                val filesDir = VireoLumaTVApp.instance.filesDir
                val serializedFile = AdblockCache.fileFor(filesDir, engine, configuredUrl)
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
                // Popup and element hiding rules: from their own caches, or once from the saved list text.
                if (!hasAuxiliary(configuredUrl)) {
                    restoreAuxiliary(configuredUrl) ?: run {
                        val texts = filterLists.mapNotNull { list ->
                            readCachedFilterList(list, File(filesDir, list.cacheFileName))
                        }
                        if (texts.isNotEmpty()) buildAuxiliary(configuredUrl, texts)
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
                    reachedServer = resolvedLists.any { it.source != FilterListSource.CACHE }
                    val missingLists = resolvedLists.size < filterLists.size
                    val usedCacheFallback = resolvedLists.any { it.source == FilterListSource.CACHE }
                    if (resolvedLists.isEmpty()) {
                        Log.w(TAG, "No usable adblock filter list text available")
                    } else {
                        // Nothing new (HTTP 304 or failed downloads): keep the compiled rules.
                        if (resolvedLists.none { it.source == FilterListSource.DOWNLOAD }) {
                            val cachedClient = loadedClient ?: if (hasCurrentClient(configuredUrl)) null else deserializeCachedList(serializedFile)
                            if (cachedClient != null || hasCurrentClient(configuredUrl)) {
                                loadedClient = cachedClient
                                updated = true
                                partialUpdate = missingLists || usedCacheFallback
                                if (!hasAuxiliary(configuredUrl)) {
                                    buildAuxiliary(configuredUrl, resolvedLists.map { it.content })
                                }
                                Log.i(TAG, "Adblock lists unchanged. Not modified: " +
                                        "${resolvedLists.namesFrom(FilterListSource.NOT_MODIFIED)}; " +
                                        "cached: ${resolvedLists.namesFrom(FilterListSource.CACHE)}")
                                return@ioContext
                            }
                            Log.w(TAG, "Serialized adblock list unavailable; compiling saved filter list text")
                        }
                        val combinedFilterList = buildCombinedFilterList(resolvedLists)
                        val freshClient = engine.compile(combinedFilterList)
                        if (freshClient != null) {
                            val cacheWritten = AdblockCache.write(serializedFile, freshClient)
                            if (!cacheWritten) Log.w(TAG, "Compiled rules active but cache write failed; will retry")
                            loadedClient = freshClient
                            updated = true
                            partialUpdate = !cacheWritten || missingLists || usedCacheFallback
                            buildAuxiliary(configuredUrl, resolvedLists.map { it.content })
                            Log.i(
                                TAG,
                                "Compiled adblock lists. Downloaded: ${resolvedLists.namesFrom(FilterListSource.DOWNLOAD)}; " +
                                        "not modified: ${resolvedLists.namesFrom(FilterListSource.NOT_MODIFIED)}; " +
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
            // HTTP 304 is a successful check: the saved copy is the current list.
            if (updated && reachedServer) config.adBlockListLastUpdate = now
            if (downloadAttempted) {
                lastAttemptUnreachable = !reachedServer
                config.adBlockListNextRetry = when {
                    !reachedServer -> now + FAILURE_RETRY_MINUTES * 60_000L
                    !updated || partialUpdate -> now + PARTIAL_UPDATE_RETRY_MINUTES * 60_000L
                    else -> 0L
                }
            }
            updateResult.value = when {
                updated && reachedServer && !partialUpdate -> UpdateResult.UPDATED
                updated && reachedServer -> UpdateResult.PARTIAL
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
            val validatorsFile = File(cacheFile.path + ".meta")
            try {
                Log.i(TAG, "Downloading adblock list: ${filterList.name}")
                // Validators are only sent while the saved copy they describe is usable.
                val savedValidators = readValidators(validatorsFile)
                val cachedText = savedValidators?.let { readCachedFilterList(filterList, cacheFile) }
                val validators = if (cachedText != null) savedValidators else null
                when (val result = AdblockListDownloader.fetch(listOf(filterList.url) + filterList.mirrors,
                    filterList.requiresAdblockHeader, validators)) {
                    AdblockListDownloader.Result.NotModified -> {
                        Log.i(TAG, "Adblock list not modified: ${filterList.name}")
                        ResolvedFilterList(filterList, cachedText!!, FilterListSource.NOT_MODIFIED)
                    }
                    is AdblockListDownloader.Result.Downloaded -> {
                        val downloadedText = result.text
                        if (!isValidFilterList(filterList, downloadedText)) {
                            throw IllegalArgumentException("Invalid adblock list content: ${filterList.name}")
                        }
                        if (AdblockCache.writeText(cacheFile, downloadedText)) {
                            writeValidators(validatorsFile, result.validators)
                        } else {
                            validatorsFile.delete()
                            Log.w(TAG, "Valid download available, but text cache could not be saved: ${filterList.name}")
                        }
                        Log.i(TAG, "Downloaded valid adblock list: ${filterList.name}")
                        ResolvedFilterList(filterList, downloadedText, FilterListSource.DOWNLOAD)
                    }
                }
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

    private fun readValidators(file: File): AdblockListDownloader.Validators? = try {
        if (!file.exists() || file.length() > 4096) null else {
            val lines = file.readLines()
            val url = lines.getOrNull(0)?.takeIf { it.isNotEmpty() }
            val etag = lines.getOrNull(1)?.takeIf { it.isNotEmpty() }
            val lastModified = lines.getOrNull(2)?.takeIf { it.isNotEmpty() }
            if (url == null || (etag == null && lastModified == null)) null
            else AdblockListDownloader.Validators(url, etag, lastModified)
        }
    } catch (e: Exception) {
        null
    }

    private fun writeValidators(file: File, validators: AdblockListDownloader.Validators?) {
        if (validators == null) {
            file.delete()
            return
        }
        // Header values cannot contain line breaks; keep the file line based.
        val text = listOf(validators.url, validators.etag ?: "", validators.lastModified ?: "")
            .joinToString("\n") { it.replace('\n', ' ').replace('\r', ' ') }
        if (!AdblockCache.writeText(file, text)) file.delete()
    }

    private fun popupFileFor(source: String) =
        File(VireoLumaTVApp.instance.filesDir, AdblockCache.fileFor(VireoLumaTVApp.instance.filesDir, engine, source).name + ".popup")

    private fun cosmeticFileFor(source: String) =
        File(VireoLumaTVApp.instance.filesDir, AdblockCache.fileFor(VireoLumaTVApp.instance.filesDir, engine, source).name + ".cosmetic")

    private fun hasAuxiliary(source: String): Boolean = auxiliary?.source == source

    /** Loads the popup client and element hiding rules saved by the last [buildAuxiliary]. */
    private fun restoreAuxiliary(source: String): Auxiliary? {
        val popupFile = popupFileFor(source)
        val cosmeticFile = cosmeticFileFor(source)
        if (!cosmeticFile.exists()) return null
        return try {
            val popup = if (popupFile.exists()) engine.deserialize(popupFile) else null
            val cosmetics = CosmeticFilters.parse(
                BoundedReader(cosmeticFile.bufferedReader(), 4 * 1024 * 1024).use { it.readText() })
            Auxiliary(source, popup, cosmetics).also { installAuxiliary(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Can not restore popup/element hiding rules", e)
            null
        }
    }

    private fun buildAuxiliary(source: String, texts: List<String>) {
        try {
            val popupRules = StringBuilder()
            val cosmeticRules = StringBuilder()
            for (text in texts) {
                val extracted = FilterListPreprocessor.extract(text)
                popupRules.append(extracted.popupRules)
                cosmeticRules.append(extracted.cosmeticRules)
            }
            val popup = if (popupRules.isEmpty()) null else engine.compile(popupRules.toString())
            val popupFile = popupFileFor(source)
            if (popup == null || !AdblockCache.write(popupFile, popup)) popupFile.delete()
            val cosmeticFile = cosmeticFileFor(source)
            // Written last: its presence marks a complete auxiliary cache.
            if (!AdblockCache.writeText(cosmeticFile, cosmeticRules.toString().ifEmpty { "\n" })) cosmeticFile.delete()
            installAuxiliary(Auxiliary(source, popup, CosmeticFilters.parse(cosmeticRules.toString())))
            Log.i(TAG, "Prepared popup rules: ${popup != null}; element hiding rules: ${cosmeticRules.count { it == '\n' }}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Can not prepare popup/element hiding rules", e)
        }
    }

    private fun installAuxiliary(value: Auxiliary) {
        if (config.adBlockListURL.value == value.source) auxiliary = value
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
        auxiliary = null
        super.onClear()
    }

    fun isAd(url: Uri, type: String?, baseUri: Uri): Boolean {
        val baseHost = baseUri.host ?: return false
        val current = installedClient ?: return false
        val result = try {
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
            if (current.failureLogged.compareAndSet(false, true)) {
                Log.e(TAG, "Adblock matcher failed; later requests can retry", e)
            }
            false
        }
        return result
    }

    /**
     * New window opened from [opener]. Popup rules ignored by the native engine come first; a
     * third-party popup whose address is on the ad server lists is blocked as well (PopAds-style
     * popunders open on the user's first click, so they always carry a user gesture).
     */
    fun isPopupAd(url: Uri, opener: Uri?): Boolean {
        val host = url.host?.lowercase(Locale.ROOT) ?: return false
        if (url.scheme != "http" && url.scheme != "https") return false
        val openerHost = opener?.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val aux = auxiliary
        val popup = aux?.popup
        if (popup != null) {
            val blocked = try {
                synchronized(aux.lock) { popup.shouldBlock(url, "document", openerHost ?: host) }
            } catch (e: Exception) {
                Log.w(TAG, "Popup matcher failed", e)
                false
            }
            if (blocked) return true
        }
        if (openerHost == null || isSameSite(host, openerHost)) return false
        return isAd(url, "subdocument", opener)
    }

    private fun isSameSite(a: String, b: String): Boolean =
        a == b || a.endsWith(".$b") || b.endsWith(".$a")

    /** CSS hiding this site's ad containers, or "" (called from WebView/JavaBridge threads). */
    fun cosmeticCss(host: String?): String = auxiliary?.cosmetics?.cssFor(host) ?: ""
}
