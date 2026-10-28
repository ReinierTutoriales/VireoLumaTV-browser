package com.reiniertutoriales.vireolumatv.activity.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
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
import com.reiniertutoriales.vireolumatv.activity.IncognitoModeMainActivity
import com.reiniertutoriales.vireolumatv.activity.downloads.DownloadsActivity
import com.reiniertutoriales.vireolumatv.activity.downloads.IncognitoDownloadsActivity
import com.reiniertutoriales.vireolumatv.activity.history.HistoryActivity
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites.FavoritesDialog
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.SettingsDialog
import com.reiniertutoriales.vireolumatv.activity.main.view.ActionBar
import com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabsAdapter
import com.reiniertutoriales.vireolumatv.databinding.ActivityMainBinding
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import com.reiniertutoriales.vireolumatv.model.HostConfig
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.singleton.FaviconsPool
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.ShortcutMgr
import com.reiniertutoriales.vireolumatv.utils.BackNavigationEventsAdapter
import com.reiniertutoriales.vireolumatv.utils.RemoteInput
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import com.reiniertutoriales.vireolumatv.utils.hasInternet
import com.reiniertutoriales.vireolumatv.utils.onAnimationEnd
import com.reiniertutoriales.vireolumatv.utils.toast
import com.reiniertutoriales.vireolumatv.webengine.WebEngine
import com.reiniertutoriales.vireolumatv.webengine.WebEngineFactory
import com.reiniertutoriales.vireolumatv.webengine.webview.UserActivation
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewWebEngine
import com.reiniertutoriales.vireolumatv.widgets.NotificationView
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
 * [IncognitoModeMainActivity] in its own process.
 */
open class MainActivity : AppCompatActivity(), ActionBar.Callback {
    @VisibleForTesting internal lateinit var vb: ActivityMainBinding
    @VisibleForTesting internal lateinit var tabsModel: TabsModel
    @VisibleForTesting internal lateinit var settingsModel: SettingsModel
    @VisibleForTesting internal lateinit var adblockModel: AdblockModel
    @VisibleForTesting internal lateinit var uiHandler: Handler
    internal lateinit var viewModel: MainActivityViewModel
    internal val config = AppContext.provideConfig()
    @VisibleForTesting internal val downloads by lazy { BrowserDownloads(this, { config.incognitoMode }, ::onDownloadStarted) }
    internal val external by lazy { ExternalApps(this) }
    internal var isFullscreen = false

    /** Delivered to the current instance even after the window was recreated. */
    internal val filePickerResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        tabsModel.currentTab.value?.webEngine?.onFilePicked(result.resultCode, result.data)
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
        val incognito = config.incognitoMode
        // Private mode has its own activity class and process; the wrong one restarts as the right one.
        if (incognito xor (this is IncognitoModeMainActivity)) {
            switchProcess(incognito, intent)
            finish()
            return
        }
        viewModel = ActiveModelsRepository.get(MainActivityViewModel::class, this)
        if (incognito) viewModel.prepareSwitchToIncognito()
        settingsModel = ActiveModelsRepository.get(SettingsModel::class, this)
        adblockModel = ActiveModelsRepository.get(AdblockModel::class, this)
        val filters = adblockModel
        WebViewWebEngine.pageFiltersProvider = { url -> filters.pageFilters(url) }
        WebViewWebEngine.hiddenSelectorsProvider = { url, classes, ids -> filters.hiddenSelectors(url, classes, ids) }
        tabsModel = ActiveModelsRepository.get(TabsModel::class, this)
        uiHandler = Handler(Looper.getMainLooper())
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)
        vb.ivMiniatures.visibility = View.INVISIBLE
        vb.rlActionBar.visibility = View.INVISIBLE
        vb.progressBar.visibility = View.GONE
        vb.vTabs.listener = tabStrip
        configureBrowserControls()
        observeModels()
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        loadState()
    }

    private fun observeModels() {
        config.userAgentString.subscribe(lifecycle, false) { agent -> tabsModel.tabsStates.forEach { it.webEngine.userAgentString = agent } }
        settingsModel.keepScreenOn.subscribe(lifecycle) { on ->
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        tabsModel.currentTab.subscribe(this) { tab ->
            vb.vActionBar.setAddressBoxText(tab?.url.orEmpty())
            tab?.let(::onWebViewUpdated)
        }
        adblockModel.clientLoading.subscribe(lifecycle) { tabsModel.currentTab.value?.let(::updateAdblockStatus) }
        adblockModel.updateResult.subscribe(lifecycle) { tabsModel.currentTab.value?.let(::updateAdblockStatus) }
        tabsModel.tabsStates.subscribe(this, false) { if (it.isEmpty()) vb.flWebViewContainer.removeAllViews() }
    }

    /** Reads the stored state, then shows the tab the intent asks for, or the selected one, or a new home tab. */
    private fun loadState() = lifecycleScope.launch {
        vb.progressBarGeneric.visibility = View.VISIBLE
        vb.progressBarGeneric.requestFocus()
        viewModel.loadState().join()
        tabsModel.loadState().join()
        if (!isActive) return@launch
        vb.progressBarGeneric.visibility = View.GONE
        when {
            ExternalWebNavigation.isAllowed(intent.data) -> handleIntent(intent)
            tabsModel.tabsStates.isEmpty() -> openInNewTab(settingsModel.homePage, 0, closeCapsuleAfter = true, navigateImmediately = true)
            // No selected tab only happens after an error; the first one is shown then.
            else -> changeTab(tabsModel.tabsStates.firstOrNull { it.selected } ?: tabsModel.tabsStates[0])
        }
        val shown = tabsModel.currentTab.value
        if (shown == null || shown.url == settingsModel.homePage) openCapsule()
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::tabsModel.isInitialized && tabsModel.loaded && intent.data != null) handleIntent(intent)
    }

    /** An address from another app opens in a new tab, or in the current one when the intent asks for it. */
    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        if (!ExternalWebNavigation.isAllowed(uri)) {
            Log.w(TAG, "Rejected external navigation with scheme ${uri?.scheme}")
            return
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_IN_SAME_TAB, false) && tabsModel.tabsStates.isNotEmpty()) {
            if (tabsModel.currentTab.value == null) changeTab(tabsModel.tabsStates[0])
            navigate(uri.toString())
        } else {
            openInNewTab(uri.toString(), tabsModel.tabsStates.size, closeCapsuleAfter = true, navigateImmediately = true)
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
        tabsModel.currentTab.value?.webEngine?.onResume()
        // A TV app stays alive for days: the start-up check alone would never refresh the filter lists.
        adblockModel.updateIfDue()
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
        tabsModel.currentTab.value?.let { tab ->
            tab.webEngine.onPause()
            tab.onPause()
            tabsModel.saveTabBeforeExit(tab)
        }
        super.onPause()
    }

    override fun onDestroy() {
        overlayVisible = false
        previewGeneration++
        // Uninitialized when onCreate() restarted the window in the other mode.
        if (::vb.isInitialized) {
            vb.flWebViewContainer.onPageTouch = null
            vb.flWebViewContainer.cursorSuppressed = false
        }
        if (::uiHandler.isInitialized) uiHandler.removeCallbacks(showFocusedPreview)
        focusedPreviewTab = null
        if (::tabsModel.isInitialized) tabsModel.onDetachActivity()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        if (::tabsModel.isInitialized) {
            tabsModel.tabsStates.filterNot { it.selected }.forEach {
                it.thumbnail = null
                it.trimMemory()
            }
        }
        FaviconsPool.trimMemory()
        if (::adblockModel.isInitialized) adblockModel.trimMemory()
        super.onTrimMemory(level)
    }

    @Deprecated("Page permission requests use the numeric codes WebView hands out")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (tabsModel.currentTab.value?.webEngine?.onPermissionsResult(requestCode, permissions, grantResults) == true) return
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
        uiHandler.post {
            if (isDestroyed) return@post
            val connected = hasInternet()
            tabsModel.currentTab.value?.webEngine?.setNetworkAvailable(connected)
            // A TV often starts before Wi-Fi: fetch the filter lists as soon as it connects.
            if (connected) adblockModel.updateIfDue(networkRestored = true)
        }
    }

    // Capsule commands

    @VisibleForTesting
    internal fun configureBrowserControls() {
        val bar = vb.vActionBar
        bar.callback = this
        vb.vSuggestions.onPick = { bar.submit(it.url) }
        // A command runs on the tab shown, after the capsule hands focus back to the page.
        fun pageCommand(button: View, action: () -> Unit) = button.setOnClickListener {
            closeCapsule()
            action()
        }
        pageCommand(bar.homeButton) { navigate(settingsModel.homePage) }
        pageCommand(bar.backButton) { navigateBack() }
        pageCommand(bar.forwardButton) { tabsModel.currentTab.value?.webEngine?.let { if (it.canGoForward()) it.goForward() } }
        pageCommand(bar.refreshButton) { refresh() }
        bar.adBlockButton.setOnClickListener {
            commitStripSelection()
            toggleAdBlockForTab()
        }
        bar.adBlockButton.setOnLongClickListener {
            commitStripSelection()
            showBlockerDetails()
            true
        }
    }

    override fun showAppMenu() {
        val entries = appMenuEntries()
        AlertDialog.Builder(this)
            .setItems(entries.map { getString(it.label) }.toTypedArray()) { _, which -> runAppMenuEntry(entries[which]) }
            .show()
    }

    private fun runAppMenuEntry(entry: AppMenuEntry) = when (entry) {
        AppMenuEntry.HISTORY -> showHistory()
        AppMenuEntry.FAVORITES -> showFavorites()
        AppMenuEntry.DOWNLOADS -> showDownloads()
        AppMenuEntry.INCOGNITO -> toggleIncognitoMode()
        AppMenuEntry.SETTINGS -> showSettings()
        AppMenuEntry.CLOSE_TAB -> closeTab(tabsModel.currentTab.value)
        AppMenuEntry.CLOSE_APPLICATION -> closeWindow()
    }

    fun showDownloads() = startActivity(Intent(this,
        if (config.incognitoMode) IncognitoDownloadsActivity::class.java else DownloadsActivity::class.java))

    fun showHistory() {
        historyResult.launch(Intent(this, HistoryActivity::class.java))
        closeCapsule()
    }

    fun showFavorites() {
        val shown = tabsModel.currentTab.value
        FavoritesDialog(this, lifecycleScope, object : FavoritesDialog.Callback {
            override fun onFavoriteChoosen(item: FavoriteItem?) {
                item?.url?.let(::navigate)
            }
        }, shown?.title.orEmpty(), shown?.url.orEmpty()).show()
        closeCapsule()
    }

    fun showSettings() = SettingsDialog(this, settingsModel).show()

    /** Leaves private mode first (erasing its data) when it is on. */
    fun closeWindow() {
        lifecycleScope.launch {
            if (config.incognitoMode) toggleIncognitoMode(andSwitchProcess = false).join()
            finish()
        }
    }

    // Address field

    override fun search(text: String) = navigate(AddressInput.resolve(text, config.searchEngineURL.value))

    override fun onUrlInputDone() {
        closeSuggestions()
        closeCapsule()
    }

    override fun onAddressTyped(text: String) {
        suggestionsLookup?.cancel()
        suggestionsLookup = lifecycleScope.launch {
            delay(SUGGESTIONS_DELAY_MS) // One lookup when typing pauses, not one per key.
            showSuggestions(runCatching { AddressSuggestions.load(text, config.incognitoMode) }
                .onFailure { Log.w(TAG, "Address suggestions failed", it) }
                .getOrDefault(emptyList()))
        }
    }

    override fun focusSuggestions(): Boolean = vb.vSuggestions.hasSuggestions && vb.vSuggestions.focusFirst()

    override fun onAddressInputCancelled() = closeSuggestions()

    /** Suggestions take the tab strip's place while there are any, so the capsule does not grow. */
    private fun showSuggestions(suggestions: List<AddressSuggestion>) {
        vb.vSuggestions.show(suggestions)
        vb.vTabs.isVisible = suggestions.isEmpty()
    }

    private fun closeSuggestions() {
        suggestionsLookup?.cancel()
        showSuggestions(emptyList())
    }

    // Navigation

    fun navigate(url: String) {
        vb.vActionBar.setAddressBoxTextColor(ContextCompat.getColor(this, R.color.default_url_color))
        val tab = tabsModel.currentTab.value
        if (tab == null) {
            openInNewTab(url, 0, closeCapsuleAfter = true, navigateImmediately = true)
            return
        }
        tab.url = url
        tab.webEngine.loadUrl(url)
    }

    /** The page's history first; without it the capsule opens or closes, or [goHomeIfNoHistory] goes home. */
    fun navigateBack(goHomeIfNoHistory: Boolean = false) {
        val engine = tabsModel.currentTab.value?.webEngine
        when {
            engine?.canGoBack() == true -> engine.goBack()
            goHomeIfNoHistory -> navigate(settingsModel.homePage)
            vb.rlActionBar.isVisible -> closeCapsule()
            else -> openCapsule()
        }
    }

    fun refresh() {
        tabsModel.currentTab.value?.webEngine?.reload()
    }

    // Tabs

    private val tabStrip = object : TabsAdapter.Listener {
        /** Focus moved to another tab: show its address and, a moment later, its preview. */
        override fun onTitleChanged(index: Int) {
            val tab = tabAt(index)
            vb.vActionBar.setAddressBoxText(tab?.url.orEmpty())
            previewGeneration++
            uiHandler.removeCallbacks(showFocusedPreview)
            focusedPreviewTab = tab
            uiHandler.postDelayed(showFocusedPreview, PREVIEW_FOCUS_DELAY_MS)
        }

        override fun onTitleSelected(index: Int) {
            commitStripSelection()
            closeCapsule()
        }

        override fun onAddNewTabSelected() {
            openInNewTab(settingsModel.homePage, tabsModel.tabsStates.size, navigateImmediately = true)
        }

        override fun closeTab(tabState: WebTabState?) = this@MainActivity.closeTab(tabState)

        override fun openInNewTab(url: String, tabIndex: Int) {
            this@MainActivity.openInNewTab(url, tabIndex, closeCapsuleAfter = false, navigateImmediately = true)
        }
    }

    private fun tabAt(index: Int) = tabsModel.tabsStates.getOrNull(index)

    /** The tab the strip shows as selected, which differs from the shown one while another is previewed. */
    internal fun tabAtStripSelection() = tabAt(vb.vTabs.current)

    internal fun openInNewTab(url: String?, index: Int = 0, closeCapsuleAfter: Boolean = true, navigateImmediately: Boolean): WebEngine? {
        if (url == null) return null
        val tab = WebTabState(url = url, incognito = config.incognitoMode)
        tabsModel.tabsStates.add(index, tab)
        changeTab(tab, loadInitialUrl = false)
        if (tab.webEngine.getView() == null) return null
        if (navigateImmediately) navigate(url)
        if (closeCapsuleAfter && vb.rlActionBar.isVisible) closeCapsule()
        return tab.webEngine
    }

    /** Closes [tab] and shows a neighbour; the last tab is replaced by a new home tab. */
    internal fun closeTab(tab: WebTabState?) {
        if (tab == null) return
        val tabs = tabsModel.tabsStates
        val position = tabs.indexOf(tab)
        if (tabsModel.currentTab.value === tab) tabsModel.currentTab.value = null
        val neighbour = tabs.getOrNull(position - 1)?.takeIf { position > 0 } ?: tabs.getOrNull(position + 1)
        if (neighbour != null) changeTab(neighbour)
        else openInNewTab(settingsModel.homePage, 0, closeCapsuleAfter = true, navigateImmediately = true)
        tabsModel.onCloseTab(tab)
        closeCapsule()
    }

    internal fun changeTab(newTab: WebTabState, loadInitialUrl: Boolean = true) =
        tabsModel.changeTab(newTab, ::createWebView, vb.flWebViewContainer, TabWindow(this, newTab), loadInitialUrl)

    /** The tab's WebView, or null after telling the user that this device has no usable WebView. */
    internal fun createWebView(tab: WebTabState): View? {
        val view = try {
            tab.webEngine.getOrCreateView(this)
        } catch (error: Throwable) {
            Log.w(TAG, "WebView could not be created", error)
            showNoWebViewDialog()
            return null
        }
        config.userAgentString.value?.let { tab.webEngine.userAgentString = it }
        return view
    }

    private fun showNoWebViewDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.error)
            .setCancelable(false)
            .setMessage(R.string.err_webview_can_not_link)
            .setNegativeButton(R.string.exit) { _, _ -> finish() }
        val store = Intent(Intent.ACTION_VIEW, "market://details?id=$WEBVIEW_PACKAGE".toUri())
        if (packageManager.queryIntentActivities(store, 0).isNotEmpty()) {
            dialog.setPositiveButton(R.string.find_in_apps_store) { _, _ ->
                runCatching { startActivity(store) }.onFailure { Log.w(TAG, "Opening the app store failed", it) }
                finish()
            }
        }
        dialog.show()
    }

    /** The capsule follows the shown tab: Back and Forward availability and the blocker shield. */
    internal fun onWebViewUpdated(tab: WebTabState) {
        vb.vActionBar.backButton.isEnabled = tab.webEngine.canGoBack()
        vb.vActionBar.forwardButton.isEnabled = tab.webEngine.canGoForward()
        val blocking = tab.adblock ?: config.adBlockEnabled
        vb.vActionBar.adBlockButton.setImageResource(if (blocking) R.drawable.ic_adblock_on else R.drawable.ic_adblock_off)
        updateAdblockStatus(tab)
    }

    /** Commits the tab the strip shows as selected, or opens one when the strip points past the tabs. */
    private fun commitStripSelection() {
        val tab = tabAtStripSelection()
        when {
            tab == null -> openInNewTab(settingsModel.homePage, if (vb.vTabs.current < 0) 0 else tabsModel.tabsStates.size,
                closeCapsuleAfter = true, navigateImmediately = true)
            !tab.selected -> changeTab(tab)
        }
    }

    // Ad blocking and pop-ups

    private fun blockerStatusLabel(tab: WebTabState): Int {
        val initialized = ::adblockModel.isInitialized
        val indicator = blockerIndicator(
            tab.adblock ?: config.adBlockEnabled,
            initialized && adblockModel.hasActiveRules,
            initialized && adblockModel.clientLoading.value,
            if (initialized) adblockModel.updateResult.value else AdblockModel.UpdateResult.IDLE,
        )
        return when (indicator) {
            BlockerIndicator.PAUSED -> R.string.adblock_status_paused
            BlockerIndicator.LOADING -> R.string.adblock_status_loading
            BlockerIndicator.REFRESHING -> R.string.adblock_status_refreshing
            BlockerIndicator.ACTIVE -> R.string.adblock_status_active
            BlockerIndicator.PARTIAL -> R.string.adblock_status_partial
            BlockerIndicator.SAVED -> R.string.adblock_status_saved
            BlockerIndicator.UNAVAILABLE -> R.string.adblock_status_unavailable
        }
    }

    internal fun updateAdblockStatus(tab: WebTabState) {
        val state = getString(blockerStatusLabel(tab))
        vb.vActionBar.adBlockButton.contentDescription = getString(R.string.adblock_status_action_s, state, tab.blockedAds)
        vb.vActionBar.adBlockButton.isActivated =
            (tab.adblock ?: config.adBlockEnabled) && ::adblockModel.isInitialized && adblockModel.hasActiveRules
    }

    private fun showBlockerDetails() {
        val tab = tabsModel.currentTab.value ?: return
        val state = getString(blockerStatusLabel(tab))
        // A dialog message would hide the list, so the status is the title.
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.adblock_status_action_s, state, tab.blockedAds))
            .setItems(arrayOf(
                getString(R.string.toggle_ads_blocking),
                getString(R.string.block_popups_s, tab.blockedPopups.toString()),
            )) { _, which ->
                when (which) {
                    0 -> {
                        commitStripSelection()
                        toggleAdBlockForTab()
                    }
                    1 -> lifecycleScope.launch { showPopupBlockOptions() }
                }
            }
            .show()
    }

    /** Applies a change of the ad blocking setting to the shown tab. */
    fun refreshAdblockSettings() {
        tabsModel.currentTab.value?.let { tab ->
            tab.webEngine.onUpdateAdblockSetting(tab.adblock ?: config.adBlockEnabled)
            onWebViewUpdated(tab)
        }
    }

    /** Flips ad blocking for the shown tab only and reloads it; the lists load if this needs them. */
    private fun toggleAdBlockForTab() {
        val tab = tabsModel.currentTab.value ?: return
        val blocking = !(tab.adblock ?: config.adBlockEnabled)
        tab.adblock = blocking
        tab.webEngine.onUpdateAdblockSetting(blocking)
        onWebViewUpdated(tab)
        if (blocking && !adblockModel.hasActiveRules && !adblockModel.clientLoading.value) adblockModel.loadAdBlockList(true)
        refresh()
    }

    private suspend fun showPopupBlockOptions() {
        val tab = tabsModel.currentTab.value ?: return
        val site = tabsModel.findHostConfig(tab, false)
        val level = site?.popupBlockLevel ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE
        val host = site?.hostName ?: runCatching { URL(tab.url).host }.getOrDefault("")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.block_popups_s, host))
            .setSingleChoiceItems(R.array.popup_blocking_level, level) { dialog, choice ->
                lifecycleScope.launch {
                    tabsModel.changePopupBlockingLevel(choice, tab)
                    dialog.dismiss()
                }
            }
            .show()
    }

    internal fun onPopupAdBlocked(popup: WebTabState, opener: WebTabState) {
        if (popup.closed) return
        Log.i(TAG, "Blocked a pop-up ad window")
        if (tabsModel.currentTab.value === popup) closeTab(popup) else tabsModel.onCloseTab(popup)
        opener.blockedPopups++
        showBlockedPopup(opener, newTab = true)
    }

    /** Tells the user about a blocked pop-up or dialog, at most once per [BLOCKED_NOTICE_INTERVAL_MS]. */
    internal fun showBlockedPopup(tab: WebTabState, newTab: Boolean) {
        if (isDestroyed || tabsModel.currentTab.value !== tab) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockedNotice < BLOCKED_NOTICE_INTERVAL_MS) return
        lastBlockedNotice = now
        NotificationView.showBottomRight(vb.rlRoot, R.drawable.ic_block_popups,
            getString(if (newTab) R.string.new_tab_blocked else R.string.popup_dialog_blocked))
    }

    // Loading progress and downloads

    private val hideProgress = Runnable {
        val fade = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
        fade.onAnimationEnd { vb.progressBar.visibility = View.GONE }
        vb.progressBar.startAnimation(fade)
    }

    /** The thin bar shows loading; it fades a second after completion, or after 5 s without news. */
    internal fun showLoadProgress(progress: Int) {
        vb.progressBar.visibility = View.VISIBLE
        vb.progressBar.setProgress(progress, true)
        uiHandler.removeCallbacks(hideProgress)
        uiHandler.postDelayed(hideProgress, if (progress == 100) PROGRESS_DONE_MS else PROGRESS_STALL_MS)
    }

    private fun onDownloadStarted(fileName: String) {
        @Suppress("DEPRECATION") // Only names the folder in the message.
        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        toast(getString(R.string.download_started, folder.toString() + File.separator + fileName))
        openCapsule()
    }

    // Private mode

    fun toggleIncognitoMode() {
        toggleIncognitoMode(andSwitchProcess = true)
    }

    /** Leaving private mode erases its pages, cookies, storage, cache and tabs before the switch. */
    private fun toggleIncognitoMode(andSwitchProcess: Boolean) = lifecycleScope.launch {
        val entering = !config.incognitoMode
        vb.progressBarGeneric.visibility = View.VISIBLE
        if (!entering) {
            withContext(Dispatchers.IO) {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            WebEngineFactory.clearCache(this@MainActivity)
            tabsModel.onCloseAllTabs().join()
            tabsModel.currentTab.value = null
            viewModel.clearIncognitoData().join()
        }
        vb.progressBarGeneric.visibility = View.GONE
        config.incognitoMode = entering
        if (andSwitchProcess) switchProcess(entering)
    }

    /** Restarts as the other mode's activity in a new task; this process ends so the modes never share data. */
    private fun switchProcess(incognito: Boolean, intentToCopy: Intent? = null) {
        val target = Intent(this, if (incognito) IncognitoModeMainActivity::class.java else MainActivity::class.java)
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
            isFullscreen -> tabsModel.currentTab.value?.webEngine?.hideFullscreenView()
            overlayVisible -> closeCapsule()
            else -> openCapsule()
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            backNavigationEventsAdapter.dispatchSystemBackNavigationEvent()
        }
    }

    /** Back from the system, the remote, a gamepad's B or Escape: one event per press, whatever the channel. */
    internal val backNavigationEventsAdapter = BackNavigationEventsAdapter(
        onEmulatedBackEvent = { if (!hideSoftwareKeyboardIfVisible()) handleBackNavigation() },
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
                if (backNavigationEventsAdapter.dispatchKeyEvent(event)) return true
                val code = if (event.keyCode != 0) event.keyCode else event.scanCode
                if (code !in BackNavigationEventsAdapter.BACK_KEYS &&
                    ShortcutMgr.getInstance().handle(event, this@MainActivity, tabsModel.currentTab.value)) return true
                if (downLeavesTabStrip(overlayVisible, vb.vTabs.hasFocus(), event.keyCode, event.action, event.repeatCount)) {
                    closeCapsule()
                    return true
                }
                if (browserOwnsPointer() && event.keyCode in RemoteInput.NAVIGATION_KEYS &&
                    vb.flWebViewContainer.dispatchKeyEvent(event)) return true
                return views.dispatchKeyEvent(event)
            }

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                UserActivation.onTouchEvent(event)
                return views.dispatchTouchEvent(event)
            }

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                if (backNavigationEventsAdapter.dispatchGenericMotionEvent(event)) return true
                if (browserOwnsPointer() && RemoteInput.isNavigationSource(event) &&
                    vb.flWebViewContainer.dispatchGenericMotionEvent(event)) return true
                return views.dispatchGenericMotionEvent(event)
            }
        }
    }

    /** The page owns the screen: no capsule, pointer menu, text field or keyboard in front of it. */
    private fun browserOwnsPointer(): Boolean {
        if (!::vb.isInitialized || !window.decorView.hasWindowFocus()) return false
        val page = vb.flWebViewContainer
        return page.isShown && page.cursorEnabled && !page.cursorSuppressed && !page.isTextInputActive() &&
            !vb.vCursorMenu.isVisible && !vb.rlActionBar.isVisible && !keyboardVisible()
    }

    private fun keyboardVisible() =
        ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    /** Back while typing only closes the keyboard, as everywhere on Android. */
    private fun hideSoftwareKeyboardIfVisible(): Boolean {
        if (!keyboardVisible()) return false
        val target = currentFocus ?: window.decorView
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(target.windowToken, 0)
        return true
    }

    /** Back closes the innermost thing open: pointer menu, fullscreen, typing, capsule, grab mode; else opens the capsule. */
    @VisibleForTesting
    internal fun handleBackNavigation() {
        val pointer = vb.flWebViewContainer.cursorDrawerDelegate
        when {
            vb.vCursorMenu.isVisible -> vb.vCursorMenu.close()
            isFullscreen -> tabsModel.currentTab.value?.webEngine?.hideFullscreenView()
            vb.vActionBar.isEditingAddress -> vb.vActionBar.cancelAddressInput()
            overlayVisible -> closeCapsule()
            pointer.canHandleBackNavigation() -> pointer.handleBackNavigation()
            else -> {
                tabsModel.currentTab.value?.webEngine?.setVirtualCursorMode(true)
                backNavigationEventsAdapter.gameControllersLongPressBForBackNavigation = false
                openCapsule()
            }
        }
    }

    // The capsule over the page

    private var overlayVisible = false
    /** Bumped whenever the preview may change, so a slow picture load never shows a stale tab. */
    private var previewGeneration = 0
    private var focusedPreviewTab: WebTabState? = null
    private val showFocusedPreview = Runnable { lifecycleScope.launch { displayThumbnail(focusedPreviewTab) } }

    /**
     * Opens the capsule over the page with focus on Back (Home when Back is unavailable). The shown
     * tab stays live behind it; another tab selected in the strip is previewed by its picture.
     */
    @VisibleForTesting
    internal fun openCapsule() {
        if (isFullscreen) {
            tabsModel.currentTab.value?.webEngine?.hideFullscreenView()
            return
        }
        if (overlayVisible) return
        overlayVisible = true
        previewGeneration++
        vb.rlActionBar.animate().cancel()
        vb.ivMiniatures.animate().cancel()
        vb.ivMiniatures.visibility = View.INVISIBLE
        vb.ivMiniatures.setImageResource(0)
        vb.flWebViewContainer.cursorSuppressed = true
        // The first press on the page closes the capsule and reaches the page.
        vb.flWebViewContainer.onPageTouch = {
            if (vb.vActionBar.isEditingAddress) vb.vActionBar.cancelAddressInput()
            closeCapsule()
        }
        val previewed = tabAtStripSelection()
        vb.flWebViewContainer.visibility =
            if (previewed != null && previewed === tabsModel.currentTab.value) View.VISIBLE else View.INVISIBLE
        lifecycleScope.launch { displayThumbnail(previewed) }
        vb.vActionBar.dismissExtendedAddressBarMode()
        vb.rlActionBar.visibility = View.VISIBLE
        // History can change without a page load (pushState on single-page sites): refresh Back first.
        tabsModel.currentTab.value?.let(::onWebViewUpdated)
        // Focus is part of opening, not of the animation's end.
        if (!vb.vActionBar.backButton.requestFocus()) vb.vActionBar.homeButton.requestFocus()
        vb.rlActionBar.translationY = -vb.rlActionBar.height.toFloat()
        vb.rlActionBar.alpha = 0f
        vb.rlActionBar.animate().translationY(0f).alpha(1f).setDuration(OVERLAY_OPEN_MS)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /** Shows [tab] behind the capsule: the live page for the shown tab, its stored picture for another. */
    internal suspend fun displayThumbnail(tab: WebTabState?) {
        if (!overlayVisible || tabAtStripSelection() !== tab) return
        if (tab != null && tab === tabsModel.currentTab.value) {
            // The live WebView at full resolution, never a stretched small picture.
            previewGeneration++
            showPicture(null, placeholder = false)
            vb.flWebViewContainer.visibility = View.VISIBLE
            return
        }
        vb.flWebViewContainer.visibility = View.INVISIBLE
        if (tab == null) return showPicture(null, placeholder = true)
        val request = ++previewGeneration
        vb.ivMiniatures.setImageResource(0) // Never leave the previous site's picture up while loading.
        val picture = tab.thumbnail ?: withContext(Dispatchers.IO) { tab.loadThumbnail() }
        if (request != previewGeneration || !overlayVisible || tab.closed || tabAtStripSelection() !== tab) return
        showPicture(picture, placeholder = picture == null)
    }

    private fun showPicture(picture: Bitmap?, placeholder: Boolean) {
        vb.llMiniaturePlaceholder.visibility = if (placeholder) View.VISIBLE else View.INVISIBLE
        vb.ivMiniatures.visibility = if (picture == null) View.INVISIBLE else View.VISIBLE
        vb.ivMiniatures.setImageBitmap(picture)
    }

    /** Closes the capsule at once (it stops owning focus even if an animation was cut) and returns to the page. */
    @VisibleForTesting
    internal fun closeCapsule() {
        val wasVisible = overlayVisible
        overlayVisible = false
        vb.flWebViewContainer.onPageTouch = null
        vb.flWebViewContainer.cursorSuppressed = false
        previewGeneration++
        uiHandler.removeCallbacks(showFocusedPreview)
        focusedPreviewTab = null
        vb.vActionBar.dismissExtendedAddressBarMode()
        vb.rlActionBar.animate().cancel()
        vb.ivMiniatures.animate().cancel()
        vb.rlActionBar.visibility = View.INVISIBLE
        vb.rlActionBar.translationY = 0f
        vb.rlActionBar.alpha = 1f
        vb.ivMiniatures.translationY = 0f
        showPicture(null, placeholder = false)
        if (wasVisible) commitStripSelection()
        if (!vb.vCursorMenu.isVisible) {
            vb.flWebViewContainer.visibility = View.VISIBLE
            tabsModel.currentTab.value?.webEngine?.getView()?.requestFocus()
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
