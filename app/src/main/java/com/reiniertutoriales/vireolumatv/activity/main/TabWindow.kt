package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
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
import com.reiniertutoriales.vireolumatv.webengine.WebEngine
import com.reiniertutoriales.vireolumatv.webengine.WebEngineWindowProviderCallback
import com.reiniertutoriales.vireolumatv.webengine.webview.UserActivation
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate
import kotlinx.coroutines.launch
import java.io.InputStream

/**
 * The browser window as seen by one [tab]'s page: address and title updates, downloads, pop-ups
 * and dialogs under the site's rules, ads, fullscreen, links to other apps and the pointer menu.
 */
internal class TabWindow(private val browser: MainActivity, private val tab: WebTabState) : WebEngineWindowProviderCallback {
    private val vb get() = browser.vb
    private val tabs get() = browser.tabsModel
    private val ui get() = browser.uiHandler
    private val adblock get() = tab.adblock ?: browser.config.adBlockEnabled
    private val isShownTab get() = tabs.currentTab.value === tab

    override fun getActivity(): Activity = browser

    override fun onOpenInNewTabRequested(url: String, navigateImmediately: Boolean): WebEngine? {
        val current = tabs.tabsStates.indexOf(tabs.currentTab.value)
        return browser.openInNewTab(url, if (current == -1) tabs.tabsStates.size else current + 1, true, navigateImmediately)
    }

    // Downloads

    /** The link menu's download: the name and type come from the address. */
    override fun onDownloadRequested(url: String) {
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url))
        onDownloadRequested(url, tab.url, url.toUri().lastPathSegment, tab.webEngine.userAgentString, type)
    }

    override fun onDownloadRequested(
        url: String, referer: String, originalDownloadFileName: String?, userAgent: String?, mimeType: String?,
        operationAfterDownload: Download.OperationAfterDownload, base64BlobData: String?, stream: InputStream?, size: Long,
        contentDisposition: String?,
    ) {
        val name = DownloadUtils.guessFileName(url, contentDisposition, mimeType)
        browser.downloads.request(Download(url, name, null, operationAfterDownload, mimeType, referer, userAgent, base64BlobData, stream, size))
    }

    /** A download the page itself starts: only a fresh remote press may authorize it, once. */
    override fun onDownloadRequested(url: String, userAgent: String?, contentDisposition: String, mimetype: String?, contentLength: Long) {
        if (!UserActivation.consume()) {
            Log.i(TAG, "Download without a recent press ignored")
            return
        }
        onDownloadRequested(url = url, referer = tab.url, originalDownloadFileName = null, userAgent = userAgent,
            mimeType = mimetype, size = contentLength, contentDisposition = contentDisposition)
    }

    // Page state

    override fun onProgressChanged(newProgress: Int) = browser.showLoadProgress(newProgress)

    override fun onReceivedTitle(title: String) {
        tab.title = title
        vb.vTabs.onTabTitleUpdated(tab)
        browser.viewModel.onTabTitleUpdated(tab)
    }

    override fun onReceivedIcon(icon: Bitmap) = vb.vTabs.onFavIconUpdated(tab)

    override fun requestPermissions(array: Array<String>): Int = browser.requestPagePermissions(array)

    override fun onShowFileChooser(intent: Intent): Boolean = try {
        browser.filePickerResult.launch(intent)
        true
    } catch (_: ActivityNotFoundException) {
        // Pickers often declare no specific types yet accept any: try once more with */*.
        try {
            browser.filePickerResult.launch(intent.setType("*/*"))
            true
        } catch (_: ActivityNotFoundException) {
            browser.applicationContext.toast(browser.getString(R.string.err_cant_open_file_chooser))
            false
        }
    }

    override fun shouldOverrideUrlLoading(url: String): Boolean {
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
        (tab.webEngine.url ?: reported)?.let { tab.url = it }
        if (browser.tabAtStripSelection() === tab) vb.vActionBar.setAddressBoxText(tab.url)
    }

    private val capturePreview = Runnable {
        if (browser.isFinishing || browser.isDestroyed || tab.closed || !isShownTab ||
            tab.webEngine.getView()?.isAttachedToWindow != true) return@Runnable
        browser.lifecycleScope.launch {
            val preview = tab.webEngine.renderThumbnail(tab.thumbnail) ?: return@launch
            tab.updateThumbnail(browser, preview)
            if (vb.rlActionBar.isVisible && isShownTab) browser.displayThumbnail(tab)
        }
    }

    override fun onPageCertificateError(url: String?) = vb.vActionBar.setAddressBoxTextColor(Color.RED)

    override fun onRenderProcessGone() {
        if (isShownTab) browser.changeTab(tab)
    }

    override fun onScaleChanged(oldScale: Float, newScale: Float) {
        tab.scale = newScale
    }

    override fun onVisited(url: String) {
        val shown = tabs.currentTab.value ?: return
        if (!browser.config.incognitoMode) browser.viewModel.logVisitedHistory(shown.title, url, shown.faviconHash)
    }

    // Ads, dialogs and pop-ups

    override fun isAd(url: Uri, requestType: String?, baseUri: Uri): Boolean? = browser.adblockModel.isAd(url, requestType, baseUri)

    override fun adRedirect(url: Uri, requestType: String?, baseUri: Uri): String? = browser.adblockModel.redirectFor(url, requestType, baseUri)

    override fun isAdBlockingEnabled(): Boolean = adblock

    /** The home page may show dialogs; other pages follow their site's pop-up level. */
    override fun isDialogsBlockingEnabled(): Boolean = tab.url != Config.HOME_PAGE_URL && blocksNewWindow(dialog = true, userGesture = false)

    private fun blocksNewWindow(dialog: Boolean, userGesture: Boolean): Boolean = when (tabs.popupBlockingLevel(tab)) {
        HostConfig.POPUP_BLOCK_NONE -> false
        HostConfig.POPUP_BLOCK_DIALOGS -> dialog
        HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS -> dialog || !userGesture
        else -> true
    }

    override fun isTabUnderAd(url: Uri, page: Uri?): Boolean {
        if (!adblock || !browser.adblockModel.isTabUnderAd(url, page)) return false
        Log.i(TAG, "Blocked tab-under navigation to ${url.host}")
        tab.blockedPopups++
        browser.showBlockedPopup(tab, newTab = true)
        return true
    }

    override fun onBlockedAds(count: Int) {
        if (!adblock || count <= 0) return
        tab.blockedAds += count
        if (isShownTab) browser.updateAdblockStatus(tab)
    }

    override fun onBlockedDialog(newTab: Boolean) {
        tab.blockedPopups++
        browser.runOnUiThread { browser.showBlockedPopup(tab, newTab) }
    }

    /**
     * A page asks for a new window. It is refused by the site's pop-up level, or while the page plays
     * media (on video sites that is a popunder). Otherwise it opens as a tab next to its opener; with
     * ad blocking the opener stays alive until the pop-up's destination is known to be no ad.
     */
    override fun onCreateWindow(dialog: Boolean, userGesture: Boolean): View? {
        if (blocksNewWindow(dialog, userGesture)) {
            onBlockedDialog(!dialog)
            return null
        }
        val opener = tabs.currentTab.value ?: return null
        if (adblock && opener.mediaStarted) {
            // The page gets an inert window (window_open_decoy.js); the player is never replaced.
            Log.i(TAG, "Blocked a new window from a page playing media")
            onBlockedDialog(!dialog)
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
        if (adblock) {
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
            vb.flWebViewContainer.post(show)
        }
        return view
    }

    override fun closeWindow(internalRepresentation: Any) {
        tabs.tabsStates.firstOrNull { it.webEngine.isSameSession(internalRepresentation) }?.let(browser::closeTab)
    }

    // Other apps

    override fun onCopyTextToClipboardRequested(url: String) = browser.external.copyToClipboard(url)

    override fun onShareUrlRequested(url: String) = browser.external.share(url)

    override fun onOpenInExternalAppRequested(url: String) = browser.external.openInApp(url)

    // Fullscreen

    override fun onPrepareForFullscreen() {
        val window = browser.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION") // The insets API needs API 30; these flags work on every supported TV.
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        browser.isFullscreen = true
    }

    override fun onExitFullscreen() {
        val window = browser.window
        if (!browser.config.keepScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        browser.isFullscreen = false
    }

    override fun onRestoreBrowserControlsAfterFullscreen() {
        if (isShownTab && !browser.isFinishing && !browser.isDestroyed) browser.openCapsule()
    }

    // Pointer menu and link actions

    override fun onContextMenu(
        cursorDrawer: CursorDrawerDelegate, baseUri: String?, linkUri: String?, srcUri: String?,
        title: String?, altText: String?, textContent: String?, x: Int, y: Int,
    ) {
        ui.post {
            vb.vCursorMenu.show(tab, this, cursorDrawer, baseUri, linkUri, srcUri, title, altText, textContent, x, y,
                browser.backNavigationEventsAdapter)
        }
    }

    /** The link actions menu at the pointer: what applies depends on whether a web link is under it. */
    override fun suggestActionsForLink(
        baseUri: String?, linkUri: String?, srcUri: String?, title: String?, altText: String?, textContent: String?, x: Int, y: Int,
    ) {
        val url = (linkUri ?: srcUri)?.removeSurrounding("\"")
        val web = url != null && (url.startsWith("http://") || url.startsWith("https://"))
        val anchor = View(browser)
        vb.flWebViewContainer.addView(anchor, FrameLayout.LayoutParams(1, 1).apply { setMargins(x, y, 0, 0) })
        PopupMenu(browser, anchor, Gravity.BOTTOM).apply {
            inflate(R.menu.menu_link)
            menu.findItem(R.id.miOpenInNewTab).isVisible = web
            menu.findItem(R.id.miOpenInExternalApp).isVisible = web
            menu.findItem(R.id.miDownload).isVisible = web
            menu.findItem(R.id.miCopyToClipboard).isVisible = url != null
            menu.findItem(R.id.miShare).isVisible = url != null
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.miRefreshPage -> tab.webEngine.reload()
                    R.id.miOpenInNewTab -> url?.let { onOpenInNewTabRequested(it, true) }
                    R.id.miOpenInExternalApp -> url?.let(::onOpenInExternalAppRequested)
                    R.id.miDownload -> url?.let { onDownloadRequested(it) }
                    R.id.miCopyToClipboard -> url?.let(::onCopyTextToClipboardRequested)
                    R.id.miShare -> url?.let(::onShareUrlRequested)
                }
                true
            }
            setOnDismissListener { vb.flWebViewContainer.removeView(anchor) }
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
