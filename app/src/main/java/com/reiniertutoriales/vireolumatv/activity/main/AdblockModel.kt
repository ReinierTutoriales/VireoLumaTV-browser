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
import com.reiniertutoriales.vireolumatv.adblock.AdblockResources
import com.reiniertutoriales.vireolumatv.adblock.RustAdBlockEngine
import com.reiniertutoriales.vireolumatv.adblock.ContentBlocker
import com.reiniertutoriales.vireolumatv.adblock.ContentBlockerEngine
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
    private val engine: ContentBlockerEngine = RustAdBlockEngine { AdblockResources.json(VireoLumaTVApp.instance) },
    autoLoad: Boolean = true
) : ActiveModel() {
    companion object {
        const val TAG: String = "AdblockModel"

        // uBlock "Quick fixes" expire in 8 hours, the others in 4-5 days. With conditional
        // requests an unchanged list costs one HTTP 304, and adblock-rust recompiles in about a second.
        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24
        // Some lists failed or could not be cached: retry the same day, not the next one.
        private const val PARTIAL_UPDATE_RETRY_MINUTES = 60 * 6
        // No server reached (TV started before Wi-Fi, DNS down): retry soon. Regaining the network
        // also retries immediately, see updateIfDue().
        private const val FAILURE_RETRY_MINUTES = 60
        private const val EASY_PRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
        private const val EASY_LIST_SPANISH_URL = "https://easylist-downloads.adblockplus.org/easylistspanish.txt"
        private const val UBO_PRIMARY = "https://ublockorigin.github.io/uAssets/filters/"
        private val UBO_MIRRORS = listOf(
            "https://ublockorigin.pages.dev/filters/",
            "https://cdn.jsdelivr.net/gh/uBlockOrigin/uAssets@latest/filters/"
        )
        // Brave ad-block 0.0.4 caches left by earlier versions.
        private val LEGACY_CACHE_SUFFIXES = listOf("-adblock_ser.dat", "-adblock_ser.dat.popup", "-adblock_ser.dat.cosmetic")
    }

    private data class FilterList(
        val name: String,
        val url: String,
        val cacheFileName: String,
        val requiresAdblockHeader: Boolean,
        val mirrors: List<String> = emptyList(),
        /** uBlock Origin lists: required "! Title:" prefix and `!#include` sub-lists to fetch. */
        val uboTitle: String? = null
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
        val decisions = object : LruCache<DecisionKey, Int>(256 * 1024) {
            override fun sizeOf(key: DecisionKey, value: Int): Int =
                64 + 2 * (key.url.length + (key.type?.length ?: 0) + key.baseHost.length)
        }
        // Page filters of recent frame URLs (ad iframes and reloads repeat them): ~1 MiB of text.
        val pageFilters = object : LruCache<String, String>(512 * 1024) {
            override fun sizeOf(key: String, value: String): Int = key.length + value.length
        }
    }
    // Publishing a new generation must not make Main wait for a WebView request/native matcher.
    @Volatile private var installedClient: InstalledClient? = null
    // Installs from the IO loader and onClear() on Main: nothing may be installed after clearing.
    private val installLock = Any()
    @Volatile private var cleared = false
    private fun installClient(value: ContentBlocker, source: String) = synchronized(installLock) {
        val current = installedClient
        if (cleared) {
            if (current?.blocker !== value) value.release()
            return@synchronized
        }
        if (current == null || current.blocker !== value || current.source != source) {
            installedClient = InstalledClient(value, source)
            // Native rules are freed once in-flight checks on them have finished.
            if (current != null && current.blocker !== value) current.blocker.release()
        }
    }
    /** `$popup` rules, compiled into their own small engine (no popup request type exists). */
    private class Auxiliary(val source: String, val popup: ContentBlocker?) {
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
                        val freshClient = engine.compile(filterTexts(resolvedLists))
                        if (freshClient != null) {
                            val cacheWritten = AdblockCache.write(serializedFile, freshClient)
                            if (!cacheWritten) Log.w(TAG, "Compiled rules active but cache write failed; will retry")
                            else deleteLegacyCaches()
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
            // A client that is not installed below is freed in the finally block.
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
            // Compiled or restored but not installed (list URL changed, model cleared, cancelled
            // while the native compile ran): free its native memory now.
            loadedClient?.let { if (it !== installedClient?.blocker) it.release() }
            clientLoading.value = false
            if (!cleared && config.adBlockListURL.value != configuredUrl) loadAdBlockList(true)
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
            ),
            // Anti-adblock and stream-site fixes, exceptions for broken sites, scam/malware hosts.
            uboList("uBlock filters", "filters.txt"),
            uboList("uBlock Quick fixes", "quick-fixes.txt"),
            uboList("uBlock Unbreak", "unbreak.txt"),
            uboList("uBlock Badware risks", "badware.txt")
        )
    }

    private fun uboList(name: String, file: String) = FilterList(
        name,
        UBO_PRIMARY + file,
        "adblock_list_ubo_$file",
        requiresAdblockHeader = false,
        mirrors = UBO_MIRRORS.map { it + file },
        uboTitle = "uBlock"
    )

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
                        val downloadedText = prepareDownloadedList(filterList, result)
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

    /** uBO directives are evaluated once here; the saved text is what gets compiled. */
    private fun prepareDownloadedList(filterList: FilterList, result: AdblockListDownloader.Result.Downloaded): String {
        if (filterList.uboTitle == null) return result.text
        val text = FilterListPreprocessor.applyDirectives(result.text)
        val includes = FilterListPreprocessor.includes(text)
        if (includes.isEmpty()) return text
        val base = result.url.substringBeforeLast('/') + "/"
        return buildString(text.length * 2) {
            append(text)
            for (name in includes) {
                // A missing sub-list makes the whole list fail and fall back to the saved copy.
                val included = AdblockListDownloader.download(listOf(base + name), requiresAdblockHeader = false)
                append('\n').append(FilterListPreprocessor.applyDirectives(included))
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

    /** Marks a list generation without popup rules, so it is not rebuilt on every start. */
    private fun noPopupMarkerFor(source: String) = File(popupFileFor(source).path + ".none")

    private fun hasAuxiliary(source: String): Boolean = auxiliary?.source == source

    /** Loads the popup engine saved by the last [buildAuxiliary]. */
    private fun restoreAuxiliary(source: String): Auxiliary? {
        val popupFile = popupFileFor(source)
        return try {
            when {
                popupFile.exists() -> engine.deserialize(popupFile)?.let { Auxiliary(source, it) }
                noPopupMarkerFor(source).exists() -> Auxiliary(source, null)
                else -> null
            }?.also { installAuxiliary(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Can not restore popup rules", e)
            null
        }
    }

    private fun buildAuxiliary(source: String, texts: List<String>) {
        try {
            val popupRules = StringBuilder()
            for (text in texts) popupRules.append(FilterListPreprocessor.extract(text).popupRules)
            val popup = if (popupRules.isEmpty()) null else engine.compile(popupRules.toString())
            val popupFile = popupFileFor(source)
            val marker = noPopupMarkerFor(source)
            if (popup != null && AdblockCache.write(popupFile, popup)) marker.delete()
            else {
                popupFile.delete()
                if (popup == null) AdblockCache.writeText(marker, "\n")
            }
            installAuxiliary(Auxiliary(source, popup))
            Log.i(TAG, "Prepared popup rules: ${popupRules.count { it == '\n' }}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Can not prepare popup rules", e)
        }
    }

    private fun installAuxiliary(value: Auxiliary) = synchronized(installLock) {
        if (cleared || config.adBlockListURL.value != value.source) {
            value.popup?.release()
            return@synchronized
        }
        val previous = auxiliary
        auxiliary = value
        if (previous != null && previous.popup !== value.popup) previous.popup?.release()
    }

    /** Removes caches of the former ad-block 0.0.4 engine once the new engine saved its own. */
    private fun deleteLegacyCaches() {
        VireoLumaTVApp.instance.filesDir.listFiles()?.forEach { file ->
            if (LEGACY_CACHE_SUFFIXES.any { file.name.endsWith(it) }) file.delete()
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
        if (!AdblockFilterListValidator.isValid(content = content, requiresAdblockHeader = filterList.requiresAdblockHeader)) {
            return false
        }
        val title = filterList.uboTitle ?: return true
        // An error page or a captive portal must not replace a uBlock Origin list.
        return content.lineSequence().take(8).any { it.startsWith("! Title: $title") }
    }

    /** Each list is handed to the engine on its own: no combined multi-megabyte copy is built. */
    private fun filterTexts(resolvedLists: List<ResolvedFilterList>): List<String> {
        require(resolvedLists.sumOf { it.content.length.toLong() } <= 16L * 1024 * 1024) {
            "Combined adblock text exceeds the low-memory budget"
        }
        return resolvedLists.map { it.content }
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
        synchronized(installLock) {
            cleared = true
            installedClient?.blocker?.release()
            installedClient = null
            auxiliary?.popup?.release()
            auxiliary = null
        }
        super.onClear()
    }

    fun isAd(url: Uri, type: String?, baseUri: Uri): Boolean = decision(url, type, baseUri) != ContentBlocker.ALLOW

    /**
     * `data:` URL of the `$redirect` resource for a blocked request (uBO `noopjs`, `1x1.gif`, empty
     * VAST...), so the page sees a harmless answer instead of an error it can detect.
     */
    fun redirectFor(url: Uri, type: String?, baseUri: Uri): String? {
        if (decision(url, type, baseUri) != ContentBlocker.BLOCK_REDIRECT) return null
        val current = installedClient ?: return null
        return try {
            if (current.blocker.isConcurrent) current.blocker.redirect(url, type, baseUri)
            else synchronized(current.lock) { current.blocker.redirect(url, type, baseUri) }
        } catch (e: Exception) {
            null
        }
    }

    private fun decision(url: Uri, type: String?, baseUri: Uri): Int {
        val baseHost = baseUri.host ?: return ContentBlocker.ALLOW
        val current = installedClient ?: return ContentBlocker.ALLOW
        val text = url.toString()
        val key = if (text.length <= 2048 && baseHost.length <= 255)
            DecisionKey(text, type, baseHost) else null
        // LruCache is internally synchronized: cached answers never wait for a native match that
        // another WebView thread is running.
        key?.let { current.decisions.get(it) }?.let { return it }
        val result = try {
            // adblock-rust is Sync: WebView worker threads match in parallel.
            if (current.blocker.isConcurrent) current.blocker.check(url, type, baseUri)
            else synchronized(current.lock) { current.blocker.check(url, type, baseUri) }
        } catch (e: Exception) {
            if (current.failureLogged.compareAndSet(false, true)) {
                Log.e(TAG, "Adblock matcher failed; later requests can retry", e)
            }
            return ContentBlocker.ALLOW
        }
        if (key != null) current.decisions.put(key, result)
        return result
    }

    /** Memory pressure: decisions are rebuilt on demand, rules stay loaded. */
    fun trimMemory() {
        installedClient?.decisions?.evictAll()
        installedClient?.pageFilters?.evictAll()
    }

    /**
     * "Tab-under": a click opens the content elsewhere and sends this tab to a popunder network.
     * Only the dedicated popup rules are used, and only for third-party destinations.
     */
    fun isTabUnderAd(url: Uri, page: Uri?): Boolean {
        if (url.scheme != "http" && url.scheme != "https") return false
        val host = url.host?.lowercase(Locale.ROOT) ?: return false
        val pageHost = page?.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return false
        if (isSameSite(host, pageHost)) return false
        val aux = auxiliary ?: return false
        val popup = aux.popup ?: return false
        return try {
            if (popup.isConcurrent) popup.shouldBlock(url, "document", pageHost)
            else synchronized(aux.lock) { popup.shouldBlock(url, "document", pageHost) }
        } catch (e: Exception) {
            false
        }
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
                if (popup.isConcurrent) popup.shouldBlock(url, "document", openerHost ?: host)
                else synchronized(aux.lock) { popup.shouldBlock(url, "document", openerHost ?: host) }
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

    /**
     * Element hiding selectors and scriptlets for a document (any frame) as JSON, or "". Called
     * from the JavaBridge thread by assets/adblock/page_filters.js.
     */
    fun pageFilters(pageUrl: String): String {
        val current = installedClient ?: return ""
        current.pageFilters.get(pageUrl)?.let { return it }
        return try {
            (if (current.blocker.isConcurrent) current.blocker.pageFilters(pageUrl)
            else synchronized(current.lock) { current.blocker.pageFilters(pageUrl) })
                .also { if (pageUrl.length <= 2048) current.pageFilters.put(pageUrl, it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun hiddenSelectors(pageUrl: String, classes: String, ids: String): String {
        val current = installedClient ?: return "[]"
        return try {
            if (current.blocker.isConcurrent) current.blocker.hiddenSelectors(pageUrl, classes, ids)
            else synchronized(current.lock) { current.blocker.hiddenSelectors(pageUrl, classes, ids) }
        } catch (e: Exception) {
            "[]"
        }
    }
}
