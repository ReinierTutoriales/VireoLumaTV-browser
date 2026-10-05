package com.reiniertutoriales.vireolumatv.activity.main

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.HostConfig
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.Utils
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableList
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableValue
import com.reiniertutoriales.vireolumatv.webengine.WebEngineWindowProviderCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URL

class TabsModel : ActiveModel() {
    companion object {
        //literal, not ::class.java.simpleName: R8 renames the class in release builds
        const val TAG = "TabsModel"

        private val incognitoSession = IncognitoSessionTabs()
    }

    private val saveMutex = Mutex()
    var loaded = false
    val currentTab = ObservableValue<WebTabState?>(null)
    val tabsStates = ObservableList<WebTabState>()
    private val config = AppContext.provideConfig()
    private var incognitoMode = config.incognitoMode

    init {
        tabsStates.subscribe({
            //auto-update positions on any list change
            var positionsChanged = false
            tabsStates.forEachIndexed { index, webTabState ->
                if (webTabState.position != index) {
                    webTabState.position = index
                    positionsChanged = true
                }
            }
            if (positionsChanged) {
                val tabsListClone = listOf(*tabsStates.toTypedArray())
                modelScope.launch(Dispatchers.Main) {
                    val tabsDao = AppDatabase.db.tabsDao()
                    tabsDao.updatePositions(tabsListClone)
                }
            }
        }, false)
    }

    fun loadState() = modelScope.launch(Dispatchers.Main) {
        if (loaded) {
            //check is incognito mode changed
            if (incognitoMode != config.incognitoMode) {
                incognitoMode = config.incognitoMode
                loaded = false
            } else {
                return@launch
            }
        }
        val tabsDao = AppDatabase.db.tabsDao()
        if (config.incognitoMode) {
            tabsStates.replaceAll(incognitoSession.load(tabsDao))
        } else {
            tabsStates.replaceAll(tabsDao.getAll(false))
        }
        loaded = true
    }

    // Start the state capture before returning to the Activity, finish the small atomic write even
    // if the last Activity destroys its model. No Activity or View is captured by this job.
    fun saveTabBeforeExit(tab: WebTabState) = modelScope.launch(start = CoroutineStart.UNDISPATCHED) {
        withContext(NonCancellable) { saveTab(tab) }
    }

    suspend fun saveTab(tab: WebTabState) {
        val snapshot = tab.copy().apply { savedState = tab.savedState }
        // Capture WebView state on Main, serialize and commit the captured state on IO.
        withContext(Dispatchers.IO + NonCancellable) {
            saveMutex.withLock {
                if (tab.closed) return@withLock
                snapshot.id = tab.id
                snapshot.saveWebViewStateToFile()
                tab.wvStateFileName = snapshot.wvStateFileName
                tab.id = AppDatabase.db.tabsDao().save(snapshot)
            }
        }
    }

    fun onCloseTab(tab: WebTabState) {
        tab.closed = true
        tab.webEngine.onDetachFromWindow(completely = true, destroyTab = true)
        tabsStates.remove(tab)
        modelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                AppDatabase.db.tabsDao().delete(tab)
                tab.removeFiles()
            }
        }
    }

    fun onCloseAllTabs() = modelScope.launch(Dispatchers.Main) {
        val tabsClone = ArrayList(tabsStates)
        tabsClone.forEach {
            it.closed = true
            it.webEngine.onDetachFromWindow(completely = true, destroyTab = true)
        }
        currentTab.value = null
        tabsStates.clear()
        val mode = config.incognitoMode
        withContext(Dispatchers.IO) {
            saveMutex.withLock {
                AppDatabase.db.tabsDao().deleteAll(mode)
                tabsClone.forEach { it.removeFiles() }
            }
        }
    }

    fun onDetachActivity() {
        for (tab in tabsStates) {
            tab.webEngine.onDetachFromWindow(completely = true, destroyTab = false)
        }
    }

    fun changeTab(
        newTab: WebTabState,
        webViewProvider: (tab: WebTabState) -> View?,
        webViewParent: ViewGroup,
        webEngineWindowProviderCallback: WebEngineWindowProviderCallback,
        loadInitialUrl: Boolean = true
    ) {
        if (currentTab.value == newTab && newTab.webEngine.getView() != null) return
        if (currentTab.value != newTab) {
            tabsStates.forEach {
                it.selected = false
            }
            currentTab.value?.apply {
                webEngine.onDetachFromWindow(completely = false, destroyTab = false)
                onPause()
                modelScope.launch { saveTab(this@apply) }
            }

            newTab.selected = true
            currentTab.value = newTab
        }
        // Release the old renderer before allocating its replacement, including on low-RAM devices.
        releaseBackgroundWebViews(newTab)
        var wv = newTab.webEngine.getView()
        var needReloadUrl = false
        if (wv == null) {
            wv = webViewProvider(newTab)
            if (wv == null) {
                return
            }
            needReloadUrl = !newTab.restoreWebView()
        }
        newTab.webEngine.onAttachToWindow(webEngineWindowProviderCallback, webViewParent)
        if (needReloadUrl && loadInitialUrl) {
            newTab.webEngine.loadUrl(newTab.url)
            newTab.rendererLost = false
        }
        newTab.webEngine.setNetworkAvailable(Utils.isNetworkConnected(VireoLumaTVApp.instance))
    }

    // Only the visible tab owns a WebView. Captured history survives destruction and is restored
    // when the user returns; background pages (including audio/video) stop running.
    private fun releaseBackgroundWebViews(activeTab: WebTabState) {
        var released = 0
        for (tab in tabsStates) {
            if (tab === activeTab || tab.webEngine.getView() == null) continue
            tab.webEngine.onDetachFromWindow(completely = true, destroyTab = false)
            released++
        }
        if (released > 0) {
            Log.i(TAG, "released $released background WebView(s), kept only current tab")
        }
    }

    suspend fun findHostConfig(tab: WebTabState, createIfNotFound: Boolean): HostConfig? {
        Log.d(WebTabState.TAG, "findOrCreateHostConfig")
        val currentHostName = try {
            URL(tab.url).host
        } catch (e: Exception) {
            Log.w(WebTabState.TAG, "Can not parse current url host: $e")
            return null
        }
        var hostConfig = tab.cachedHostConfig
        if (hostConfig == null || hostConfig.hostName != currentHostName) {
            val db = com.reiniertutoriales.vireolumatv.singleton.AppDatabase.db.hostsDao()
            hostConfig = withContext(Dispatchers.IO) { db.findByHostName(currentHostName) }
            if (hostConfig == null && createIfNotFound) {
                hostConfig = HostConfig(currentHostName)
                hostConfig.id = db.insert(hostConfig)
            }
            if (runCatching { URL(tab.url).host }.getOrNull() == currentHostName) {
                tab.cachedHostConfig = hostConfig
            }
        }
        return hostConfig
    }

    /** Synchronous WebView policy callbacks must never wait for disk on the UI thread. */
    fun popupBlockingLevel(tab: WebTabState): Int {
        val host = runCatching { URL(tab.url).host }.getOrNull()
        return tab.cachedHostConfig?.takeIf { it.hostName == host }?.popupBlockLevel
            ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE
    }

    suspend fun changePopupBlockingLevel(newLevel: Int, tab: WebTabState) {
        val hostConfig = findHostConfig(tab,true) ?: return
        hostConfig.popupBlockLevel = newLevel
        AppDatabase.db.hostsDao().update(hostConfig)
    }
}