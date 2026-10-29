package com.reiniertutoriales.vireolumatv.activity.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.PrivateBrowserActivity
import com.reiniertutoriales.vireolumatv.activity.downloads.DownloadsActivity
import com.reiniertutoriales.vireolumatv.activity.downloads.IncognitoDownloadsActivity
import com.reiniertutoriales.vireolumatv.activity.history.HistoryActivity
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites.FavoritesPanel
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.SettingsScreen
import com.reiniertutoriales.vireolumatv.activity.main.view.BrowserControls
import com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabChips
import com.reiniertutoriales.vireolumatv.databinding.BrowserWindowBinding
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.singleton.Favicons
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.Shortcuts
import com.reiniertutoriales.vireolumatv.utils.BackPresses
import com.reiniertutoriales.vireolumatv.utils.RemoteInput
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.utils.hasInternet
import com.reiniertutoriales.vireolumatv.utils.onAnimationEnd
import com.reiniertutoriales.vireolumatv.utils.toast
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import com.reiniertutoriales.vireolumatv.webengine.Engines
import com.reiniertutoriales.vireolumatv.webengine.webview.UserActivation
import com.reiniertutoriales.vireolumatv.webengine.webview.ServiceWorkerFilter
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewPageEngine
import com.reiniertutoriales.vireolumatv.widgets.ToastBar
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
open class MainActivity : AppCompatActivity(), BrowserControls.Callback {
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

    /** Delivered to the current instance even after the window was recreated. */
    internal val filePickerResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        tabStore.currentTab.value?.engine?.onFilePicked(result.resultCode, result.data)
    }
    private val historyResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        result.data?.getStringExtra(HistoryActivity.KEY_URL)?.let(::navigate)
        closeCapsule()
    }
    private var nextPermissionRequest = PAGE_PERMISSION_REQUESTS_START
    private var lastBlockedNotice = 0L
    private var suggestionsLookup: Job? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incognito = config.privateBrowsing
        // Private mode has its own activity class and process; the wrong one restarts as the right one.
        if (incognito xor (this is PrivateBrowserActivity)) {
            openPrivateWindow(incognito, intent)
            finish()
            return
        }
        visitLog = ScreenModels.get(VisitLog::class, this)
        if (incognito) visitLog.preparePrivateWindow()
        browserSettings = ScreenModels.get(BrowserSettings::class, this)
        adBlocker = ScreenModels.get(AdBlocker::class, this)
        val filters = adBlocker
        WebViewPageEngine.pageFiltersProvider = { url -> filters.pageFilters(url) }
        WebViewPageEngine.hiddenSelectorsProvider = { url, classes, ids -> filters.hiddenSelectors(url, classes, ids) }
        tabStore = ScreenModels.get(TabStore::class, this)
        ServiceWorkerFilter.policy = serviceWorkerPolicy
        ServiceWorkerFilter.install(this)
        mainThread = Handler(Looper.getMainLooper())
        vb = BrowserWindowBinding.inflate(layoutInflater)
        setContentView(vb.root)
        vb.tabPreview.visibility = View.INVISIBLE
        vb.controlsOverlay.visibility = View.INVISIBLE
        vb.loadProgress.visibility = View.GONE
        vb.tabStrip.listener = tabStrip
        configureBrowserControls()
        observeModels()
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        loadState()
    }

    private fun observeModels() {
        config.userAgentOverride.observe(lifecycle, false) { agent -> tabStore.openTabs.forEach { it.engine.userAgent = agent } }
        browserSettings.keepScreenOn.observe(lifecycle) { on ->
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        tabStore.currentTab.observe(this) { tab ->
            vb.controlsRow.showAddress(tab?.url.orEmpty())
            tab?.let(::onPageViewChanged)
        }
        adBlocker.listLoading.observe(lifecycle) { tabStore.currentTab.value?.let(::updateAdblockStatus) }
        adBlocker.updateResult.observe(lifecycle) { tabStore.currentTab.value?.let(::updateAdblockStatus) }
        tabStore.openTabs.observe(this, false) { if (it.isEmpty()) vb.pageContainer.removeAllViews() }
    }

    /** Reads the stored state, then shows the tab the intent asks for, or the selected one, or a new home tab. */
    private fun loadState() = lifecycleScope.launch {
        vb.startupSpinner.visibility = View.VISIBLE
        vb.startupSpinner.requestFocus()
        visitLog.loadState().join()
        tabStore.loadState().join()
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
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::tabStore.isInitialized && tabStore.loaded && intent.data != null) handleIntent(intent)
    }

    /** An address from another app opens in a new tab, or in the current one when the intent asks for it. */
    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        if (!ExternalWebNavigation.isAllowed(uri)) {
            Log.w(TAG, "Rejected external navigation with scheme ${uri?.scheme}")
            return
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_IN_SAME_TAB, false) && tabStore.openTabs.isNotEmpty()) {
            if (tabStore.currentTab.value == null) changeTab(tabStore.openTabs[0])
            navigate(uri.toString())
        } else {
            openInNewTab(uri.toString(), tabStore.openTabs.size, closeCapsuleAfter = true, navigateImmediately = true)
        }
    }

    override fun onStart() {
        super.onStart()
        downloads.bind()
    }

    override fun onStop() {
        super.onStop()
        downloads.unbind()
    }

    override fun onResume() {
        super.onResume()
        if (!networkCallbackRegistered) {
            try {
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
    }

    override fun onPause() {
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

    /** Service workers belong to no tab: the shown tab's choice applies to its own site, else the setting. */
    private val serviceWorkerPolicy = object : ServiceWorkerFilter.Policy {
        override fun blocksAds(site: Uri): Boolean {
            val tab = tabStore.currentTab.value
            val sameSite = tab != null && tab.url.toUri().host.equals(site.host, ignoreCase = true)
            return (if (sameSite) tab?.adblock else null) ?: config.adBlockEnabled
        }
        override fun isAd(url: Uri, type: String?, site: Uri) = adBlocker.isAd(url, type, site)
        override fun redirect(url: Uri, type: String?, site: Uri) = adBlocker.redirectFor(url, type, site)
    }

    override fun onDestroy() {
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
    // Replaces the CONNECTIVITY_ACTION broadcast; callbacks arrive on a binder thread.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = postNetworkState()
        override fun onLost(network: Network) = postNetworkState()
    }

    private fun postNetworkState() {
        mainThread.post {
            if (isDestroyed) return@post
            val connected = hasInternet()
            tabStore.currentTab.value?.engine?.setOnline(connected)
            // A TV often starts before Wi-Fi: fetch the filter lists as soon as it connects.
            if (connected) adBlocker.updateIfDue(networkRestored = true)
        }
    }

    // Capsule commands

    @VisibleForTesting
    internal fun configureBrowserControls() {
        val bar = vb.controlsRow
        bar.callback = this
        vb.vSuggestions.onPick = { bar.submit(it.url) }
        // A command runs on the tab shown, after the capsule hands focus back to the page.
        fun pageCommand(button: View, action: () -> Unit) = button.setOnClickListener {
            closeCapsule()
            action()
        }
        pageCommand(bar.homeButton) { navigate(browserSettings.homePage) }
        pageCommand(bar.backButton) { navigateBack() }
        pageCommand(bar.forwardButton) { tabStore.currentTab.value?.engine?.let { if (it.canGoForward) it.forward() } }
        pageCommand(bar.refreshButton) { refresh() }
        bar.adBlockButton.setOnClickListener {
            commitStripSelection()
            toggleBlockerForTab()
        }
        bar.adBlockButton.setOnLongClickListener {
            commitStripSelection()
            showBlockerDetails()
            true
        }
    }

    override fun showAppMenu() = AppMenuPanel(this, appMenuEntries(), ::runAppMenuEntry).show()

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
        historyResult.launch(Intent(this, HistoryActivity::class.java))
        closeCapsule()
    }

    fun showFavorites() {
        val shown = tabStore.currentTab.value
        FavoritesPanel(this, lifecycleScope, shown?.title, shown?.url) { item -> item.url?.let(::navigate) }.show()
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

    override fun search(text: String) = navigate(AddressInput.resolve(text, config.searchAddress.value))

    override fun onUrlInputDone() {
        closeSuggestions()
        closeCapsule()
    }

    override fun onAddressTyped(text: String) {
        suggestionsLookup?.cancel()
        suggestionsLookup = lifecycleScope.launch {
            delay(SUGGESTIONS_DELAY_MS) // One lookup when typing pauses, not one per key.
            showSuggestions(runCatching { AddressSuggestions.load(text, config.privateBrowsing) }
                .onFailure { Log.w(TAG, "Address suggestions failed", it) }
                .getOrDefault(emptyList()))
        }
    }

    override fun focusSuggestions(): Boolean = vb.vSuggestions.hasSuggestions && vb.vSuggestions.focusFirst()

    override fun onAddressInputCancelled() = closeSuggestions()

    /** Suggestions take the tab strip's place while there are any, so the capsule does not grow. */
    private fun showSuggestions(suggestions: List<AddressSuggestion>) {
        vb.vSuggestions.show(suggestions)
        vb.tabStrip.isVisible = suggestions.isEmpty()
    }

    private fun closeSuggestions() {
        suggestionsLookup?.cancel()
        showSuggestions(emptyList())
    }

    // Navigation

    fun navigate(url: String) {
        vb.controlsRow.tintAddress(ContextCompat.getColor(this, R.color.on_surface))
        val tab = tabStore.currentTab.value
        if (tab == null) {
            openInNewTab(url, 0, closeCapsuleAfter = true, navigateImmediately = true)
            return
        }
        tab.url = url
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

    private val tabStrip = object : TabChips.Listener {
        /** Focus moved to another tab: show its address and, a moment later, its preview. */
        override fun onTitleChanged(index: Int) {
            val tab = tabAt(index)
            vb.controlsRow.showAddress(tab?.url.orEmpty())
            previewGeneration++
            mainThread.removeCallbacks(showFocusedPreview)
            focusedPreviewTab = tab
            mainThread.postDelayed(showFocusedPreview, PREVIEW_FOCUS_DELAY_MS)
        }

        override fun onTitleSelected(index: Int) {
            commitStripSelection()
            closeCapsule()
        }

        override fun onAddNewTabSelected() {
            openInNewTab(browserSettings.homePage, tabStore.openTabs.size, navigateImmediately = true)
        }

        override fun closeTab(tabState: BrowserTab?) = this@MainActivity.closeTab(tabState)

        override fun openInNewTab(url: String, tabIndex: Int) {
            this@MainActivity.openInNewTab(url, tabIndex, closeCapsuleAfter = false, navigateImmediately = true)
        }
    }

    private fun tabAt(index: Int) = tabStore.openTabs.getOrNull(index)

    /** The tab the strip shows as selected, which differs from the shown one while another is previewed. */
    internal fun tabAtStripSelection() = tabAt(vb.tabStrip.current)

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
        if (tabStore.currentTab.value === tab) tabStore.currentTab.value = null
        val neighbour = tabs.getOrNull(position - 1)?.takeIf { position > 0 } ?: tabs.getOrNull(position + 1)
        if (neighbour != null) changeTab(neighbour)
        else openInNewTab(browserSettings.homePage, 0, closeCapsuleAfter = true, navigateImmediately = true)
        tabStore.onCloseTab(tab)
        closeCapsule()
    }

    internal fun changeTab(newTab: BrowserTab, loadInitialUrl: Boolean = true) =
        tabStore.changeTab(newTab, ::createWebView, vb.pageContainer, TabWindow(this, newTab), loadInitialUrl)

    /** The tab's WebView, or null after telling the user that this device has no usable WebView. */
    internal fun createWebView(tab: BrowserTab): View? {
        val view = try {
            tab.engine.createView(this)
        } catch (error: Throwable) {
            Log.w(TAG, "WebView could not be created", error)
            showNoWebViewDialog()
            return null
        }
        config.userAgentOverride.value?.let { tab.engine.userAgent = it }
        return view
    }

    private fun showNoWebViewDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.error)
            .setCancelable(false)
            .setMessage(R.string.error_no_webview)
            .setNegativeButton(R.string.action_exit) { _, _ -> finish() }
        val store = Intent(Intent.ACTION_VIEW, "market://details?id=$WEBVIEW_PACKAGE".toUri())
        if (packageManager.queryIntentActivities(store, 0).isNotEmpty()) {
            dialog.setPositiveButton(R.string.action_open_store) { _, _ ->
                runCatching { startActivity(store) }.onFailure { Log.w(TAG, "Opening the app store failed", it) }
                finish()
            }
        }
        dialog.show()
    }

    /** The capsule follows the shown tab: Back and Forward availability and the blocker shield. */
    internal fun onPageViewChanged(tab: BrowserTab) {
        vb.controlsRow.backButton.isEnabled = tab.engine.canGoBack
        vb.controlsRow.forwardButton.isEnabled = tab.engine.canGoForward
        val blocking = tab.adblock ?: config.adBlockEnabled
        vb.controlsRow.adBlockButton.setImageResource(if (blocking) R.drawable.ic_shield_on else R.drawable.ic_shield_off)
        updateAdblockStatus(tab)
    }

    /** Commits the tab the strip shows as selected, or opens one when the strip points past the tabs. */
    private fun commitStripSelection() {
        val tab = tabAtStripSelection()
        when {
            tab == null -> openInNewTab(browserSettings.homePage, if (vb.tabStrip.current < 0) 0 else tabStore.openTabs.size,
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
        vb.controlsRow.adBlockButton.contentDescription = getString(R.string.blocker_summary, state, tab.blockedAds)
        vb.controlsRow.adBlockButton.isActivated =
            (tab.adblock ?: config.adBlockEnabled) && ::adBlocker.isInitialized && adBlocker.hasActiveRules
    }

    private fun showBlockerDetails() {
        val tab = tabStore.currentTab.value ?: return
        val state = getString(blockerStatusLabel(tab))
        // A dialog message would hide the list, so the status is the title.
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.blocker_summary, state, tab.blockedAds))
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
        AlertDialog.Builder(this)
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
        ToastBar.showBottomRight(vb.browserRoot, R.drawable.ic_block,
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
        togglePrivateMode(andSwitchProcess = true)
    }

    /** Leaving private mode erases its pages, cookies, storage, cache and tabs before the switch. */
    private fun togglePrivateMode(andSwitchProcess: Boolean) = lifecycleScope.launch {
        val entering = !config.privateBrowsing
        vb.startupSpinner.visibility = View.VISIBLE
        if (!entering) {
            withContext(Dispatchers.IO) {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
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
            source.extras?.let(target::putExtras)
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
                if (downLeavesTabStrip(overlayVisible, vb.tabStrip.hasFocus(), event.keyCode, event.action, event.repeatCount)) {
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
        if (!::vb.isInitialized || !window.decorView.hasWindowFocus()) return false
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

    /** Back closes the innermost thing open: pointer menu, fullscreen, typing, capsule, grab mode; else opens the capsule. */
    @VisibleForTesting
    internal fun consumeBack() {
        val pointer = vb.pageContainer.pointerController
        when {
            vb.pointerMenu.isVisible -> vb.pointerMenu.close()
            isFullscreen -> tabStore.currentTab.value?.engine?.closeFullscreen()
            vb.controlsRow.isEditingAddress -> vb.controlsRow.cancelAddressInput()
            overlayVisible -> closeCapsule()
            pointer.canGoBackInPage() -> pointer.consumeBack()
            else -> {
                tabStore.currentTab.value?.engine?.setPointerMode(true)
                backPresses.holdBGoesBack = false
                openCapsule()
            }
        }
    }

    // The capsule over the page

    private var overlayVisible = false
    /** Bumped whenever the preview may change, so a slow picture load never shows a stale tab. */
    private var previewGeneration = 0
    private var focusedPreviewTab: BrowserTab? = null
    private val showFocusedPreview = Runnable { lifecycleScope.launch { showPreview(focusedPreviewTab) } }

    /**
     * Opens the capsule over the page with focus on Back (Home when Back is unavailable). The shown
     * tab stays live behind it; another tab selected in the strip is previewed by its picture.
     */
    @VisibleForTesting
    internal fun openCapsule() {
        if (isFullscreen) {
            tabStore.currentTab.value?.engine?.closeFullscreen()
            return
        }
        if (overlayVisible) return
        overlayVisible = true
        previewGeneration++
        vb.controlsOverlay.animate().cancel()
        vb.tabPreview.animate().cancel()
        vb.tabPreview.visibility = View.INVISIBLE
        vb.tabPreview.setImageResource(0)
        vb.pageContainer.cursorSuppressed = true
        // The first press on the page closes the capsule and reaches the page.
        vb.pageContainer.onPageTouch = {
            if (vb.controlsRow.isEditingAddress) vb.controlsRow.cancelAddressInput()
            closeCapsule()
        }
        val previewed = tabAtStripSelection()
        vb.pageContainer.visibility =
            if (previewed != null && previewed === tabStore.currentTab.value) View.VISIBLE else View.INVISIBLE
        lifecycleScope.launch { showPreview(previewed) }
        vb.controlsRow.closeAddressEditing()
        vb.controlsOverlay.visibility = View.VISIBLE
        // History can change without a page load (pushState on single-page sites): refresh Back first.
        tabStore.currentTab.value?.let(::onPageViewChanged)
        // Focus is part of opening, not of the animation's end.
        if (!vb.controlsRow.backButton.requestFocus()) vb.controlsRow.homeButton.requestFocus()
        vb.controlsOverlay.translationY = -vb.controlsOverlay.height.toFloat()
        vb.controlsOverlay.alpha = 0f
        vb.controlsOverlay.animate().translationY(0f).alpha(1f).setDuration(OVERLAY_OPEN_MS)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /** Shows [tab] behind the capsule: the live page for the shown tab, its stored picture for another. */
    internal suspend fun showPreview(tab: BrowserTab?) {
        if (!overlayVisible || tabAtStripSelection() !== tab) return
        if (tab != null && tab === tabStore.currentTab.value) {
            // The live WebView at full resolution, never a stretched small picture.
            previewGeneration++
            showPicture(null, placeholder = false)
            vb.pageContainer.visibility = View.VISIBLE
            return
        }
        vb.pageContainer.visibility = View.INVISIBLE
        if (tab == null) return showPicture(null, placeholder = true)
        val request = ++previewGeneration
        vb.tabPreview.setImageResource(0) // Never leave the previous site's picture up while loading.
        val picture = tab.thumbnail ?: withContext(Dispatchers.IO) { tab.loadPreview() }
        if (request != previewGeneration || !overlayVisible || tab.closed || tabAtStripSelection() !== tab) return
        showPicture(picture, placeholder = picture == null)
    }

    private fun showPicture(picture: Bitmap?, placeholder: Boolean) {
        vb.previewPlaceholder.visibility = if (placeholder) View.VISIBLE else View.INVISIBLE
        vb.tabPreview.visibility = if (picture == null) View.INVISIBLE else View.VISIBLE
        vb.tabPreview.setImageBitmap(picture)
    }

    /** Closes the capsule at once (it stops owning focus even if an animation was cut) and returns to the page. */
    @VisibleForTesting
    internal fun closeCapsule() {
        val wasVisible = overlayVisible
        overlayVisible = false
        vb.pageContainer.onPageTouch = null
        vb.pageContainer.cursorSuppressed = false
        previewGeneration++
        mainThread.removeCallbacks(showFocusedPreview)
        focusedPreviewTab = null
        vb.controlsRow.closeAddressEditing()
        vb.controlsOverlay.animate().cancel()
        vb.tabPreview.animate().cancel()
        vb.controlsOverlay.visibility = View.INVISIBLE
        vb.controlsOverlay.translationY = 0f
        vb.controlsOverlay.alpha = 1f
        vb.tabPreview.translationY = 0f
        showPicture(null, placeholder = false)
        if (wasVisible) commitStripSelection()
        if (!vb.pointerMenu.isVisible) {
            vb.pageContainer.visibility = View.VISIBLE
            tabStore.currentTab.value?.engine?.view?.requestFocus()
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val EXTRA_OPEN_IN_SAME_TAB = "com.reiniertutoriales.vireolumatv.EXTRA_OPEN_IN_SAME_TAB"
        const val WEBVIEW_PACKAGE = "com.google.android.webview"
        /** Codes for page permission requests, above the download queue's. */
        const val PAGE_PERMISSION_REQUESTS_START = 10100
        const val SUGGESTIONS_DELAY_MS = 150L
        const val PREVIEW_FOCUS_DELAY_MS = 200L
        const val OVERLAY_OPEN_MS = 220L
        const val PROGRESS_DONE_MS = 1000L
        const val PROGRESS_STALL_MS = 5000L
        // Pop-up and dialog spam must not stack notices on a slow TV.
        const val BLOCKED_NOTICE_INTERVAL_MS = 3000L
    }
}
