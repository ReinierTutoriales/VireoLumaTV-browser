package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Build
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.BuildConfig
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.IncognitoWebViewData
import com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.deleteDirectory
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
class MainActivityViewModel : ActiveModel() {
    private val config = AppContext.provideConfig()
    private var loaded = false

    /** The latest visit; it is written only after [VISIT_SETTLE_MS], so redirects and quick hops leave no row. */
    private var lastVisit: HistoryItem? = null
    private var pendingVisit: Job? = null

    fun loadState() = modelScope.launch {
        if (loaded) return@launch
        loaded = true
        if (config.appVersionCodeMark != BuildConfig.VERSION_CODE) {
            // An update leaves its own downloaded installer behind; remove it once.
            config.appVersionCodeMark = BuildConfig.VERSION_CODE
            withContext(Dispatchers.IO) { ReleaseUpdater.clearDownloads(VireoLumaTVApp.instance) }
        }
        trimHistory()
        lastVisit = runCatching { AppDatabase.db.historyDao().last().firstOrNull() }.getOrNull()
    }

    /** Past [HISTORY_TRIM_THRESHOLD] visits, those older than [HISTORY_KEEP_DAYS] days are removed. */
    private suspend fun trimHistory() {
        val history = AppDatabase.db.historyDao()
        if (history.count() > HISTORY_TRIM_THRESHOLD) {
            history.deleteWhereTimeLessThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HISTORY_KEEP_DAYS))
        }
    }

    /** Records a page visit; the same address twice in a row, the home page and non-web pages are skipped. */
    fun logVisitedHistory(title: String?, url: String, faviconHash: String?) {
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
        pendingVisit = modelScope.launch {
            delay(VISIT_SETTLE_MS)
            val history = AppDatabase.db.historyDao()
            val insertedTitle = visit.title
            visit.id = history.insert(visit)
            visit.saved = true
            // A title that arrived while the row was being written is applied now.
            if (visit.title != insertedTitle) history.updateTitle(visit.id, visit.title)
        }
    }

    /** A title that arrives after the visit was logged replaces the stored one. */
    fun onTabTitleUpdated(tab: WebTabState) {
        if (config.incognitoMode) return
        val visit = lastVisit?.takeIf { it.url == tab.url } ?: return
        visit.title = tab.title
        if (visit.saved) modelScope.launch { AppDatabase.db.historyDao().updateTitle(visit.id, visit.title) }
    }

    /**
     * Gives private mode WebView data of its own: a data directory suffix from Android 9, and
     * before that a backup of the normal data that [clearIncognitoData] puts back.
     */
    fun prepareSwitchToIncognito() {
        val app = VireoLumaTVApp.instance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return IncognitoWebViewData.configure(app)
        val (data, cache) = legacyDirectories()
        val dataBackup = data.backup()
        if (dataBackup.exists()) return // Already switched.
        data.renameTo(dataBackup)
        cache.renameTo(cache.backup())
    }

    fun clearIncognitoData() = modelScope.launch(Dispatchers.IO) {
        val app = VireoLumaTVApp.instance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return@launch IncognitoWebViewData.clear(app)
        for (directory in legacyDirectories()) {
            deleteDirectory(directory)
            directory.backup().renameTo(directory)
        }
    }

    private fun legacyDirectories(): List<File> {
        val app = VireoLumaTVApp.instance
        return listOf(File(requireNotNull(app.filesDir.parentFile), WEB_VIEW_DATA_FOLDER), File(app.cacheDir, WEB_VIEW_CACHE_FOLDER))
    }

    private fun File.backup() = File(parentFile, name + BACKUP_SUFFIX)

    companion object {
        const val WEB_VIEW_DATA_FOLDER = "app_webview"
        const val WEB_VIEW_CACHE_FOLDER = "WebView"
        private const val BACKUP_SUFFIX = "_backup"
        private const val VISIT_SETTLE_MS = 5_000L
        private const val HISTORY_TRIM_THRESHOLD = 5000
        private const val HISTORY_KEEP_DAYS = 90L
    }
}
