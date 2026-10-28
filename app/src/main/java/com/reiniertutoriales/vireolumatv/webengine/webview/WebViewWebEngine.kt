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
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.BundleBytes
import com.reiniertutoriales.vireolumatv.webengine.WebEngine
import com.reiniertutoriales.vireolumatv.webengine.WebEngineProviderCallback
import com.reiniertutoriales.vireolumatv.webengine.PagePoint
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorLayout
import org.json.JSONTokener

/**
 * [tab]'s engine on Android WebView. It owns the tab's [WebViewEx] while one exists and is the
 * link between the page and the browser [window] it is shown in, which is null while detached.
 * Page fullscreen (video) replaces the WebView in the window and is always undone on detach.
 */
class WebViewWebEngine(val tab: WebTabState) : WebEngine, CursorDrawerDelegate.Callback {
    private var webView: WebViewEx? = null
    /** The window the tab is shown in; JavaScript bridge calls use it as well. */
    internal var window: PageWindow? = null
    private var container: CursorLayout? = null
    private var fullscreenView: View? = null
    /** Pending Android permission requests: code to "is it the location request". */
    private val permissionRequests = HashMap<Int, Boolean>()
    internal val jsInterface = AndroidJSInterface(this)

    private val adblockEnabled get() = tab.adblock ?: AppContext.provideConfig().adBlockEnabled

    override val url: String? get() = webView?.url

    override var userAgentString: String? = null
        set(value) {
            field = value
            // Null restores the WebView's own agent, on a live WebView too.
            webView?.settings?.userAgentString = value
        }

    override fun isSameSession(internalRepresentation: Any): Boolean = internalRepresentation === webView

    override fun saveState(): Any? = Bundle().takeIf { webView?.saveState(it) != null }

    override fun restoreState(savedInstanceState: Any): Boolean {
        require(savedInstanceState is Bundle) { "WebView state is a Bundle" }
        return webView?.restoreState(savedInstanceState) != null
    }

    override fun stateFromBytes(bytes: ByteArray): Any? = BundleBytes.decode(bytes)

    override fun getView(): View? = webView

    override fun getOrCreateView(activityContext: Context): View =
        webView ?: WebViewEx(activityContext, this).also {
            // Before the first navigation, with this tab's choice even before it is attached.
            it.onUpdateAdblockSetting(adblockEnabled)
            webView = it
        }

    override fun loadUrl(url: String) { webView?.loadUrl(url) }
    override fun reload() { webView?.reload() }
    override fun canGoBack() = webView?.canGoBack() == true
    override fun goBack() { webView?.goBack() }
    override fun canGoForward() = webView?.canGoForward() == true
    override fun goForward() { webView?.goForward() }
    override fun zoomIn() { webView?.zoomIn() }
    override fun zoomOut() { webView?.zoomOut() }
    override fun setNetworkAvailable(connected: Boolean) { webView?.setNetworkAvailable(connected) }
    override fun onFilePicked(resultCode: Int, data: Intent?) { webView?.onFilePicked(resultCode, data) }
    override fun onUpdateAdblockSetting(newState: Boolean) { webView?.onUpdateAdblockSetting(newState) }
    override suspend fun renderThumbnail(bitmap: Bitmap?): Bitmap? = webView?.renderThumbnail(bitmap)

    override fun onResume() {
        webView?.apply {
            onUpdateAdblockSetting(adblockEnabled)
            resumeTimers()
            onResume()
        }
    }

    override fun onPause() {
        webView?.apply {
            onPause()
            // onPause() alone leaves JavaScript timers running.
            pauseTimers()
        }
    }

    // Media keys call the functions generic_injects.js defines for the page's main video.
    override fun togglePlayback() = media("vireoLumaTVTogglePlayback()")
    override fun stopPlayback() = media("vireoLumaTVStopPlayback()")
    override fun rewind() = media("vireoLumaTVRewind()")
    override fun fastForward() = media("vireoLumaTVFastForward()")
    private fun media(call: String) { webView?.evaluateJavascript(call, null) }

    override fun onAttachToWindow(window: PageWindow, parent: ViewGroup) {
        val view = checkNotNull(webView) { "The WebView must exist before it is attached" }
        val cursor = parent as CursorLayout
        this.window = window
        container = cursor
        cursor.removeAllViews()
        cursor.addView(view)
        cursor.resetInput()
        cursor.cursorEnabled = true
        view.setVirtualCursorMode(true)
        view.isFocusableInTouchMode = true
        view.requestFocus()
        cursor.cursorDrawerDelegate.callback = this
        cursor.cursorDrawerDelegate.animateAppearing()
        onResume()
    }

    override fun onDetachFromWindow(completely: Boolean, destroyTab: Boolean) {
        exitFullscreen(restoreBrowserControls = false)
        onPause()
        (webView?.parent as? ViewGroup)?.removeView(webView)
        window = null
        container?.cursorDrawerDelegate?.callback = null
        container = null
        if (completely) {
            permissionRequests.clear()
            webView?.destroy()
            webView = null
        }
    }

    override fun trimMemory() {
        val view = webView ?: return
        if (view.isAttachedToWindow) return
        exitFullscreen(restoreBrowserControls = false)
        view.destroy()
        webView = null
    }

    override fun hideFullscreenView() {
        webView?.hideCustomView()
        if (fullscreenView != null) exitFullscreen(restoreBrowserControls = true)
    }

    override fun setVirtualCursorMode(enabled: Boolean) {
        container?.cursorEnabled = enabled
        if (enabled) container?.cursorDrawerDelegate?.animateAppearing()
        webView?.setVirtualCursorMode(enabled)
    }

    override fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        val location = permissionRequests.remove(requestCode) ?: return false
        webView?.onPermissionsResult(permissions, grantResults, location)
        return true
    }

    /** Pointer long press: asks the page for the link under it, then opens the pointer menu there. */
    override fun onLongPress(x: Int, y: Int) {
        val view = webView ?: return
        view.evaluateJavascript(Scripts.LONG_PRESS_SCRIPT) { json ->
            if (webView !== view || container == null) return@evaluateJavascript
            // The answer is JSON, not a raw address.
            val link = runCatching { JSONTokener(json).nextValue() as? String }.getOrNull()
            showPointerMenu(view.currentOriginalUrl.toString(), link, x, y)
        }
    }

    private fun showPointerMenu(baseUrl: String?, href: String?, x: Int, y: Int) {
        val cursor = container ?: return
        window?.openPointerMenu(cursor.cursorDrawerDelegate, PagePoint(baseUrl, href, x, y))
    }

    // The page's side, called by WebViewEx and its clients.

    /** Element hiding and scriptlets for a document of this tab, any frame (JavaBridge thread). */
    fun pageFilters(url: String): String = if (adblockEnabled) pageFiltersProvider?.invoke(url).orEmpty() else ""

    fun hiddenSelectors(url: String, classes: String, ids: String): String =
        if (adblockEnabled) hiddenSelectorsProvider?.invoke(url, classes, ids) ?: "[]" else "[]"

    internal fun onPageStarted(url: String?) {
        tab.mediaStarted = false
        window?.onPageStarted(url)
    }

    internal fun requestPermissions(permissions: Array<String>, location: Boolean) {
        val code = window?.requestPermissions(permissions) ?: return
        permissionRequests[code] = location
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

    internal fun onCreateWindow(dialog: Boolean, userGesture: Boolean): WebViewEx? =
        window?.createWindow(dialog, userGesture) as? WebViewEx

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
        window?.onEnterFullscreen()
        webView?.visibility = View.GONE
        webView?.setVirtualCursorMode(false)
        container?.cursorEnabled = false
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
        val shown = fullscreenView
        fullscreenView = null
        (shown?.parent as? ViewGroup)?.removeView(shown)
        webView?.visibility = View.VISIBLE
        webView?.setVirtualCursorMode(true)
        container?.cursorEnabled = true
        window?.onExitFullscreen(restoreBrowserControls)
    }

    companion object {
        private const val TAG = "WebViewWebEngine"

        /** Installed by the window owner (the ad block model); read from the JavaBridge thread. */
        @Volatile var pageFiltersProvider: ((String) -> String)? = null
        @Volatile var hiddenSelectorsProvider: ((String, String, String) -> String)? = null

        /** The WebView backend the app installs in WebEngineFactory at start-up. */
        val Backend = object : WebEngineProviderCallback {
            override fun createWebEngine(tab: WebTabState): WebEngine = WebViewWebEngine(tab)

            // The disk cache belongs to the shared WebView profile, so a short-lived WebView clears it.
            override suspend fun clearCache(ctx: Context) {
                val webView = WebView(ctx)
                try {
                    webView.clearCache(true)
                } finally {
                    webView.destroy()
                }
            }

            override fun getWebEngineVersionString(): String {
                val webViewPackage = WebViewCompat.getCurrentWebViewPackage(AppContext.get())
                return (webViewPackage?.packageName ?: "unknown") + ":" + (webViewPackage?.versionName ?: "unknown")
            }
        }
    }
}
