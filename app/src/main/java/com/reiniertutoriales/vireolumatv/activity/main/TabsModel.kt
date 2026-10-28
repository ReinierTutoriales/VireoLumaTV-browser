package com.reiniertutoriales.vireolumatv.activity.main

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.HostConfig
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.hasInternet
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableList
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableValue
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
class TabsModel : ActiveModel() {
    val currentTab = ObservableValue<WebTabState?>(null)
    val tabsStates = ObservableList<WebTabState>()
    var loaded = false
        private set

    private val config = AppContext.config
    /** Orders saves and deletions; held only around the IO part. */
    @VisibleForTesting internal val storage = Mutex()
    private var loadedIncognito = config.incognitoMode

    init {
        // A tab's stored position is its place in the list; reordering stores the new places.
        tabsStates.subscribe({ tabs ->
            var moved = false
            tabs.forEachIndexed { index, tab ->
                if (tab.position != index) {
                    tab.position = index
                    moved = true
                }
            }
            if (moved) {
                val snapshot = tabs.toList()
                modelScope.launch { AppDatabase.db.tabsDao().updatePositions(snapshot) }
            }
        }, notifyOnSubscribe = false)
    }

    /** Loads the current mode's tabs once, and again after a switch between normal and private mode. */
    fun loadState() = modelScope.launch {
        if (loaded && loadedIncognito == config.incognitoMode) return@launch
        loadedIncognito = config.incognitoMode
        val dao = AppDatabase.db.tabsDao()
        tabsStates.replaceAll(if (loadedIncognito) incognitoSession.load(dao) else dao.getAll(false))
        loaded = true
    }

    /** Starts saving before the window goes away; the short write finishes even if the model is cleared. */
    fun saveTabBeforeExit(tab: WebTabState) = modelScope.launch(start = CoroutineStart.UNDISPATCHED) {
        withContext(NonCancellable) { saveTab(tab) }
    }

    /**
     * Saves [tab]: its engine state was captured on the main thread, the file and row are written
     * on IO. A later save of the same tab, or closing it, makes an earlier unfinished save give up.
     */
    suspend fun saveTab(tab: WebTabState) {
        val revision = tab.persistenceRevision.incrementAndGet()
        val snapshot = tab.copy().apply { savedState = tab.savedState }
        withContext(Dispatchers.IO + NonCancellable) {
            storage.withLock {
                if (tab.closed || revision != tab.persistenceRevision.get()) return@withLock
                snapshot.id = tab.id
                snapshot.saveWebViewStateToFile()
                tab.wvStateFileName = snapshot.wvStateFileName
                tab.id = AppDatabase.db.tabsDao().save(snapshot)
            }
        }
    }

    fun onCloseTab(tab: WebTabState) {
        tab.closed = true
        tab.engine.detach(destroyView = true)
        tabsStates.remove(tab)
        modelScope.launch(Dispatchers.IO) {
            storage.withLock {
                AppDatabase.db.tabsDao().delete(tab)
                tab.removeFiles()
            }
        }
    }

    fun onCloseAllTabs() = modelScope.launch {
        val closing = tabsStates.toList()
        for (tab in closing) {
            tab.closed = true
            tab.engine.detach(destroyView = true)
        }
        currentTab.value = null
        tabsStates.clear()
        val incognito = config.incognitoMode
        withContext(Dispatchers.IO) {
            storage.withLock {
                AppDatabase.db.tabsDao().deleteAll(incognito)
                closing.forEach(WebTabState::removeFiles)
            }
        }
    }

    /** The window is going away: every WebView is destroyed, the tabs stay open. */
    fun onDetachActivity() {
        for (tab in tabsStates) tab.engine.detach(destroyView = true)
    }

    /**
     * Shows [newTab] in [webViewParent]. The previous tab is paused, detached and saved; other
     * WebViews are released first, so a low-RAM device never holds two renderers for a switch.
     * A tab without a live WebView gets one from [webViewProvider] and its history back, or its
     * address loaded when there is none (and [loadInitialUrl]).
     */
    fun changeTab(
        newTab: WebTabState,
        webViewProvider: (tab: WebTabState) -> View?,
        webViewParent: ViewGroup,
        window: PageWindow,
        loadInitialUrl: Boolean = true,
    ) {
        val previous = currentTab.value
        if (previous === newTab && newTab.engine.view != null) return
        if (previous !== newTab) {
            tabsStates.forEach { it.selected = false }
            previous?.let { old ->
                old.engine.detach(destroyView = false)
                old.onPause()
                modelScope.launch { saveTab(old) }
            }
            newTab.selected = true
            currentTab.value = newTab
        }
        releaseBackgroundWebViews(newTab)
        var needsLoad = false
        if (newTab.engine.view == null) {
            webViewProvider(newTab) ?: return
            needsLoad = !newTab.restoreWebView()
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
    private fun releaseBackgroundWebViews(activeTab: WebTabState) {
        val opener = activeTab.opener?.takeIf { !it.closed && it in tabsStates }
        val background = tabsStates.filter { it !== activeTab && it !== opener && it.engine.view != null }
        background.forEach { it.engine.detach(destroyView = true) }
        if (background.isNotEmpty()) Log.i(TAG, "Released ${background.size} background WebView(s)")
    }

    /** The settings of [tab]'s site, cached on the tab; with [createIfNotFound] a row is made for it. */
    suspend fun findHostConfig(tab: WebTabState, createIfNotFound: Boolean): HostConfig? {
        val host = hostOf(tab.url) ?: return null
        tab.cachedHostConfig?.takeIf { it.hostName == host }?.let { return it }
        val hosts = AppDatabase.db.hostsDao()
        var config = withContext(Dispatchers.IO) { hosts.findByHostName(host) }
        if (config == null && createIfNotFound) {
            config = hosts.ensureHost(host)
        }
        // The tab may have moved to another site while the database answered.
        if (hostOf(tab.url) == host) tab.cachedHostConfig = config
        return config
    }

    /** The pop-up level for [tab]'s site from the cache only: WebView asks on the main thread. */
    fun popupBlockingLevel(tab: WebTabState): Int =
        tab.cachedHostConfig?.takeIf { it.hostName == hostOf(tab.url) }?.popupBlockLevel ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE

    suspend fun changePopupBlockingLevel(newLevel: Int, tab: WebTabState) {
        val config = findHostConfig(tab, createIfNotFound = true) ?: return
        config.popupBlockLevel = newLevel
        AppDatabase.db.hostsDao().storePopupLevel(config.hostName, newLevel)
    }

    private fun hostOf(url: String): String? = runCatching { URL(url).host }.getOrNull()

    companion object {
        // A literal: R8 renames the class in release builds.
        const val TAG = "TabsModel"
        private val incognitoSession = IncognitoSessionTabs()
    }
}
