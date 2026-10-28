package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.PopupMenu
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.model.HostConfig
import com.reiniertutoriales.vireolumatv.model.PopupGuard
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.DownloadUtils
import com.reiniertutoriales.vireolumatv.utils.toast
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import com.reiniertutoriales.vireolumatv.webengine.PagePoint
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import com.reiniertutoriales.vireolumatv.webengine.webview.UserActivation
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate
import kotlinx.coroutines.launch

/**
 * The browser window as seen by one [tab]'s page: address and title updates, downloads, pop-ups
 * and dialogs under the site's rules, ads, fullscreen, links to other apps and the pointer menu.
 */
internal class TabWindow(private val browser: MainActivity, private val tab: WebTabState) : PageWindow {
    private val vb get() = browser.vb
    private val tabs get() = browser.tabsModel
    private val ui get() = browser.uiHandler
    private val isShownTab get() = tabs.currentTab.value === tab

    override val activity: Activity get() = browser

    override fun openInNewTab(url: String, load: Boolean): PageEngine? {
        val current = tabs.tabsStates.indexOf(tabs.currentTab.value)
        return browser.openInNewTab(url, if (current == -1) tabs.tabsStates.size else current + 1, true, load)
    }

    // Downloads

    /** A download the page itself starts: only a fresh remote press may allow it, once. */
    override fun onPageDownload(url: String, userAgent: String?, contentDisposition: String, mimeType: String?, length: Long) {
        if (!UserActivation.consume()) {
            Log.i(TAG, "Download without a recent press ignored")
            return
        }
        startDownload(url, DownloadUtils.guessFileName(url, contentDisposition, mimeType), mimeType, userAgent, length)
    }

    override fun download(download: Download) = browser.downloads.request(download)

    /** The link menu's download: the user chose it; the name and type come from the address. */
    private fun downloadLink(url: String) {
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url))
        startDownload(url, DownloadUtils.guessFileName(url, null, type), type, tab.engine.userAgent, 0L)
    }

    private fun startDownload(url: String, name: String, mimeType: String?, userAgent: String?, length: Long) =
        download(Download(url, name, null, Download.OperationAfterDownload.NOP, mimeType, tab.url, userAgent, null, null, length))

    // Page state

    override fun onProgress(percent: Int) = browser.showLoadProgress(percent)

    override fun onTitle(title: String) {
        tab.title = title
        vb.tabStrip.onTabTitleUpdated(tab)
        browser.viewModel.onTabTitleUpdated(tab)
    }

    override fun onIcon(icon: Bitmap) = vb.tabStrip.onFavIconUpdated(tab)

    override fun requestPermissions(permissions: Array<String>): Int = browser.requestPagePermissions(permissions)

    override fun pickFile(intent: Intent): Boolean = try {
        browser.filePickerResult.launch(intent)
        true
    } catch (_: ActivityNotFoundException) {
        // Pickers often declare no specific types yet accept any: try once more with */*.
        try {
            browser.filePickerResult.launch(intent.setType("*/*"))
            true
        } catch (_: ActivityNotFoundException) {
            browser.applicationContext.toast(browser.getString(R.string.error_no_file_picker))
            false
        }
    }

    override fun interceptNavigation(url: String): Boolean {
        tab.lastLoadingUrl = url
        return when (browser.external.classify(url)) {
            ExternalApps.Navigation.LOAD -> false
            ExternalApps.Navigation.BLOCK -> true
            ExternalApps.Navigation.ASK_INTENT -> true.also { browser.runOnUiThread { browser.external.askToOpenIntentUrl(url) } }
            ExternalApps.Navigation.ASK_EXTERNAL -> true.also { browser.runOnUiThread { browser.external.askToOpenInApp(url) } }
        }
    }

    override fun onPageStarted(url: String?) {
        updateAddress(url)
        tab.blockedAds = 0
        tab.blockedPopups = 0
        ui.removeCallbacks(capturePreview)
        // After the address is updated, so redirects look up the right site.
        browser.lifecycleScope.launch { tabs.findHostConfig(tab, false) }
        browser.onWebViewUpdated(tab)
    }

    override fun onPageFinished(url: String?) {
        if (tabs.currentTab.value == null) return
        browser.onWebViewUpdated(tab)
        updateAddress(url)
        tabs.tabsStates.forEach { if (it !== tab) it.thumbnail = null }
        // Drawing a WebView in software is slow on TV chips: one preview once the page has settled,
        // not one per redirect, frame or single-page navigation.
        ui.removeCallbacks(capturePreview)
        ui.postDelayed(capturePreview, PREVIEW_DELAY_MS)
    }

    private fun updateAddress(reported: String?) {
        (tab.engine.url ?: reported)?.let { tab.url = it }
        if (browser.tabAtStripSelection() === tab) vb.controlsRow.setAddressBoxText(tab.url)
    }

    private val capturePreview = Runnable {
        if (browser.isFinishing || browser.isDestroyed || tab.closed || !isShownTab ||
            tab.engine.view?.isAttachedToWindow != true) return@Runnable
        browser.lifecycleScope.launch {
            val preview = tab.engine.preview(tab.thumbnail) ?: return@launch
            tab.updateThumbnail(browser, preview)
            if (vb.controlsOverlay.isVisible && isShownTab) browser.displayThumbnail(tab)
        }
    }

    override fun onCertificateError() = vb.controlsRow.setAddressBoxTextColor(browser.getColor(R.color.error))

    override fun onRendererGone() {
        if (isShownTab) browser.changeTab(tab)
    }

    override fun onScale(scale: Float) {
        tab.scale = scale
    }

    override fun onVisited(url: String) {
        val shown = tabs.currentTab.value ?: return
        if (!browser.config.incognitoMode) browser.viewModel.logVisitedHistory(shown.title, url, shown.faviconHash)
    }

    // Ads, dialogs and pop-ups

    override fun isAd(url: Uri, requestType: String?, page: Uri): Boolean = browser.adblockModel.isAd(url, requestType, page)

    override fun adRedirect(url: Uri, requestType: String?, page: Uri): String? = browser.adblockModel.redirectFor(url, requestType, page)

    override val blocksAds: Boolean get() = tab.adblock ?: browser.config.adBlockEnabled

    /** The home page may show dialogs; other pages follow their site's pop-up level. */
    override val blocksDialogs: Boolean get() = tab.url != Config.HOME_PAGE_URL && blocksNewWindow(dialog = true, userGesture = false)

    private fun blocksNewWindow(dialog: Boolean, userGesture: Boolean): Boolean = when (tabs.popupBlockingLevel(tab)) {
        HostConfig.POPUP_BLOCK_NONE -> false
        HostConfig.POPUP_BLOCK_DIALOGS -> dialog
        HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS -> dialog || !userGesture
        else -> true
    }

    override fun isTabUnderAd(url: Uri, page: Uri?): Boolean {
        if (!blocksAds || !browser.adblockModel.isTabUnderAd(url, page)) return false
        Log.i(TAG, "Blocked tab-under navigation to ${url.host}")
        tab.blockedPopups++
        browser.showBlockedPopup(tab, newTab = true)
        return true
    }

    override fun onAdsBlocked(count: Int) {
        if (!blocksAds || count <= 0) return
        tab.blockedAds += count
        if (isShownTab) browser.updateAdblockStatus(tab)
    }

    override fun onDialogBlocked(newTab: Boolean) {
        tab.blockedPopups++
        browser.runOnUiThread { browser.showBlockedPopup(tab, newTab) }
    }

    /**
     * A page asks for a new window. It is refused by the site's pop-up level, or while the page plays
     * media (on video sites that is a popunder). Otherwise it opens as a tab next to its opener; with
     * ad blocking the opener stays alive until the pop-up's destination is known to be no ad.
     */
    override fun createWindow(dialog: Boolean, userGesture: Boolean): View? {
        if (blocksNewWindow(dialog, userGesture)) {
            onDialogBlocked(!dialog)
            return null
        }
        val opener = tabs.currentTab.value ?: return null
        if (blocksAds && opener.mediaStarted) {
            // The page gets an inert window (window_open_decoy.js); the player is never replaced.
            Log.i(TAG, "Blocked a new window from a page playing media")
            onDialogBlocked(!dialog)
            return null
        }
        val popup = WebTabState(incognito = browser.config.incognitoMode).apply { this.opener = opener }
        val view = browser.createWebView(popup) ?: return null
        tabs.tabsStates.add(tabs.tabsStates.indexOf(opener) + 1, popup)
        val show = Runnable {
            if (popup.closed) return@Runnable
            if (browser.isFinishing || browser.isDestroyed || tabs.currentTab.value !== opener) tabs.onCloseTab(popup)
            else browser.changeTab(popup, loadInitialUrl = false)
        }
        if (blocksAds) {
            val openerUrl = runCatching { opener.url.toUri() }.getOrNull()
            val model = browser.adblockModel
            popup.popupGuard = PopupGuard(
                isAd = { url -> model.isPopupAd(url, openerUrl) },
                onBlocked = { ui.post { browser.onPopupAdBlocked(popup, opener) } },
                onAllowed = { ui.post(show) },
            )
            ui.postDelayed({ popup.popupGuard?.expire() }, POPUP_DECISION_TIMEOUT_MS)
        } else {
            // Let Chromium finish handing the window over before the opener's WebView may go.
            vb.pageContainer.post(show)
        }
        return view
    }

    override fun closeWindow(view: Any) {
        tabs.tabsStates.firstOrNull { it.engine.ownsView(view) }?.let(browser::closeTab)
    }

    // Fullscreen

    override fun onEnterFullscreen() {
        val window = browser.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION") // The insets API needs API 30; these flags work on every supported TV.
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        browser.isFullscreen = true
    }

    override fun onExitFullscreen(restoreControls: Boolean) {
        val window = browser.window
        if (!browser.config.keepScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        browser.isFullscreen = false
        if (restoreControls && isShownTab && !browser.isFinishing && !browser.isDestroyed) browser.openCapsule()
    }

    // Pointer menu and link actions

    override fun openPointerMenu(cursor: CursorDrawerDelegate, target: PagePoint) {
        ui.post { vb.pointerMenu.show(tab, this, cursor, target, browser.backNavigationEventsAdapter) }
    }

    /** The link actions menu at the pointer: what applies depends on whether a web link is under it. */
    override fun openLinkActions(target: PagePoint) {
        val url = target.link?.removeSurrounding("\"")
        val anchor = View(browser)
        vb.pageContainer.addView(anchor, FrameLayout.LayoutParams(1, 1).apply { setMargins(target.x, target.y, 0, 0) })
        PopupMenu(browser, anchor, Gravity.BOTTOM).apply {
            val actions = LinkAction.available(url)
            actions.forEachIndexed { index, action -> menu.add(0, index, index, action.label) }
            setOnMenuItemClickListener { item ->
                when (actions[item.itemId]) {
                    LinkAction.RELOAD -> tab.engine.reload()
                    LinkAction.OPEN_TAB -> url?.let { openInNewTab(it, load = true) }
                    LinkAction.OPEN_APP -> url?.let(browser.external::openInApp)
                    LinkAction.DOWNLOAD -> url?.let(::downloadLink)
                    LinkAction.COPY -> url?.let(browser.external::copyToClipboard)
                    LinkAction.SHARE -> url?.let(browser.external::share)
                }
                true
            }
            setOnDismissListener { vb.pageContainer.removeView(anchor) }
            show()
        }
    }

    private companion object {
        const val TAG = "TabWindow"
        const val PREVIEW_DELAY_MS = 1500L
        // A pop-up that has requested no page by then (about:blank and document.write) is shown.
        const val POPUP_DECISION_TIMEOUT_MS = 1200L
    }
}
