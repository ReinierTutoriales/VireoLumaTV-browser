package com.reiniertutoriales.vireolumatv.activity.main

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.model.SiteSettingsLookup
import com.reiniertutoriales.vireolumatv.model.dao.SiteSettingsDao
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModel
import com.reiniertutoriales.vireolumatv.utils.hasInternet
import com.reiniertutoriales.vireolumatv.utils.observable.LiveList
import com.reiniertutoriales.vireolumatv.utils.observable.LiveValue
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * The open tabs of the current mode and the one on screen. Only that tab keeps a WebView (plus
 * the page that opened a visible popup); the others keep their saved history and get a new
 * WebView when shown again. Saves and deletions are serialized so they reach the database in order.
 */
class TabStore : ScreenModel() {
    val currentTab = LiveValue<BrowserTab?>(null)
    val openTabs = LiveList<BrowserTab>()
    var loaded = false
        private set

    private val config = AppContext.config
    /** Orders saves and deletions; held only around the IO part. */
    @VisibleForTesting internal val storage = Mutex()
    private var loadedIncognito = config.privateBrowsing

    init {
        // A tab's stored position is its place in the list; reordering stores the new places.
        openTabs.observe({ tabs ->
            var moved = false
            tabs.forEachIndexed { index, tab ->
                if (tab.position != index) {
                    tab.position = index
                    moved = true
                }
            }
            if (moved) {
                val snapshot = tabs.toList()
                workScope.launch { AppDatabase.db.tabDao().updatePositions(snapshot) }
            }
        }, sendCurrent = false)
    }

    /** Loads the current mode's tabs once, and again after a switch between normal and private mode. */
    fun loadState() = workScope.launch {
        if (loaded && loadedIncognito == config.privateBrowsing) return@launch
        loadedIncognito = config.privateBrowsing
        val dao = AppDatabase.db.tabDao()
        openTabs.replaceAll(if (loadedIncognito) incognitoSession.load(dao) else dao.getAll(false))
        loaded = true
    }

    /** Starts saving before the window goes away; the short write finishes even if the model is cleared. */
    fun saveTabBeforeExit(tab: BrowserTab) = workScope.launch(start = CoroutineStart.UNDISPATCHED) {
        withContext(NonCancellable) { saveTab(tab) }
    }

    /**
     * Saves [tab]: its engine state was captured on the main thread, the file and row are written
     * on IO. A later save of the same tab, or closing it, makes an earlier unfinished save give up.
     */
    suspend fun saveTab(tab: BrowserTab) {
        val revision = tab.persistenceRevision.incrementAndGet()
        val snapshot = tab.copy().apply { savedState = tab.savedState }
        withContext(Dispatchers.IO + NonCancellable) {
            storage.withLock {
                if (tab.closed || revision != tab.persistenceRevision.get()) return@withLock
                snapshot.id = tab.id
                snapshot.saveHistoryFile()
                tab.wvStateFileName = snapshot.wvStateFileName
                tab.id = AppDatabase.db.tabDao().save(snapshot)
            }
        }
    }

    fun onCloseTab(tab: BrowserTab) {
        tab.closed = true
        tab.engine.detach(destroyView = true)
        openTabs.remove(tab)
        workScope.launch(Dispatchers.IO) {
            storage.withLock {
                AppDatabase.db.tabDao().delete(tab)
                tab.removeFiles()
            }
        }
    }

    fun onCloseOthers() = workScope.launch {
        val closing = openTabs.toList()
        for (tab in closing) {
            tab.closed = true
            tab.engine.detach(destroyView = true)
        }
        currentTab.value = null
        openTabs.clear()
        val incognito = config.privateBrowsing
        withContext(Dispatchers.IO) {
            storage.withLock {
                AppDatabase.db.tabDao().deleteAll(incognito)
                closing.forEach(BrowserTab::removeFiles)
            }
        }
    }

    /** The window is going away: every WebView is destroyed, the tabs stay open. */
    fun onDetachActivity() {
        for (tab in openTabs) tab.engine.detach(destroyView = true)
    }

    /**
     * Shows [newTab] in [webViewParent]. The previous tab is paused, detached and saved; other
     * WebViews are released first, so a low-RAM device never holds two renderers for a switch.
     * A tab without a live WebView gets one from [webViewProvider] and its history back, or its
     * address loaded when there is none (and [loadInitialUrl]).
     */
    fun changeTab(
        newTab: BrowserTab,
        webViewProvider: (tab: BrowserTab) -> View?,
        webViewParent: ViewGroup,
        window: PageWindow,
        loadInitialUrl: Boolean = true,
    ) {
        val previous = currentTab.value
        if (previous === newTab && newTab.engine.view != null) return
        if (previous !== newTab) {
            openTabs.forEach { it.selected = false }
            previous?.let { old ->
                old.engine.detach(destroyView = false)
                old.onPause()
                workScope.launch { saveTab(old) }
            }
            newTab.selected = true
            currentTab.value = newTab
        }
        releaseBackgroundWebViews(newTab)
        var needsLoad = false
        if (newTab.engine.view == null) {
            webViewProvider(newTab) ?: return
            needsLoad = !newTab.restorePage()
        }
        newTab.engine.attach(window, webViewParent)
        if (needsLoad && loadInitialUrl) {
            newTab.engine.load(newTab.url)
            newTab.rendererLost = false
        }
        newTab.engine.setOnline(VireoLumaTVApp.instance.hasInternet())
    }

    /**
     * Destroys every WebView but [activeTab]'s, and its opener's while a popup shows, so closing the
     * popup resumes that page (a video player) instead of reloading it. Memory pressure still frees it.
     */
    private fun releaseBackgroundWebViews(activeTab: BrowserTab) {
        val opener = activeTab.opener?.takeIf { !it.closed && it in openTabs }
        val background = openTabs.filter { it !== activeTab && it !== opener && it.engine.view != null }
        background.forEach { it.engine.detach(destroyView = true) }
        if (background.isNotEmpty()) Log.i(TAG, "Released ${background.size} background WebView(s)")
    }

    /** Where site settings are read and written; tests substitute a double. */
    @VisibleForTesting internal var siteSettingsDao: () -> SiteSettingsDao = { AppDatabase.db.siteSettingsDao() }

    /**
     * The settings of [tab]'s site, cached on the tab: a row, or the fact that there is none, so
     * navigating within a site without settings asks the database once. Any write to site settings
     * makes earlier answers stale; with [createIfNotFound] a missing row is created, never assumed.
     */
    suspend fun siteSettingsFor(tab: BrowserTab, createIfNotFound: Boolean): SiteSettings? {
        val host = hostOf(tab.url) ?: return null
        val hosts = siteSettingsDao()
        val generation = hosts.generation()
        tab.siteSettings?.takeIf {
            it.host == host && it.generation == generation && (it.settings != null || !createIfNotFound)
        }?.let { return it.settings }
        var config = withContext(Dispatchers.IO) { hosts.findByHostName(host) }
        if (config == null && createIfNotFound) config = hosts.ensureHost(host)
        remember(tab, SiteSettingsLookup(host, config, generation))
        return config
    }

    /** The pop-up level for [tab]'s site from the cache only: WebView asks on the main thread. */
    fun popupBlockingLevel(tab: BrowserTab): Int =
        tab.siteSettings?.takeIf { it.host == hostOf(tab.url) }?.settings?.popupBlockLevel ?: SiteSettings.DEFAULT_POPUP_POLICY

    suspend fun setPopupPolicy(newLevel: Int, tab: BrowserTab) {
        val config = siteSettingsFor(tab, createIfNotFound = true) ?: return
        val generation = siteSettingsDao().storePopupLevel(config.hostName, newLevel)
        config.popupBlockLevel = newLevel
        remember(tab, SiteSettingsLookup(config.hostName, config, generation))
    }

    /**
     * Keeps [lookup] unless the tab has moved to another site meanwhile, or already holds a newer
     * answer for the same site: a slow read that began before a write must not replace its result.
     */
    private fun remember(tab: BrowserTab, lookup: SiteSettingsLookup) {
        if (hostOf(tab.url) != lookup.host) return
        val held = tab.siteSettings
        if (held != null && held.host == lookup.host && held.generation > lookup.generation) return
        tab.siteSettings = lookup
    }

    private fun hostOf(url: String): String? = runCatching { URL(url).host }.getOrNull()

    companion object {
        // A literal: R8 renames the class in release builds.
        const val TAG = "TabStore"
        private val incognitoSession = IncognitoSessionTabs()
    }
}
