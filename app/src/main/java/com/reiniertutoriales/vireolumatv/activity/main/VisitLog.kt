package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Build
import android.util.Log
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.BuildConfig
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.IncognitoWebViewData
import com.reiniertutoriales.vireolumatv.utils.restoreLegacyWebViewBackups
import com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Browser-wide work behind the main screen: start-up housekeeping, the history of visited pages
 * and the separate WebView data of private mode.
 */
class VisitLog : ScreenModel() {
    private val config = AppContext.config
    private var loaded = false

    /** The latest visit; it is written only after [VISIT_SETTLE_MS], so redirects and quick hops leave no row. */
    private var lastVisit: HistoryItem? = null
    private var pendingVisit: Job? = null

    fun loadState() = workScope.launch {
        if (loaded) return@launch
        loaded = true
        if (config.lastVersionCode != BuildConfig.VERSION_CODE) {
            // An update leaves its own downloaded installer behind; remove it once.
            config.lastVersionCode = BuildConfig.VERSION_CODE
            withContext(Dispatchers.IO) { ReleaseUpdater.clearDownloads(VireoLumaTVApp.instance) }
        }
        trimHistory()
        lastVisit = runCatching { AppDatabase.db.visitDao().last().firstOrNull() }.getOrNull()
    }

    /** Past [HISTORY_TRIM_THRESHOLD] visits, those older than [HISTORY_KEEP_DAYS] days are removed. */
    private suspend fun trimHistory() {
        val history = AppDatabase.db.visitDao()
        if (history.exceeds(HISTORY_TRIM_THRESHOLD)) {
            history.deleteWhereTimeLessThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HISTORY_KEEP_DAYS))
        }
    }

    /** Records a page visit; the same address twice in a row, the home page and non-web pages are skipped. */
    fun recordVisit(title: String?, url: String, faviconHash: String?) {
        if (url == lastVisit?.url || url == Config.HOME_PAGE_URL || !url.startsWith("http", ignoreCase = true)) return
        val now = System.currentTimeMillis()
        lastVisit?.let { if (!it.saved && now - it.time < VISIT_SETTLE_MS) pendingVisit?.cancel() }
        val visit = HistoryItem().apply {
            this.url = url
            this.title = title.orEmpty()
            time = now
            favicon = faviconHash
        }
        lastVisit = visit
        pendingVisit = workScope.launch {
            delay(VISIT_SETTLE_MS)
            val history = AppDatabase.db.visitDao()
            val insertedTitle = visit.title
            visit.id = history.insert(visit)
            visit.saved = true
            // A title that arrived while the row was being written is applied now.
            if (visit.title != insertedTitle) history.updateTitle(visit.id, visit.title)
        }
    }

    /** A title that arrives after the visit was logged replaces the stored one. */
    fun onTabTitle(tab: BrowserTab) {
        if (config.privateBrowsing) return
        val visit = lastVisit?.takeIf { it.url == tab.url } ?: return
        visit.title = tab.title
        if (visit.saved) workScope.launch { AppDatabase.db.visitDao().updateTitle(visit.id, visit.title) }
    }

    /** Gives private mode its own WebView data directory; private mode exists only from Android 9. */
    fun preparePrivateWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) IncognitoWebViewData.configure(VireoLumaTVApp.instance)
    }

    fun clearPrivateData() = workScope.launch(Dispatchers.IO) { IncognitoWebViewData.clear(VireoLumaTVApp.instance) }

    /**
     * Normal window start, before any WebView exists: data of a private session the system killed
     * before [clearPrivateData] is not kept on disk until the next private session.
     */
    fun discardStalePrivateData() {
        val app = VireoLumaTVApp.instance
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            restoreLegacyWebViewBackups(listOf(File(requireNotNull(app.filesDir.parentFile), WEBVIEW_DATA_DIR),
                File(app.cacheDir, WEBVIEW_CACHE_DIR)))
            return
        }
        workScope.launch(Dispatchers.IO) {
            try { IncognitoWebViewData.clear(app) } catch (error: IllegalStateException) { Log.w(TAG, "Private WebView data not cleared", error) }
        }
    }

    companion object {
        const val WEBVIEW_DATA_DIR = "app_webview"
        const val WEBVIEW_CACHE_DIR = "WebView"
        private const val TAG = "VisitLog"
        private const val VISIT_SETTLE_MS = 5_000L
        private const val HISTORY_TRIM_THRESHOLD = 5000
        private const val HISTORY_KEEP_DAYS = 90L
    }
}
