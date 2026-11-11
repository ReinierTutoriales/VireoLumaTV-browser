package com.reiniertutoriales.vireolumatv.activity.main

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.activity.ComponentActivity
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.PrivateBrowserActivity
import com.reiniertutoriales.vireolumatv.activity.downloads.DownloadsActivity
import com.reiniertutoriales.vireolumatv.activity.downloads.IncognitoDownloadsActivity
import com.reiniertutoriales.vireolumatv.activity.downloads.TransferBoard
import com.reiniertutoriales.vireolumatv.activity.history.HistoryActivity
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites.FavoritesPanel
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites.TabFavorites
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.SettingsScreen
import com.reiniertutoriales.vireolumatv.activity.main.home.NativeHome
import com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand
import com.reiniertutoriales.vireolumatv.databinding.BrowserWindowBinding
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.singleton.Favicons
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.Shortcuts
import com.reiniertutoriales.vireolumatv.utils.AppLanguages
import com.reiniertutoriales.vireolumatv.utils.BackPresses
import com.reiniertutoriales.vireolumatv.utils.RemoteInput
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.utils.CookiePersistence
import com.reiniertutoriales.vireolumatv.utils.NetworkWatcher
import com.reiniertutoriales.vireolumatv.utils.onAnimationEnd
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.toast
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import com.reiniertutoriales.vireolumatv.webengine.Engines
import com.reiniertutoriales.vireolumatv.webengine.webview.CookieJar
import com.reiniertutoriales.vireolumatv.webengine.webview.UserActivation
import com.reiniertutoriales.vireolumatv.webengine.webview.LegacyLocationGrants
import com.reiniertutoriales.vireolumatv.webengine.webview.ServiceWorkerFilter
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewFreshness
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewPageEngine
import com.reiniertutoriales.vireolumatv.widgets.ToastBar
import com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater
import com.reiniertutoriales.vireolumatv.utils.UpdateSchedule
import com.reiniertutoriales.vireolumatv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.system.exitProcess

/**
 * The browser window. The page fills the screen; Back or the menu key brings the capsule
 * (address, commands, tabs) over it, and focusing another tab previews it until it is chosen.
 * Each tab's page talks to the window through a [TabWindow]; downloads go through
 * [BrowserDownloads] and other apps through [ExternalApps]. Private mode runs as
 * [PrivateBrowserActivity] in its own process.
 */
open class MainActivity : ComponentActivity() {
    internal lateinit var vb: BrowserWindowBinding
    internal lateinit var tabStore: TabStore
    internal lateinit var browserSettings: BrowserSettings
    internal lateinit var adBlocker: AdBlocker
    internal lateinit var mainThread: Handler
    internal lateinit var visitLog: VisitLog
    internal val config = AppContext.config
    internal val downloads by lazy { BrowserDownloads(this, { config.privateBrowsing }, ::onDownloadStarted) }
    internal val external by lazy { ExternalApps(this) }
    internal var isFullscreen = false

    /** The engine that launched the outstanding picker; tab selection cannot transfer ownership. */
    internal var filePickerOwner: PageEngine? = null
    internal val filePickerResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onFilePicked(result.resultCode, result.data)
    }

    internal fun onFilePicked(resultCode: Int, data: Intent?) {
        val owner = filePickerOwner ?: return
        filePickerOwner = null
        owner.onFilePicked(resultCode, data)
    }
    private val historyPick = registerForActivityResult(HistoryActivity.PickAddress) { address ->
        if (address == null) return@registerForActivityResult
        navigate(address)
        closeCapsule()
    }
    private var nextPermissionRequest = PAGE_PERMISSION_REQUESTS_START
    private var lastBlockedNotice = 0L
    private var suggestionsLookup: Job? = null
    internal var nativeHome: NativeHome? = null

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguages.localized(newBase))

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Older WebView providers cannot isolate persistent profiles. Recover a previously
        // saved private-mode preference before routing activities, including after an upgrade.
        if (android.os.Build.VERSION.SDK_INT < 28 && config.privateBrowsing) {
            config.privateBrowsing = false
        }
        val incognito = config.privateBrowsing
        // Private mode has its own activity class and process; the wrong one restarts as the right one.
        if (incognito xor (this is PrivateBrowserActivity)) {
            openPrivateWindow(incognito, intent)
            finish()
            return
        }
        visitLog = ScreenModels.get(VisitLog::class, this)
        if (incognito) visitLog.preparePrivateWindow() else visitLog.discardStalePrivateData()
        browserSettings = ScreenModels.get(BrowserSettings::class, this)
        adBlocker = ScreenModels.get(AdBlocker::class, this)
        val filters = adBlocker
        WebViewPageEngine.pageFiltersProvider = { url -> filters.pageFilters(url) }
        WebViewPageEngine.hiddenSelectorsProvider = { url, classes, ids -> filters.hiddenSelectors(url, classes, ids) }
        tabStore = ScreenModels.get(TabStore::class, this)
        ServiceWorkerFilter.policy = serviceWorkerPolicy
        mainThread = Handler(Looper.getMainLooper())
        vb = BrowserWindowBinding.inflate(layoutInflater)
        setContentView(vb.root)
        vb.tabPreview.visibility = View.INVISIBLE
        vb.controlsOverlay.visibility = View.INVISIBLE
        vb.loadProgress.visibility = View.GONE
        vb.controlsHost.onTabFocus = { index -> onTabCommand(com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand.Focus(index)) }
        vb.controlsHost.onTabActivate = { index -> onTabCommand(com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand.Show(index)) }
        vb.controlsHost.onTabClose = { tab -> onTabCommand(com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand.Close(tab)) }
        vb.controlsHost.onTabOptions = { tab -> showTabOptions(tab) }
        vb.controlsHost.onAddTab = { onTabCommand(com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand.Add) }
        nativeHome = NativeHome(this, vb.nativeHomeContainer)
        configureBrowserControls()
        observeModels()
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        loadState()
    }

    private fun observeModels() {
        ScreenModels.get(TransferBoard::class, this).transfers.observe(this) { vb.controlsHost.showDownloads(it.size) }
        browserSettings.keepScreenOn.observe(lifecycle) { on ->
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        tabStore.currentTab.observe(this) { tab ->
            vb.controlsHost.showAddress(tab)
            publishShownSite()
            if (tab == null) nativeHome?.hide() else onPageViewChanged(tab)
        }
        adBlocker.listLoading.observe(lifecycle) { tabStore.currentTab.value?.let(::updateAdblockStatus) }
        adBlocker.updateResult.observe(lifecycle) { tabStore.currentTab.value?.let(::updateAdblockStatus) }
        tabStore.openTabs.observe(this, false) { tabs ->
            if (tabs.isEmpty()) vb.pageContainer.removeAllViews()
            val selected = tabs.indexOf(tabStore.currentTab.value).coerceAtLeast(0)
            vb.controlsHost.setTabs(tabs.toList(), selected)
        }
    }

    /** Reads the stored state, then shows the tab the intent asks for, or the selected one, or a new home tab. */
    private fun loadState() = lifecycleScope.launch {
        vb.startupSpinner.visibility = View.VISIBLE
        vb.startupSpinner.requestFocus()
        visitLog.loadState().join()
        tabStore.loadState().join()
        if (config.adBlockEnabled) adBlocker.initialRulesReady.await()
        if (!isActive) return@launch
        vb.startupSpinner.visibility = View.GONE
        when {
            ExternalWebNavigation.isAllowed(intent.data) -> handleIntent(intent)
            tabStore.openTabs.isEmpty() -> openInNewTab(browserSettings.homePage, 0, closeCapsuleAfter = true, navigateImmediately = true)
            // No selected tab only happens after an error; the first one is shown then.
            else -> changeTab(tabStore.openTabs.firstOrNull { it.selected } ?: tabStore.openTabs[0])
        }
        val shown = tabStore.currentTab.value
        if (shown == null || shown.url == browserSettings.homePage) openCapsule()
        startupDone = true
        checkForUpdateIfDue()
    }

    @VisibleForTesting internal var updateCheck: Job? = null
    @VisibleForTesting internal var updateTimer: Job? = null
        private set
    @VisibleForTesting internal var updateForeground = false
    @VisibleForTesting internal var pendingUpdate: ReleaseUpdater.Release? = null
        private set
    private var updateOffer: TvDialog? = null
    @VisibleForTesting internal var startupDone = false

    /**
     * The generic build looks for a newer signed release by itself once a day, never in private
     * mode. It runs after start-up, on every return to the screen and when the network comes back;
     * the next due check runs while the screen stays active. Failures retry after an hour.
     * A release found during fullscreen/background waits in memory until it can be offered;
     * the timestamp is committed only after that offer, so recreation cannot lose it for a day.
     * Updating still asks first, and installing goes through the system installer.
     */
    @VisibleForTesting
    internal fun checkForUpdateIfDue(fetch: suspend () -> ReleaseUpdater.Release? = ReleaseUpdater::fetchNewerRelease) {
        if (!BuildConfig.BUILT_IN_AUTO_UPDATE || config.privateBrowsing || !startupDone) return
        if (pendingUpdate != null) {
            showPendingUpdate()
            return
        }
        if (updateCheck?.isActive == true || updateOffer?.isShowing == true) return
        val wait = UpdateSchedule.delayUntilDue(config.prefs, System.currentTimeMillis())
        if (wait > 0L) {
            scheduleUpdateCheck(wait, fetch)
            return
        }
        updateTimer?.cancel()
        updateTimer = null
        updateCheck = lifecycleScope.launch {
            val release = try {
                withContext(Dispatchers.IO) { fetch() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Automatic update check failed", error)
                scheduleUpdateCheck(UpdateSchedule.RETRY_MS, fetch)
                return@launch
            }
            if (release == null) {
                UpdateSchedule.checked(config.prefs, System.currentTimeMillis())
                scheduleUpdateCheck(UpdateSchedule.INTERVAL_MS, fetch)
            } else {
                pendingUpdate = release
                showPendingUpdate()
            }
        }
    }

    /** One cancellable timer, scoped to the foreground activity; never a background service. */
    @VisibleForTesting
    internal fun scheduleUpdateCheck(waitMs: Long, fetch: suspend () -> ReleaseUpdater.Release? = ReleaseUpdater::fetchNewerRelease) {
        updateTimer?.cancel()
        updateTimer = null
        if (!updateForeground || !BuildConfig.BUILT_IN_AUTO_UPDATE || config.privateBrowsing || !startupDone) return
        updateTimer = lifecycleScope.launch {
            delay(waitMs.coerceAtLeast(1L))
            updateTimer = null
            checkForUpdateIfDue(fetch)
        }
    }

    internal fun stopUpdateTimer() {
        updateForeground = false
        updateTimer?.cancel()
        updateTimer = null
    }

    @VisibleForTesting
    internal fun showPendingUpdate(show: (ReleaseUpdater.Release) -> Unit = ::offerUpdate): Boolean {
        val release = pendingUpdate ?: return false
        if (!updateForeground || !startupDone || config.privateBrowsing || isFullscreen ||
            isFinishing || isDestroyed || updateOffer?.isShowing == true) return false
        show(release)
        pendingUpdate = null
        UpdateSchedule.checked(config.prefs, System.currentTimeMillis())
        scheduleUpdateCheck(UpdateSchedule.INTERVAL_MS)
        return true
    }

    internal fun onUpdateOfferDismissed() {
        updateOffer = null
        if (overlayVisible) vb.controlsHost.requestCommandFocus()
        // Pause or a due timer may have removed the timer while this dialog was open.
        if (updateForeground) checkForUpdateIfDue()
    }

    private fun offerUpdate(release: ReleaseUpdater.Release) {
        updateOffer = TvDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(getString(R.string.update_available_message, release.version))
            .setPositiveButton(R.string.action_update) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val apk = withContext(Dispatchers.IO) { ReleaseUpdater.download(this@MainActivity, release) }
                        ReleaseUpdater.install(this@MainActivity, apk)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "Update download failed", error)
                        toast(R.string.update_failed)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { onUpdateOfferDismissed() }
            .show()
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::tabStore.isInitialized && tabStore.loaded && intent.data != null &&
            (!config.adBlockEnabled || adBlocker.initialRulesReady.isCompleted)) handleIntent(intent)
    }

    /**
     * An address from another app always opens in a new tab: no extra lets a sender replace the page
     * the user is looking at, which would show the sender's content under the trusted tab.
     */
    internal fun handleIntent(intent: Intent) {
        val uri = intent.data
        if (!ExternalWebNavigation.isAllowed(uri)) {
            Log.w(TAG, "Rejected external navigation with scheme ${uri?.scheme}")
            return
        }
        openInNewTab(uri.toString(), tabStore.openTabs.size, closeCapsuleAfter = true, navigateImmediately = true)
    }

    override fun onStart() {
        super.onStart()
        downloads.bind()
    }

    override fun onStop() {
        super.onStop()
        downloads.unbind()
        // Home, another app or standby: keep the signed-in sessions in case the power goes next.
        cookies.onLeftScreen(config.privateBrowsing)
    }

    override fun onResume() {
        super.onResume()
        updateForeground = true
        if (!networkCallbackRegistered) {
            try {
                // The callback reports the current network at once; it then counts as restored.
                networkCallback.reset()
                getSystemService<ConnectivityManager>()?.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            } catch (error: RuntimeException) {
                // Some TV firmwares throw here; the state is still refreshed on every tab attach.
                Log.w(TAG, "No network callback", error)
            }
        }
        tabStore.currentTab.value?.engine?.resume()
        // A TV app stays alive for days: the start-up check alone would never refresh the filter lists.
        adBlocker.updateIfDue()
        checkForUpdateIfDue()
    }

    override fun onPause() {
        stopUpdateTimer()
        if (networkCallbackRegistered) {
            networkCallbackRegistered = false
            try {
                getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(networkCallback)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Network callback was not registered", error)
            }
        }
        tabStore.currentTab.value?.let { tab ->
            tab.engine.pause()
            tab.onPause()
            tabStore.saveTabBeforeExit(tab)
        }
        super.onPause()
    }

    /** The shown tab's blocker choice for service workers; replaced whole on the main thread. */
    @Volatile private var shownSite = ServiceWorkerFilter.ShownSite.NONE

    /** Call on the main thread whenever the shown tab, its address or its blocker choice changes. */
    internal fun publishShownSite() {
        val tab = tabStore.currentTab.value
        shownSite = ServiceWorkerFilter.ShownSite(tab?.url?.toUri()?.host, tab?.adblock)
    }

    /** Service workers belong to no tab: the shown tab's choice applies to its own site, else the setting. */
    @VisibleForTesting
    internal val serviceWorkerPolicy = object : ServiceWorkerFilter.Policy {
        override fun blocksAds(site: Uri): Boolean = shownSite.blocks(site, config.adBlockEnabled)
        override fun verdict(url: Uri, type: String?, site: Uri, method: String) = adBlocker.verdict(url, type, site, method)
    }

    override fun onDestroy() {
        filePickerOwner = null
        nativeHome?.hide()
        nativeHome = null
        if (ServiceWorkerFilter.policy === serviceWorkerPolicy) ServiceWorkerFilter.policy = null
        overlayVisible = false
        previewGeneration++
        // Uninitialized when onCreate() restarted the window in the other mode.
        if (::vb.isInitialized) {
            vb.pageContainer.onPageTouch = null
            vb.pageContainer.cursorSuppressed = false
        }
        if (::mainThread.isInitialized) mainThread.removeCallbacks(showFocusedPreview)
        focusedPreviewTab = null
        if (::tabStore.isInitialized) tabStore.onDetachActivity()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        if (::tabStore.isInitialized) {
            tabStore.openTabs.filterNot { it.selected }.forEach {
                it.thumbnail = null
                it.trimMemory()
            }
        }
        Favicons.trimMemory()
        if (::adBlocker.isInitialized) adBlocker.trimMemory()
        super.onTrimMemory(level)
    }

    @Deprecated("Page permission requests use the numeric codes WebView hands out")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (tabStore.currentTab.value?.engine?.onPermissionsResult(requestCode, permissions, grantResults) == true) return
        if (downloads.onPermissionsResult(requestCode, grantResults)) return
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /** Asks Android for permissions a page needs; the code returned identifies the answer. */
    internal fun requestPagePermissions(permissions: Array<String>): Int =
        nextPermissionRequest++.also { requestPermissions(permissions, it) }

    // Network

    private var networkCallbackRegistered = false
    @VisibleForTesting internal var cookies = CookiePersistence()
    // Replaces the CONNECTIVITY_ACTION broadcast; callbacks arrive on a binder thread.
    private val networkCallback = NetworkWatcher { online, restored ->
        mainThread.post {
            if (isDestroyed) return@post
            val engine = tabStore.currentTab.value?.engine
            engine?.setOnline(online)
            if (!restored) return@post
            // A page that failed for lack of network loads again by itself.
            engine?.onNetworkRestored()
            // A TV often starts before Wi-Fi: fetch the filter lists as soon as it connects.
            adBlocker.updateIfDue(networkRestored = true)
            checkForUpdateIfDue()
        }
    }

    // Capsule commands

    @VisibleForTesting
    internal fun configureBrowserControls() {
        vb.controlsHost.onSubmitAddress = { target ->
            onUrlInputDone()
            search(target)
        }
        vb.controlsHost.onCancelAddress = { onAddressInputCancelled() }
        vb.controlsHost.onAddressDraftChanged = { onAddressTyped(it) }
        vb.controlsHost.onFocusSuggestions = { focusSuggestions() }
        vb.vSuggestions.onPick = { vb.controlsHost.submit(it.url) }
        vb.vSuggestions.onExitUp = { vb.controlsHost.focusAddressEditor() }
        val actions = com.reiniertutoriales.vireolumatv.activity.main.compose.BrowserCommandActions(
            back = { closeCapsule(); navigateBack() },
            forward = { closeCapsule(); tabStore.currentTab.value?.engine?.let { if (it.canGoForward) it.forward() } },
            reload = { closeCapsule(); refresh() },
            home = { closeCapsule(); navigate(browserSettings.homePage) },
            shield = { commitStripSelection(); toggleBlockerForTab() },
            shieldDetails = { commitStripSelection(); showBlockerDetails() },
            menu = { showAppMenu() },
        )
        vb.controlsHost.bindActions(actions)
    }

    fun showAppMenu() = AppMenuPanel(this, appMenuEntries(), ::runAppMenuEntry).show()

    private fun runAppMenuEntry(entry: AppMenuEntry) = when (entry) {
        AppMenuEntry.HISTORY -> showHistory()
        AppMenuEntry.FAVORITES -> showFavorites()
        AppMenuEntry.DOWNLOADS -> showDownloads()
        AppMenuEntry.INCOGNITO -> togglePrivateMode()
        AppMenuEntry.SETTINGS -> showSettings()
        AppMenuEntry.CLOSE_TAB -> closeTab(tabStore.currentTab.value)
        AppMenuEntry.CLOSE_APPLICATION -> closeWindow()
    }

    fun showDownloads() = startActivity(Intent(this,
        if (config.privateBrowsing) IncognitoDownloadsActivity::class.java else DownloadsActivity::class.java))

    fun showHistory() {
        historyPick.launch(Unit)
        closeCapsule()
    }

    /**
     * Saves [tab] itself, with its title and address read now, wherever it has moved meanwhile. Only
     * this explicit choice saves it; private mode keeps favorites, as its explanation tells the user.
     */
    internal fun addTabToFavorites(tab: BrowserTab) {
        if (tab.closed || tabStore.openTabs.indexOf(tab) < 0) return
        val title = tab.title
        val url = tab.url
        lifecycleScope.launch {
            val result = try {
                TabFavorites.add(AppDatabase.db.favoriteDao(), title, url)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w(TAG, "Saving the tab as a favorite failed", error)
                return@launch toast(R.string.favorite_save_failed)
            }
            toast(when (result) {
                TabFavorites.Result.ADDED -> R.string.favorite_added
                TabFavorites.Result.ALREADY_SAVED -> R.string.favorite_exists
                TabFavorites.Result.NOT_A_WEB_PAGE -> R.string.favorite_not_web
            })
        }
    }

    fun showFavorites() {
        val shown = tabStore.currentTab.value
        // "Add this page" offers only a web page; on the start page it starts with empty fields.
        val page = TabFavorites.webAddress(shown?.url)
        FavoritesPanel(this, lifecycleScope, shown?.title.takeIf { page != null }, page) { item -> item.url?.let(::navigate) }.show()
        closeCapsule()
    }

    fun showSettings() = SettingsScreen(this, browserSettings).show()

    /** Leaves private mode first (erasing its data) when it is on. */
    fun closeWindow() {
        lifecycleScope.launch {
            if (config.privateBrowsing) togglePrivateMode(andSwitchProcess = false).join()
            finish()
        }
    }

    // Address field

    fun search(text: String) = navigate(AddressInput.resolve(text, config.searchAddress.value))

    fun onUrlInputDone() {
        closeSuggestions()
        closeCapsule()
    }

    fun onAddressTyped(text: String) {
        suggestionsLookup?.cancel()
        suggestionsLookup = lifecycleScope.launch {
            delay(SUGGESTIONS_DELAY_MS) // One lookup when typing pauses, not one per key.
            showSuggestions(runCatching { AddressSuggestions.load(text, config.privateBrowsing) }
                .onFailure { Log.w(TAG, "Address suggestions failed", it) }
                .getOrDefault(emptyList()))
        }
    }

    fun focusSuggestions(): Boolean = vb.vSuggestions.hasSuggestions &&
        if (vb.pageContainer.pointerEnabled) vb.vSuggestions.focusPointer() else vb.vSuggestions.focusFirst()

    fun suggestionsHaveFocus(): Boolean = vb.vSuggestions.hasFocus()

    fun onAddressInputCancelled() = closeSuggestions()

    /** Suggestions take the tab strip's place while there are any, so the capsule does not grow. */
    private fun showSuggestions(suggestions: List<AddressSuggestion>) {
        vb.vSuggestions.show(suggestions)
        vb.controlsHost.showTabStrip(suggestions.isEmpty())
    }

    private fun closeSuggestions() {
        suggestionsLookup?.cancel()
        showSuggestions(emptyList())
    }

    // Navigation

    fun navigate(url: String) {
        val tab = tabStore.currentTab.value
        if (tab == null) {
            openInNewTab(url, 0, closeCapsuleAfter = true, navigateImmediately = true)
            return
        }
        tab.url = url
        publishShownSite()
        tab.engine.load(url)
    }

    /** The page's history first; without it the capsule opens or closes, or [goHomeIfNoHistory] goes home. */
    fun navigateBack(goHomeIfNoHistory: Boolean = false) {
        val engine = tabStore.currentTab.value?.engine
        when {
            engine?.canGoBack == true -> engine.back()
            goHomeIfNoHistory -> navigate(browserSettings.homePage)
            vb.controlsOverlay.isVisible -> closeCapsule()
            else -> openCapsule()
        }
    }

    fun refresh() {
        tabStore.currentTab.value?.engine?.reload()
    }

    // Tabs


    private fun showTabOptions(tab: com.reiniertutoriales.vireolumatv.model.BrowserTab) {
        val actions = arrayOf(
            getString(R.string.tab_action_new),
            getString(R.string.tab_action_close),
            getString(R.string.tab_action_favorite),
        )
        TvDialog.Builder(this)
            .setTitle(R.string.tabs_title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> onTabCommand(com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand.AddAt(tabStore.openTabs.indexOf(tab) + 1))
                    1 -> onTabCommand(com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand.Close(tab))
                    2 -> onTabCommand(com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand.Favorite(tab))
                }
            }.show()
    }

    private fun onTabCommand(command: TabCommand) = when (command) {
        is TabCommand.Focus -> tabAt(command.index)?.let { tab ->
            vb.controlsHost.current = command.index
            previewFocusedTab(tab)
        }
        is TabCommand.Show -> tabAt(command.index)?.let {
            vb.controlsHost.current = command.index
            commitStripSelection()
            closeCapsule()
        }
        TabCommand.Add -> openInNewTab(browserSettings.homePage, tabStore.openTabs.size, navigateImmediately = true)
        is TabCommand.AddAt ->
            openInNewTab(browserSettings.homePage, command.index, closeCapsuleAfter = false, navigateImmediately = true)
        is TabCommand.Favorite -> addTabToFavorites(command.tab)
        is TabCommand.Close -> closeTab(command.tab)
    }

    /** Focus moved to another tab: show its address and, a moment later, its preview. */
    private fun previewFocusedTab(tab: BrowserTab?) {
        vb.controlsHost.showAddress(tab)
        previewGeneration++
        mainThread.removeCallbacks(showFocusedPreview)
        focusedPreviewTab = tab
        mainThread.postDelayed(showFocusedPreview, PREVIEW_FOCUS_DELAY_MS)
    }

    private fun tabAt(index: Int) = tabStore.openTabs.getOrNull(index)

    /** The tab the strip shows as selected, which differs from the shown one while another is previewed. */
    internal fun tabAtStripSelection() = tabAt(vb.controlsHost.current)

    internal fun openInNewTab(url: String?, index: Int = 0, closeCapsuleAfter: Boolean = true, navigateImmediately: Boolean): PageEngine? {
        if (url == null) return null
        val tab = BrowserTab(url = url, incognito = config.privateBrowsing)
        tabStore.openTabs.add(index, tab)
        changeTab(tab, loadInitialUrl = false)
        if (tab.engine.view == null) return null
        if (navigateImmediately) navigate(url)
        if (closeCapsuleAfter && vb.controlsOverlay.isVisible) closeCapsule()
        return tab.engine
    }

    /** Closes [tab] and shows a neighbour; the last tab is replaced by a new home tab. */
    internal fun closeTab(tab: BrowserTab?) {
        if (tab == null) return
        val tabs = tabStore.openTabs
        val position = tabs.indexOf(tab)
        if (position < 0) return
        val keepCapsule = overlayVisible
        val wasCurrent = tabStore.currentTab.value === tab
        if (wasCurrent) tabStore.currentTab.value = null
        val neighbour = tabs.getOrNull(position - 1)?.takeIf { position > 0 } ?: tabs.getOrNull(position + 1)
        if (wasCurrent || tabStore.currentTab.value == null) {
            if (neighbour != null) changeTab(neighbour)
            else openInNewTab(browserSettings.homePage, 0, closeCapsuleAfter = !keepCapsule, navigateImmediately = true)
        }
        tabStore.onCloseTab(tab)
        val current = tabStore.currentTab.value
        vb.controlsHost.current = tabs.indexOf(current).coerceAtLeast(0)
        if (keepCapsule) {
            // Showing the neighbour focused its page behind the capsule: focus goes back to the strip,
            // on the tab now selected, instead of wherever the removed one leaves it.
            vb.controlsHost.focusTab(vb.controlsHost.current)
            previewGeneration++
            mainThread.removeCallbacks(showFocusedPreview)
            focusedPreviewTab = null
            vb.controlsHost.showAddress(current)
            lifecycleScope.launch { showPreview(current) }
            vb.controlsHost.post { if (overlayVisible) vb.controlsHost.focusTab(vb.controlsHost.current) }
        } else closeCapsule()
    }

    internal fun changeTab(newTab: BrowserTab, loadInitialUrl: Boolean = true) {
        tabStore.changeTab(newTab, ::createWebView, vb.pageContainer, TabWindow(this, newTab), loadInitialUrl)
        nativeHome?.update(tabStore.currentTab.value)
    }

    /** The tab's WebView, or null after telling the user that this device has no usable WebView. */
    internal fun createWebView(tab: BrowserTab): View? {
        val view = try {
            tab.engine.createView(this)
        } catch (error: Throwable) {
            Log.w(TAG, "WebView could not be created", error)
            showNoWebViewDialog()
            return null
        }
        // The provider is ready once a WebView exists; registering earlier could fail before the dialog above.
        ServiceWorkerFilter.install(this)
        LegacyLocationGrants.clearOnce(config.prefs, webViewProfile()) { GeolocationPermissions.getInstance().clearAll() }
        warnIfWebViewIsStale()
        return view
    }

    /** The WebView profile of this process: private mode runs in its own process and data directory. */
    private fun webViewProfile(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else packageName

    private fun showNoWebViewDialog() {
        val dialog = TvDialog.Builder(this)
            .setTitle(R.string.webview_missing_title)
            .setCancelable(false)
            .setMessage(R.string.error_no_webview)
            .setNegativeButton(R.string.action_exit) { _, _ -> finish() }
        storeIntent(WEBVIEW_PACKAGE)?.let { store ->
            dialog.setPositiveButton(R.string.action_open_store) { _, _ ->
                openStore(store)
                finish()
            }
        }
        dialog.show()
    }

    /** Once per provider version, when the installed WebView has not been updated for a long time. */
    private fun warnIfWebViewIsStale() {
        val provider = runCatching { WebViewCompat.getCurrentWebViewPackage(this) }.getOrNull() ?: return
        val notice = WebViewFreshness.pendingNotice(config.prefs, provider, System.currentTimeMillis()) ?: return
        WebViewFreshness.noticeShown(config.prefs, notice)
        val dialog = TvDialog.Builder(this)
            .setTitle(R.string.warning_title)
            .setMessage(R.string.webview_outdated)
            .setNegativeButton(android.R.string.ok, null)
            .setOnDismissListener {
                if (overlayVisible) vb.controlsHost.requestCommandFocus()
            }
        storeIntent(provider.packageName)?.let { store ->
            dialog.setPositiveButton(R.string.action_open_store) { _, _ -> openStore(store) }
        }
        dialog.show()
    }

    private fun storeIntent(packageName: String): Intent? =
        Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
            .takeIf { packageManager.queryIntentActivities(it, 0).isNotEmpty() }

    private fun openStore(store: Intent) {
        runCatching { startActivity(store) }.onFailure { Log.w(TAG, "Opening the app store failed", it) }
    }

    /** The capsule follows the shown tab: Back and Forward availability and the blocker shield. */
    internal fun onPageViewChanged(tab: BrowserTab) {
        if (tab !== tabStore.currentTab.value) return
        publishShownSite()
        nativeHome?.update(tab)
        vb.controlsHost.updateNavigation(tab.engine.canGoBack, tab.engine.canGoForward)
        updateAdblockStatus(tab)
    }

    /** Puts the strip back on the shown tab; with no shown tab left, the strip's choice is opened instead. */
    private fun cancelStripSelection() {
        val shown = tabStore.currentTab.value
        val index = tabStore.openTabs.indexOf(shown)
        if (shown == null || index < 0) return commitStripSelection()
        vb.controlsHost.current = index
        vb.controlsHost.showAddress(shown)
    }

    /** Commits the tab the strip shows as selected, or opens one when the strip points past the tabs. */
    private fun commitStripSelection() {
        val tab = tabAtStripSelection()
        when {
            tab == null -> openInNewTab(browserSettings.homePage, if (vb.controlsHost.current < 0) 0 else tabStore.openTabs.size,
                closeCapsuleAfter = true, navigateImmediately = true)
            !tab.selected -> changeTab(tab)
        }
    }

    // Ad blocking and pop-ups

    private fun blockerStatusLabel(tab: BrowserTab): Int {
        val initialized = ::adBlocker.isInitialized
        val indicator = blockerIndicator(
            tab.adblock ?: config.adBlockEnabled,
            initialized && adBlocker.hasActiveRules,
            initialized && adBlocker.listLoading.value,
            if (initialized) adBlocker.updateResult.value else AdBlocker.UpdateResult.IDLE,
        )
        return when (indicator) {
            BlockerIndicator.PAUSED -> R.string.blocker_state_paused
            BlockerIndicator.LOADING -> R.string.blocker_state_loading
            BlockerIndicator.REFRESHING -> R.string.blocker_state_updating
            BlockerIndicator.ACTIVE -> R.string.blocker_state_active
            BlockerIndicator.PARTIAL -> R.string.blocker_state_partial
            BlockerIndicator.SAVED -> R.string.blocker_state_saved
            BlockerIndicator.UNAVAILABLE -> R.string.blocker_state_none
        }
    }

    internal fun updateAdblockStatus(tab: BrowserTab) {
        val state = getString(blockerStatusLabel(tab))
        vb.controlsHost.showBlocker(tab.adblock ?: config.adBlockEnabled,
            resources.getQuantityString(R.plurals.blocker_summary, tab.blockedAds, state, tab.blockedAds))
    }

    private fun showBlockerDetails() {
        val tab = tabStore.currentTab.value ?: return
        val state = getString(blockerStatusLabel(tab))
        // A dialog message would hide the list, so the status is the title.
        TvDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.blocker_summary, tab.blockedAds, state, tab.blockedAds))
            .setItems(arrayOf(
                getString(R.string.blocker_toggle),
                getString(R.string.popups_title, tab.blockedPopups.toString()),
            )) { _, which ->
                when (which) {
                    0 -> {
                        commitStripSelection()
                        toggleBlockerForTab()
                    }
                    1 -> lifecycleScope.launch { choosePopupPolicy() }
                }
            }
            .show()
    }

    /** Applies a change of the ad blocking setting to the shown tab. */
    fun refreshAdblockSettings() {
        tabStore.currentTab.value?.let { tab ->
            tab.engine.setAdBlocking(tab.adblock ?: config.adBlockEnabled)
            onPageViewChanged(tab)
        }
    }

    /** Flips ad blocking for the shown tab only and reloads it; the lists load if this needs them. */
    private fun toggleBlockerForTab() {
        val tab = tabStore.currentTab.value ?: return
        val blocking = !(tab.adblock ?: config.adBlockEnabled)
        tab.adblock = blocking
        tab.engine.setAdBlocking(blocking)
        onPageViewChanged(tab)
        if (blocking && !adBlocker.hasActiveRules && !adBlocker.listLoading.value) adBlocker.loadBlockList(true)
        refresh()
    }

    private suspend fun choosePopupPolicy() {
        val tab = tabStore.currentTab.value ?: return
        val site = tabStore.siteSettingsFor(tab, false)
        val level = site?.popupBlockLevel ?: SiteSettings.DEFAULT_POPUP_POLICY
        val host = site?.hostName ?: runCatching { URL(tab.url).host }.getOrDefault("")
        TvDialog.Builder(this)
            .setTitle(getString(R.string.popups_title, host))
            .setSingleChoiceItems(R.array.popups_levels, level) { dialog, choice ->
                lifecycleScope.launch {
                    tabStore.setPopupPolicy(choice, tab)
                    dialog.dismiss()
                }
            }
            .show()
    }

    internal fun onPopupAdBlocked(popup: BrowserTab, opener: BrowserTab) {
        if (popup.closed) return
        Log.i(TAG, "Blocked a pop-up ad window")
        if (tabStore.currentTab.value === popup) closeTab(popup) else tabStore.onCloseTab(popup)
        opener.blockedPopups++
        showBlockedPopup(opener, newTab = true)
    }

    /** Tells the user about a blocked pop-up or dialog, at most once per [BLOCKED_NOTICE_INTERVAL_MS]. */
    internal fun showBlockedPopup(tab: BrowserTab, newTab: Boolean) {
        if (isDestroyed || tabStore.currentTab.value !== tab) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockedNotice < BLOCKED_NOTICE_INTERVAL_MS) return
        lastBlockedNotice = now
        ToastBar.showBottomRight(vb.browserRoot, TvSymbol.Block,
            getString(if (newTab) R.string.popups_window_blocked else R.string.popups_dialog_blocked))
    }

    // Loading progress and downloads

    private val hideProgress = Runnable {
        val fade = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
        fade.onAnimationEnd { vb.loadProgress.visibility = View.GONE }
        vb.loadProgress.startAnimation(fade)
    }

    /** The thin bar shows loading; it fades a second after completion, or after 5 s without news. */
    internal fun showLoadProgress(progress: Int) {
        vb.loadProgress.visibility = View.VISIBLE
        vb.loadProgress.setProgress(progress, true)
        mainThread.removeCallbacks(hideProgress)
        mainThread.postDelayed(hideProgress, if (progress == 100) PROGRESS_DONE_MS else PROGRESS_STALL_MS)
    }

    private fun onDownloadStarted(fileName: String) {
        @Suppress("DEPRECATION") // Only names the folder in the message.
        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        toast(getString(R.string.download_started, folder.toString() + File.separator + fileName))
        openCapsule()
    }

    // Private mode

    fun togglePrivateMode() {
        // Separate persistent WebView profiles require API 28's data-directory suffix.
        if (android.os.Build.VERSION.SDK_INT < 28) return
        togglePrivateMode(andSwitchProcess = true)
    }

    /** Leaving private mode erases its pages, cookies, storage, cache and tabs before the switch. */
    private fun togglePrivateMode(andSwitchProcess: Boolean) = lifecycleScope.launch {
        val entering = !config.privateBrowsing
        vb.startupSpinner.visibility = View.VISIBLE
        if (!entering) {
            WebStorage.getInstance().deleteAllData()
            CookieJar.clear()
            Engines.clearCache(this@MainActivity)
            tabStore.onCloseOthers().join()
            tabStore.currentTab.value = null
            visitLog.clearPrivateData().join()
        }
        vb.startupSpinner.visibility = View.GONE
        config.privateBrowsing = entering
        if (andSwitchProcess) openPrivateWindow(entering)
    }

    /** Restarts as the other mode's activity in a new task; this process ends so the modes never share data. */
    private fun openPrivateWindow(incognito: Boolean, intentToCopy: Intent? = null) {
        val target = Intent(this, if (incognito) PrivateBrowserActivity::class.java else MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intentToCopy?.let { source ->
            ExternalWebNavigation.copyAllowedData(source, target)
        }
        startActivity(target)
        exitProcess(0)
    }

    // Remote input

    fun toggleMenu() {
        when {
            isFullscreen -> tabStore.currentTab.value?.engine?.closeFullscreen()
            overlayVisible -> closeCapsule()
            else -> openCapsule()
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            backPresses.sendSystemBack()
        }
    }

    /** Back from the system, the remote, a gamepad's B or Escape: one event per press, whatever the channel. */
    internal val backPresses = BackPresses(
        onBackKey = { if (!hideKeyboard()) consumeBack() },
    )

    /**
     * Keys and motion pass through here before any view: Back first, then user shortcuts, then the
     * pointer while the page owns the screen (so a hidden capsule can never swallow it), then the views.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val views = window.callback
        window.callback = object : Window.Callback by views {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                UserActivation.onKeyEvent(event)
                if (backPresses.dispatchKeyEvent(event)) return true
                val code = if (event.keyCode != 0) event.keyCode else event.scanCode
                if (code !in BackPresses.BACK_KEYS &&
                    Shortcuts.getInstance().handle(event, this@MainActivity, tabStore.currentTab.value)) return true
                if (downLeavesTabStrip(overlayVisible, vb.controlsHost.tabStripHasFocus, event.keyCode, event.action, event.repeatCount)) {
                    closeCapsule()
                    return true
                }
                if (browserOwnsPointer() && event.keyCode in RemoteInput.NAVIGATION_KEYS &&
                    vb.pageContainer.dispatchKeyEvent(event)) return true
                return views.dispatchKeyEvent(event)
            }

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                UserActivation.onTouchEvent(event)
                return views.dispatchTouchEvent(event)
            }

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                if (backPresses.dispatchGenericMotionEvent(event)) return true
                if (browserOwnsPointer() && RemoteInput.isNavigationSource(event) &&
                    vb.pageContainer.dispatchGenericMotionEvent(event)) return true
                return views.dispatchGenericMotionEvent(event)
            }
        }
    }

    /** The page owns the screen: no capsule, pointer menu, text field or keyboard in front of it. */
    private fun browserOwnsPointer(): Boolean {
        if (!::vb.isInitialized || !window.decorView.hasWindowFocus() || nativeHome?.isVisible == true) return false
        val page = vb.pageContainer
        return page.isShown && page.pointerEnabled && !page.cursorSuppressed && !page.isTextInputActive() &&
            !vb.pointerMenu.isVisible && !vb.controlsOverlay.isVisible && !keyboardVisible()
    }

    private fun keyboardVisible() =
        ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    /** Back while typing only closes the keyboard, as everywhere on Android. */
    private fun hideKeyboard(): Boolean {
        if (!keyboardVisible()) return false
        val target = currentFocus ?: window.decorView
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(target.windowToken, 0)
        return true
    }

    /**
     * Back closes the innermost thing open: pointer menu, fullscreen, typing, Home's tile editing;
     * on Home it then leaves the app; elsewhere capsule, grab mode, D-pad mode (before any web
     * history, as its help promises); else opens the capsule.
     */
    internal fun consumeBack() {
        val pointer = vb.pageContainer.pointerController
        when {
            vb.pointerMenu.isVisible -> vb.pointerMenu.close()
            isFullscreen -> {
                fullscreenBackAt = SystemClock.uptimeMillis()
                tabStore.currentTab.value?.engine?.closeFullscreen()
            }
            vb.controlsHost.isEditingAddress -> vb.controlsHost.cancelAddressInput()
            nativeHome?.consumeBack() == true -> Unit
            // N08: Home is the app's root screen, so Back there leaves to the launcher whatever the
            // tab's web history, unless the capsule is previewing another tab (Back returns to Home).
            nativeHome?.isVisible == true && (!overlayVisible || tabAtStripSelection() === tabStore.currentTab.value) &&
                SystemClock.uptimeMillis() - fullscreenBackAt >= FULLSCREEN_BACK_SETTLE_MS -> finish()
            // A second press while the video is still coming down (or a remote's double send) must not
            // close the capsule that the first one opened.
            overlayVisible -> if (SystemClock.uptimeMillis() - fullscreenBackAt >= FULLSCREEN_BACK_SETTLE_MS) {
                // N08: at the root (no history), Back on the capsule that Back opened leaves to the
                // launcher (TV-DB); otherwise it closes the capsule as before.
                if (capsuleFromBack && tabStore.currentTab.value?.engine?.canGoBack != true) finish() else closeCapsule()
            }
            pointer.canGoBackInPage() -> pointer.consumeBack()
            !vb.pageContainer.pointerEnabled -> {
                // Leaving D-pad mode returns to the pointer on the same page; nothing else happens.
                tabStore.currentTab.value?.engine?.setPointerMode(true)
                backPresses.holdBGoesBack = false
            }
            else -> {
                openCapsule()
                capsuleFromBack = overlayVisible
            }
        }
    }

    // The capsule over the page

    private var overlayVisible = false
    /** The open capsule was opened by Back on the page, so a second Back at the root may leave. */
    private var capsuleFromBack = false
    /** When Back last closed a fullscreen video. */
    private var fullscreenBackAt = Long.MIN_VALUE / 2
    /** Bumped whenever the preview may change, so a slow picture load never shows a stale tab. */
    private var previewGeneration = 0
    private var focusedPreviewTab: BrowserTab? = null
    private val showFocusedPreview = Runnable { lifecycleScope.launch { showPreview(focusedPreviewTab) } }

    /**
     * Opens the capsule over the page with focus on Back (Home when Back is unavailable). The shown
     * tab stays live behind it; another tab selected in the strip is previewed by its picture.
     */
    internal fun openCapsule() {
        if (isFullscreen) {
            tabStore.currentTab.value?.engine?.closeFullscreen()
            return
        }
        if (overlayVisible) {
            vb.controlsHost.takeFocus()
            return
        }
        capsuleFromBack = false
        overlayVisible = true
        blockFocusBehindCapsule(true)
        previewGeneration++
        vb.controlsOverlay.animate().cancel()
        vb.tabPreview.animate().cancel()
        vb.tabPreview.visibility = View.INVISIBLE
        vb.tabPreview.setImageResource(0)
        vb.pageContainer.cursorSuppressed = true
        // The first press on the page closes the capsule and reaches the page.
        vb.pageContainer.onPageTouch = {
            if (vb.controlsHost.isEditingAddress) vb.controlsHost.cancelAddressInput()
            closeCapsule()
        }
        val previewed = tabAtStripSelection()
        showLivePage(previewed != null && previewed === tabStore.currentTab.value)
        lifecycleScope.launch { showPreview(previewed) }
        vb.controlsHost.closeAddressEditing()
        vb.controlsOverlay.visibility = View.VISIBLE
        // History can change without a page load (pushState on single-page sites): refresh Back first.
        tabStore.currentTab.value?.let(::onPageViewChanged)
        // Focus is part of opening, not of the animation's end.
        vb.controlsHost.takeFocus()
        vb.controlsOverlay.translationY = -vb.controlsOverlay.height.toFloat()
        vb.controlsOverlay.alpha = 0f
        vb.controlsOverlay.animate().translationY(0f).alpha(1f).setDuration(OVERLAY_OPEN_MS)
            .setInterpolator(DecelerateInterpolator()).start()
        nativeHome?.followCapsule(animate = true)
    }

    /** Shows [tab] behind the capsule: the live page for the shown tab, its stored picture for another. */
    internal suspend fun showPreview(tab: BrowserTab?) {
        if (!overlayVisible || tabAtStripSelection() !== tab) return
        if (tab != null && tab === tabStore.currentTab.value) {
            // The live WebView at full resolution, never a stretched small picture.
            previewGeneration++
            showPicture(null, placeholder = false)
            showLivePage(true)
            return
        }
        showLivePage(false)
        if (tab == null) return showPicture(null, placeholder = true)
        val request = ++previewGeneration
        vb.tabPreview.setImageResource(0) // Never leave the previous site's picture up while loading.
        val picture = tab.thumbnail ?: withContext(Dispatchers.IO) { tab.loadPreview() }
        if (request != previewGeneration || !overlayVisible || tab.closed || tabAtStripSelection() !== tab) return
        showPicture(picture, placeholder = picture == null)
    }

    private fun showLivePage(visible: Boolean) {
        vb.pageContainer.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        nativeHome?.setPageVisible(visible)
    }

    private fun showPicture(picture: Bitmap?, placeholder: Boolean) {
        vb.previewPlaceholder.visibility = if (placeholder) View.VISIBLE else View.INVISIBLE
        vb.tabPreview.visibility = if (picture == null) View.INVISIBLE else View.VISIBLE
        vb.tabPreview.setImageBitmap(picture)
    }

    /**
     * Closes the capsule at once (it stops owning focus even if an animation was cut) and returns to
     * the shown page. A tab only previewed in the strip is not opened: OK on it does that
     * (onTitleSelected); Back, Down or a touch return to the tab that was shown.
     */
    internal fun closeCapsule() {
        val wasVisible = overlayVisible
        overlayVisible = false
        capsuleFromBack = false
        blockFocusBehindCapsule(false)
        vb.pageContainer.onPageTouch = null
        vb.pageContainer.cursorSuppressed = nativeHome?.isVisible == true
        previewGeneration++
        mainThread.removeCallbacks(showFocusedPreview)
        focusedPreviewTab = null
        vb.controlsHost.closeAddressEditing()
        vb.controlsOverlay.animate().cancel()
        vb.tabPreview.animate().cancel()
        vb.controlsOverlay.visibility = View.INVISIBLE
        vb.controlsOverlay.translationY = 0f
        vb.controlsOverlay.alpha = 1f
        nativeHome?.followCapsule(animate = false)
        vb.tabPreview.translationY = 0f
        showPicture(null, placeholder = false)
        if (wasVisible) cancelStripSelection()
        if (!vb.pointerMenu.isVisible) {
            showLivePage(true)
            if (nativeHome?.focusContent() != true) tabStore.currentTab.value?.engine?.view?.requestFocus()
        }
    }

    /**
     * N01: the open capsule is modal for the remote. The page and Home behind it cannot take focus,
     * whatever asks for it: an arrow key past the capsule's edge, a page attached on a tab change,
     * or the window's default focus when Compose releases its own.
     */
    private fun blockFocusBehindCapsule(block: Boolean) {
        val mode = if (block) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_BEFORE_DESCENDANTS
        vb.pageContainer.descendantFocusability = mode
        vb.nativeHomeContainer.descendantFocusability = mode
    }

    private companion object {
        const val TAG = "MainActivity"
        const val WEBVIEW_PACKAGE = "com.google.android.webview"
        /** Codes for page permission requests, above the download queue's. */
        const val PAGE_PERMISSION_REQUESTS_START = 10100
        const val SUGGESTIONS_DELAY_MS = 150L
        const val PREVIEW_FOCUS_DELAY_MS = 200L
        const val OVERLAY_OPEN_MS = 220L
        const val FULLSCREEN_BACK_SETTLE_MS = 800L
        const val PROGRESS_DONE_MS = 1000L
        const val PROGRESS_STALL_MS = 5000L
        // Pop-up and dialog spam must not stack notices on a slow TV.
        const val BLOCKED_NOTICE_INTERVAL_MS = 3000L
    }
}
