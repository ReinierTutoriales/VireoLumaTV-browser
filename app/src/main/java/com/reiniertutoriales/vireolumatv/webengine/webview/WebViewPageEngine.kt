package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.adblock.AdblockRequestClassifier
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.BundleBytes
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import com.reiniertutoriales.vireolumatv.webengine.EngineBackend
import com.reiniertutoriales.vireolumatv.webengine.MediaCommand
import com.reiniertutoriales.vireolumatv.webengine.PagePoint
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import com.reiniertutoriales.vireolumatv.widgets.cursor.PointerController
import com.reiniertutoriales.vireolumatv.widgets.cursor.PointerLayout
import org.json.JSONTokener

/**
 * [tab]'s engine on Android WebView. It owns the tab's [BrowserWebView] while one exists and is the
 * link between the page and the browser [window] it is shown in, which is null while detached.
 * Page fullscreen (video) replaces the WebView in the window and is always undone on detach.
 */
class WebViewPageEngine(val tab: BrowserTab) : PageEngine, PointerController.Callback {
    private var webView: BrowserWebView? = null
    /** The window the tab is shown in; JavaScript bridge calls use it as well. */
    internal var window: PageWindow? = null
    private var container: PointerLayout? = null
    private var fullscreenView: View? = null
    /** The page's pointer mode before fullscreen, restored after it (D-pad mode stays D-pad mode). */
    private var pointerBeforeFullscreen = true
    /** Pending Android permission requests: code to "is it the location request". */
    private val permissionRequests = HashMap<Int, Pair<Boolean, Long>>()
    /** Certificate approval and blob downloads; [BrowserWebView] decides which channel reaches them. */
    internal val privileged = PrivilegedPageActions(this)

    private val adblockEnabled get() = tab.adblock ?: AppContext.config.adBlockEnabled

    override val url: String? get() = webView?.url

    override var userAgent: String? = null
        set(value) {
            field = value
            // Null restores the WebView's own agent, on a live WebView too.
            webView?.settings?.userAgentString = value
        }

    override val view: View? get() = webView
    override val canGoBack get() = webView?.let { meaningfulBackOffset(it) != null } == true
    override val canGoForward get() = webView?.canGoForward() == true

    override fun ownsView(view: Any): Boolean = view === webView

    override fun saveHistory(): Any? = Bundle().takeIf { webView?.saveState(it) != null }

    override fun restoreHistory(history: Any): Boolean {
        require(history is Bundle) { "WebView history is a Bundle" }
        return webView?.restoreState(history) != null
    }

    override fun readHistory(bytes: ByteArray): Any? = BundleBytes.decode(bytes)

    override fun createView(context: Context): View =
        webView ?: BrowserWebView(context, this).also {
            // Before the first navigation, with this tab's choice even before it is attached.
            it.onBlockerToggled(adblockEnabled)
            webView = it
        }

    override fun load(url: String) { webView?.loadUrl(url) }
    override fun reload() { webView?.reload() }
    override fun back() { webView?.let { view -> meaningfulBackOffset(view)?.let(view::goBackOrForward) } }
    override fun forward() { webView?.goForward() }
    override fun zoom(closer: Boolean) { webView?.run { if (closer) zoomIn() else zoomOut() } }
    override fun setOnline(online: Boolean) { webView?.setNetworkAvailable(online) }
    override fun onFilePicked(resultCode: Int, data: Intent?) { webView?.onFilePicked(resultCode, data) }
    override fun setAdBlocking(enabled: Boolean) { webView?.onBlockerToggled(enabled) }
    override suspend fun preview(reuse: Bitmap?): Bitmap? = webView?.drawPreview(reuse)

    override fun resume() {
        webView?.apply {
            onBlockerToggled(adblockEnabled)
            resumeTimers()
            onResume()
        }
    }

    override fun pause() {
        webView?.apply {
            onPause()
            // onPause() alone leaves JavaScript timers running.
            pauseTimers()
        }
    }

    /** Calls the function generic_injects.js defines for the page's main video. */
    override fun media(command: MediaCommand) {
        val call = when (command) {
            MediaCommand.PLAY_PAUSE -> "vireoLumaTVTogglePlayback()"
            MediaCommand.STOP -> "vireoLumaTVStopPlayback()"
            MediaCommand.REWIND -> "vireoLumaTVRewind()"
            MediaCommand.FAST_FORWARD -> "vireoLumaTVFastForward()"
        }
        webView?.evaluateJavascript(call, null)
    }

    override fun attach(window: PageWindow, parent: ViewGroup) {
        val view = checkNotNull(webView) { "The WebView must exist before it is attached" }
        val cursor = parent as PointerLayout
        this.window = window
        container = cursor
        cursor.removeAllViews()
        cursor.addView(view)
        cursor.resetInput()
        cursor.pointerEnabled = true
        view.usePointer(true)
        view.isFocusableInTouchMode = true
        view.requestFocus()
        cursor.pointerController.callback = this
        cursor.pointerController.fadeIn()
        resume()
    }

    override fun detach(destroyView: Boolean) {
        exitFullscreen(restoreBrowserControls = false)
        pause()
        (webView?.parent as? ViewGroup)?.removeView(webView)
        window = null
        container?.pointerController?.callback = null
        container = null
        if (destroyView) {
            permissionRequests.clear()
            webView?.destroy()
            webView = null
        }
    }

    override fun releaseHiddenView() {
        val view = webView ?: return
        if (view.isAttachedToWindow) return
        exitFullscreen(restoreBrowserControls = false)
        view.destroy()
        webView = null
    }

    override fun closeFullscreen() {
        webView?.hideCustomView()
        if (fullscreenView != null) exitFullscreen(restoreBrowserControls = true)
    }

    override fun setPointerMode(enabled: Boolean) {
        container?.pointerEnabled = enabled
        if (enabled) container?.pointerController?.fadeIn()
        webView?.usePointer(enabled)
    }

    override fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        val (location, generation) = permissionRequests.remove(requestCode) ?: return false
        webView?.onPermissionsResult(permissions, grantResults, location, generation)
        return true
    }

    /** Pointer long press: asks the page for the link under it, then opens the pointer menu there. */
    override fun onLongPress(x: Int, y: Int) {
        val view = webView ?: return
        view.evaluateJavascript(PageScripts.HOLD_SCRIPT) { json ->
            if (webView !== view || container == null) return@evaluateJavascript
            // The answer is JSON, not a raw address.
            val link = runCatching { JSONTokener(json).nextValue() as? String }.getOrNull()
            showPointerMenu(view.currentOriginalUrl.toString(), link, x, y)
        }
    }

    private fun showPointerMenu(baseUrl: String?, href: String?, x: Int, y: Int) {
        val cursor = container ?: return
        window?.openPointerMenu(cursor.pointerController, PagePoint(baseUrl, href, x, y))
    }

    // The page's side, called by BrowserWebView and its clients.

    /** Element hiding and scriptlets for a document of this tab, any frame (JavaBridge thread). */
    fun pageFilters(url: String): String = if (adblockEnabled) pageFiltersProvider?.invoke(url).orEmpty() else ""

    fun hiddenSelectors(url: String, classes: String, ids: String): String =
        if (adblockEnabled) hiddenSelectorsProvider?.invoke(url, classes, ids) ?: "[]" else "[]"

    internal fun onPageStarted(url: String?) {
        tab.mediaStarted = false
        window?.onPageStarted(url)
    }

    internal fun requestPermissions(permissions: Array<String>, location: Boolean) {
        val generation = webView?.permissions?.generation ?: return
        val code = window?.requestPermissions(permissions) ?: return
        permissionRequests[code] = location to generation
    }

    internal fun isAd(request: WebResourceRequest, page: Uri): Boolean = classified(request, page) { type, context ->
        window?.isAd(request.url, type, context) ?: false
    }

    internal fun adRedirect(request: WebResourceRequest, page: Uri): String? = classified(request, page) { type, context ->
        window?.adRedirect(request.url, type, context)
    }

    private inline fun <T> classified(request: WebResourceRequest, page: Uri, decide: (String?, Uri) -> T): T = decide(
        AdblockRequestClassifier.classify(request.url, request.requestHeaders, request.isForMainFrame),
        AdblockRequestClassifier.pageContext(request.url, page, request.isForMainFrame),
    )

    internal fun onCreateWindow(dialog: Boolean, userGesture: Boolean): BrowserWebView? =
        window?.createWindow(dialog, userGesture) as? BrowserWebView

    internal fun onPopupNavigation(url: Uri): Boolean = tab.popupGuard?.onNavigation(url) ?: false

    internal fun onPopupPageFinished(url: String?) { tab.popupGuard?.onPageFinished(url) }

    internal fun isTabUnderAd(url: Uri): Boolean = window?.isTabUnderAd(url, webView?.currentOriginalUrl) ?: false

    /** The renderer died: the dead view goes, and the tab reloads its address instead of its history. */
    internal fun onRenderProcessGone(): Boolean {
        Log.i(TAG, "Renderer gone (fullscreen: ${fullscreenView != null})")
        exitFullscreen(restoreBrowserControls = false)
        val dead = webView ?: return true
        tab.rendererLost = true
        (dead.parent as? ViewGroup)?.removeView(dead)
        dead.destroy()
        webView = null
        window?.onRendererGone()
        return true
    }

    internal fun enterFullscreen(view: View) {
        if (fullscreenView != null) {
            Log.i(TAG, "Fullscreen refused: one is already shown")
            (view.parent as? ViewGroup)?.removeView(view)
            return
        }
        pointerBeforeFullscreen = container?.pointerEnabled ?: true
        window?.onEnterFullscreen()
        webView?.visibility = View.GONE
        webView?.usePointer(false)
        container?.pointerEnabled = false
        (view.parent as? ViewGroup)?.removeView(view)
        container?.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fullscreenView = view
        view.requestFocus()
    }

    /**
     * Puts the page back. With [restoreBrowserControls] the window restores its controls, navigation
     * state and focus in one step, so no separate visibility change can race a later menu transition.
     */
    internal fun exitFullscreen(restoreBrowserControls: Boolean) {
        // Idempotent: WebView may report the end of fullscreen after the browser already closed it.
        val shown = fullscreenView ?: return
        fullscreenView = null
        (shown.parent as? ViewGroup)?.removeView(shown)
        webView?.visibility = View.VISIBLE
        webView?.usePointer(pointerBeforeFullscreen)
        container?.pointerEnabled = pointerBeforeFullscreen
        window?.onExitFullscreen(restoreBrowserControls)
    }

    companion object {
        private const val TAG = "WebViewPageEngine"

        /** Installed by the window owner (the ad block model); read from the JavaBridge thread. */
        @Volatile var pageFiltersProvider: ((String) -> String)? = null
        @Volatile var hiddenSelectorsProvider: ((String, String, String) -> String)? = null

        /** The WebView backend the app installs in Engines at start-up. */
        val Backend = object : EngineBackend {
            override fun create(tab: BrowserTab): PageEngine = WebViewPageEngine(tab)

            // The disk cache belongs to the shared WebView profile, so a short-lived WebView clears it.
            override suspend fun clearCache(context: Context) {
                val webView = WebView(context)
                try {
                    webView.clearCache(true)
                } finally {
                    webView.destroy()
                }
            }

            override val version: String
                get() {
                    val webViewPackage = WebViewCompat.getCurrentWebViewPackage(AppContext.context)
                    return (webViewPackage?.packageName ?: "unknown") + ":" + (webViewPackage?.versionName ?: "unknown")
                }
        }
    }
}


/**
 * The TV Back key moves to the previous distinct history URL instead of replaying
 * consecutive reloads or duplicate same-URL history entries. Hash changes remain
 * distinct, since they can represent in-page navigation.
 * A missing WebView history entry must not be skipped speculatively.
 */
internal fun meaningfulBackOffset(view: WebView): Int? {
    val history = view.copyBackForwardList()
    val current = history.currentIndex
    if (current <= 0) return null
    return meaningfulBackOffset(current) { index -> history.getItemAtIndex(index)?.url }
}

/** Returns a negative WebView history offset; null means no distinct older URL. */
internal fun meaningfulBackOffset(urls: List<String?>, currentIndex: Int): Int? =
    if (currentIndex in urls.indices) meaningfulBackOffset(currentIndex, urls::get) else null

/** Scan only as far as the previous distinct entry, without allocating a history URL list. */
private inline fun meaningfulBackOffset(currentIndex: Int, urlAt: (Int) -> String?): Int? {
    if (currentIndex <= 0) return null
    val currentUrl = urlAt(currentIndex) ?: return -1
    for (index in currentIndex - 1 downTo 0) {
        val previousUrl = urlAt(index) ?: return index - currentIndex
        if (previousUrl != currentUrl) return index - currentIndex
    }
    return null
}
