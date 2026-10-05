package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.reiniertutoriales.vireolumatv.adblock.AdblockRequestClassifier
import android.net.Uri
import android.os.Bundle
import android.util.Log
import org.json.JSONTokener
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.Utils
import com.reiniertutoriales.vireolumatv.webengine.WebEngine
import com.reiniertutoriales.vireolumatv.webengine.WebEngineFactory
import com.reiniertutoriales.vireolumatv.webengine.WebEngineProvider
import com.reiniertutoriales.vireolumatv.webengine.WebEngineProviderCallback
import com.reiniertutoriales.vireolumatv.webengine.WebEngineWindowProviderCallback
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorLayout


class WebViewWebEngine(val tab: WebTabState) : WebEngine, CursorDrawerDelegate.Callback {
    private var webView: WebViewEx? = null
    internal var callback: WebEngineWindowProviderCallback? = null
    private var viewParent: CursorLayout? = null
    private var fullScreenView: View? = null
    private val permissionsRequests = HashMap<Int, Boolean>()//request code, isGeolocationPermissionRequest
    private val jsInterface = AndroidJSInterface(this)

    override fun getWebEngineName(): String = "WebView"

    override fun isSameSession(internalRepresentation: Any): Boolean {
        return internalRepresentation == webView
    }

    override val url: String?
        get() = webView?.url

    override var userAgentString: String? = null
        set(value) {
            field = value
            if (value != null) {
                webView?.settings?.userAgentString = value
            }
        }

    override fun saveState(): Any {
        val bundle = Bundle()
        webView?.saveState(bundle)
        return bundle
    }

    override fun restoreState(savedInstanceState: Any) {
        if (savedInstanceState is Bundle) {
            webView?.restoreState(savedInstanceState)
        } else {
            throw IllegalArgumentException("savedInstanceState must be Bundle")
        }
    }

    override fun stateFromBytes(bytes: ByteArray): Any? =
        Utils.bytesToBundle(bytes)

    override fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    override fun canGoForward(): Boolean {
        return webView?.canGoForward() ?: false
    }

    override fun goForward() {
        webView?.goForward()
    }

    override fun canZoomIn(): Boolean {
        return webView?.canZoomIn() ?: false
    }

    override fun zoomIn() {
        webView?.zoomIn()
    }

    override fun canZoomOut(): Boolean {
        return webView?.canZoomOut() ?: false
    }

    override fun zoomOut() {
        webView?.zoomOut()
    }

    override fun zoomBy(zoomBy: Float) {
        webView?.zoomBy(zoomBy)
    }

    override fun evaluateJavascript(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    override fun setNetworkAvailable(connected: Boolean) {
        webView?.setNetworkAvailable(connected)
    }

    override fun getView(): View? {
        return webView
    }

    @Throws(Exception::class)
    override fun getOrCreateView(activityContext: Context): View {
        if (webView == null) {
            webView = WebViewEx(activityContext, webViewCallback, jsInterface).also {
                // Register before the first navigation, using this tab's setting even before attach.
                it.onUpdateAdblockSetting(tab.adblock ?: AppContext.provideConfig().adBlockEnabled)
            }
        }
        return webView!!
    }

    override fun canGoBack(): Boolean {
        return webView?.canGoBack() ?: false
    }

    override fun goBack() {
        webView?.goBack()
    }

    override fun reload() {
        webView?.reload()
    }

    override fun onFilePicked(resultCode: Int, data: Intent?) {
        webView?.onFilePicked(resultCode, data)
    }

    override fun onResume() {
        webView?.onUpdateAdblockSetting(tab.adblock ?: AppContext.provideConfig().adBlockEnabled)
        webView?.resumeTimers()
        webView?.onResume()
        jsInterface.onHomePageLoaded()
    }

    override fun onPause() {
        jsInterface.cancelHomeFavicons()
        webView?.onPause()
        // onPause alone leaves JavaScript timers running. The process keeps one live renderer.
        webView?.pauseTimers()
    }

    override fun onUpdateAdblockSetting(newState: Boolean) {
        webView?.onUpdateAdblockSetting(newState)
    }

    override fun hideFullscreenView() {
        webView?.hideCustomView()
        if (fullScreenView != null) {
            exitFullscreenView(restoreBrowserControls = true)
        }
    }

    override fun togglePlayback() {
        webView?.evaluateJavascript("vireoLumaTVTogglePlayback()", null)
    }

    override fun stopPlayback() {
        webView?.evaluateJavascript("vireoLumaTVStopPlayback()", null)
    }

    override fun rewind() {
        webView?.evaluateJavascript("vireoLumaTVRewind()", null)
    }

    override fun fastForward() {
        webView?.evaluateJavascript("vireoLumaTVFastForward()", null)
    }

    override suspend fun renderThumbnail(bitmap: Bitmap?): Bitmap? {
        return webView?.renderThumbnail(bitmap)
    }

    override fun onAttachToWindow(callback: WebEngineWindowProviderCallback, parent: ViewGroup) {
        this.callback = callback
        if (webView == null) {
            throw IllegalStateException("WebView is null")
        }
        this.viewParent = parent as CursorLayout
        parent.removeAllViews()
        parent.addView(webView)
        viewParent?.cursorDrawerDelegate?.callback = this
        onResume()
    }

    override fun onDetachFromWindow(completely: Boolean, destroyTab: Boolean) {
        jsInterface.cancelHomeFavicons()
        Log.i(TAG, "onDetachFromWindow completely=" + completely + " destroyTab=" + destroyTab + " hasWebView=" + (webView != null) + " hasFullscreen=" + (fullScreenView != null))
        exitFullscreenView(restoreBrowserControls = false)
        onPause()
        (webView?.parent as? ViewGroup)?.removeView(webView)
        callback = null
        viewParent?.cursorDrawerDelegate?.callback = null
        viewParent = null
        if (completely) {
            permissionsRequests.clear()
            webView?.destroy()
            webView = null
        }
    }

    override fun trimMemory() {
        val webView = webView
        Log.i(TAG, "trimMemory hasWebView=" + (webView != null) + " attached=" + webView?.isAttachedToWindow + " hasFullscreen=" + (fullScreenView != null))
        if (webView != null && !webView.isAttachedToWindow) {
            Log.i(TAG, "trimMemory destroying detached WebView")
            exitFullscreenView(restoreBrowserControls = false)
            webView.destroy()
            this.webView = null
        }
    }

    override fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        val isGeolocationPermissionRequest = permissionsRequests[requestCode] ?: return false
        permissionsRequests.remove(requestCode)
        webView?.onPermissionsResult(permissions, grantResults, isGeolocationPermissionRequest)
        return true
    }


    override fun onLongPress(x: Int, y: Int) {
        webView?.let {
            it.evaluateJavascript(Scripts.LONG_PRESS_SCRIPT) { href ->
                // evaluateJavascript returns JSON, not a raw URI.
                val linkUrl = runCatching { JSONTokener(href).nextValue() as? String }.getOrNull()
                if (webView !== it || viewParent == null) return@evaluateJavascript
                webViewCallback.onContextMenu(
                    it.currentOriginalUrl.toString(),
                    linkUrl, x, y
                )
            }
        }
    }

    override fun isVirtualCursorMode(): Boolean {
        return viewParent?.cursorEnabled ?: true
    }

    override fun setVirtualCursorMode(enabled: Boolean) {
        viewParent?.cursorEnabled = enabled
        if (enabled) {
            viewParent?.cursorDrawerDelegate?.animateAppearing()
        }
        webView?.setVirtualCursorMode(enabled)
    }

    override fun getCursorDrawerDelegate(): CursorDrawerDelegate? {
        return viewParent?.cursorDrawerDelegate
    }

    private fun enterFullscreenView(view: View) {
        Log.i(TAG, "enterFullscreenView requested view=" + view.javaClass.name + " hasFullscreen=" + (fullScreenView != null) + " hasWebView=" + (webView != null) + " hasParent=" + (viewParent != null))
        if (fullScreenView != null) {
            Log.i(TAG, "enterFullscreenView rejected: fullscreen view already present")
            (view.parent as? ViewGroup)?.removeView(view)
            return
        }
        callback?.onPrepareForFullscreen()
        webView?.visibility = View.GONE
        webView?.setVirtualCursorMode(false)
        viewParent?.cursorEnabled = false
        (view.parent as? ViewGroup)?.removeView(view)
        viewParent?.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullScreenView = view
        view.requestFocus()
    }

    private fun exitFullscreenView(restoreBrowserControls: Boolean) {
        Log.i(TAG, "exitFullscreenView restoreBrowserControls=" + restoreBrowserControls + " hasFullscreen=" + (fullScreenView != null) + " hasWebView=" + (webView != null))
        val fullscreenView = fullScreenView
        fullScreenView = null
        (fullscreenView?.parent as? ViewGroup)?.removeView(fullscreenView)
        webView?.visibility = View.VISIBLE
        webView?.setVirtualCursorMode(true)
        callback?.onExitFullscreen()
        viewParent?.cursorEnabled = true
        if (restoreBrowserControls) {
            restoreBrowserControlsAfterFullscreen()
        }
    }

    private fun restoreBrowserControlsAfterFullscreen() {
        val activity = callback?.getActivity() ?: return
        activity.window.decorView.post {
            activity.findViewById<View>(R.id.flWebViewContainer)?.visibility = View.VISIBLE
            activity.findViewById<View>(R.id.rlActionBar)?.visibility = View.VISIBLE
            activity.findViewById<View>(R.id.llBottomPanel)?.visibility = View.VISIBLE
            activity.findViewById<View>(R.id.vActionBar)?.requestFocus()
        }
    }

    private val webViewCallback = object : WebViewEx.Callback {
        override fun getActivity(): Activity? {
            return callback?.getActivity()
        }

        override fun onOpenInNewTabRequested(url: String) {
            callback?.onOpenInNewTabRequested(url, true)
        }

        override fun onDownloadRequested(url: String) {
            callback?.onDownloadRequested(url)
        }

        override fun onThumbnailError() {
            //nop for now
        }

        override fun onShowCustomView(view: View) {
            Log.i(TAG, "callback.onShowCustomView view=" + view.javaClass.name)
            enterFullscreenView(view)
        }

        override fun onHideCustomView() {
            Log.i(TAG, "callback.onHideCustomView")
            exitFullscreenView(restoreBrowserControls = true)
        }

        override fun onProgressChanged(newProgress: Int) {
            callback?.onProgressChanged(newProgress)
        }

        override fun onReceivedTitle(title: String) {
            callback?.onReceivedTitle(title)
        }

        override fun requestPermissions(array: Array<String>, geo: Boolean) {
            val requestCode = callback?.requestPermissions(array) ?: return
            permissionsRequests[requestCode] = geo
        }

        override fun onShowFileChooser(intent: Intent): Boolean {
            return callback?.onShowFileChooser(intent) ?: false
        }

        override fun onReceivedIcon(icon: Bitmap) {
            callback?.onReceivedIcon(icon)
        }

        override fun shouldOverrideUrlLoading(url: String): Boolean {
            return callback?.shouldOverrideUrlLoading(url) ?: false
        }

        override fun onPageStarted(url: String?) {
            jsInterface.cancelHomeFavicons()
            callback?.onPageStarted(url)
        }

        override fun onPageFinished(url: String?) {
            callback?.onPageFinished(url)
        }

        override fun onPageCertificateError(url: String?) {
            callback?.onPageCertificateError(url)
        }

        override fun onRenderProcessGone(): Boolean {
            Log.i(TAG, "onRenderProcessGone hasWebView=" + (webView != null) + " hasFullscreen=" + (fullScreenView != null))
            jsInterface.cancelHomeFavicons()
            exitFullscreenView(restoreBrowserControls = false)
            val deadWebView = webView ?: return true
            tab.rendererLost = true
            (deadWebView.parent as? ViewGroup)?.removeView(deadWebView)
            deadWebView.destroy()
            webView = null
            callback?.onRenderProcessGone()
            return true
        }

        override fun isAd(request: WebResourceRequest, baseUri: Uri): Boolean {
            val type = AdblockRequestClassifier.classify(request.url, request.requestHeaders, request.isForMainFrame)
            val pageContext = AdblockRequestClassifier.pageContext(request.url, baseUri, request.isForMainFrame)
            return callback?.isAd(request.url, type, pageContext) ?: false
        }

        override fun isAdBlockingEnabled(): Boolean {
            return callback?.isAdBlockingEnabled() ?: false
        }

        override fun isDialogsBlockingEnabled(): Boolean {
            return callback?.isDialogsBlockingEnabled() ?: false
        }

        override fun onBlockedAds(count: Int) {
            callback?.onBlockedAds(count)
        }

        override fun onBlockedDialog(newTab: Boolean) {
            callback?.onBlockedDialog(newTab)
        }

        override fun onCreateWindow(dialog: Boolean, userGesture: Boolean): WebViewEx? {
            return callback?.onCreateWindow(dialog, userGesture) as? WebViewEx
        }

        override fun closeWindow(window: WebView) {
            callback?.closeWindow(window)
        }

        override fun onDownloadStart(url: String, userAgent: String, contentDisposition: String, mimetype: String?, contentLength: Long) {
            callback?.onDownloadRequested(url, userAgent, contentDisposition, mimetype, contentLength)
        }

        override fun onScaleChanged(oldScale: Float, newScale: Float) {
            callback?.onScaleChanged(oldScale, newScale)
        }

        override fun onCopyTextToClipboardRequested(url: String) {
            callback?.onCopyTextToClipboardRequested(url)
        }

        override fun onShareUrlRequested(url: String) {
            callback?.onShareUrlRequested(url)
        }

        override fun onOpenInExternalAppRequested(url: String) {
            callback?.onOpenInExternalAppRequested(url)
        }

        override fun onVisited(url: String) {
            callback?.onVisited(url)
        }

        override fun onContextMenu(baseUrl:String?, href: String?, x: Int, y: Int) {
            callback?.onContextMenu(
                viewParent!!.cursorDrawerDelegate,
                baseUri = baseUrl,
                linkUri = href,
                srcUri = null,
                title = null,
                altText = null,
                textContent = null,
                x = x,
                y = y
            )
        }
    }

    companion object {
        private val TAG = WebViewWebEngine::class.java.simpleName

        init {
            WebEngineFactory.registerProvider(WebEngineProvider("WebView", object : WebEngineProviderCallback {
                override suspend fun initialize(context: Context, webViewContainer: CursorLayout) {

                }

                override fun createWebEngine(tab: WebTabState): WebEngine {
                    return WebViewWebEngine(tab)
                }

                override suspend fun clearCache(ctx: Context) {
                    //the disk cache is cleared by the shared BrowserContext, so destroying this temporary
                    //WebView right away does not cancel it
                    val webView = WebView(ctx)
                    try {
                        webView.clearCache(true)
                    } finally {
                        webView.destroy()
                    }
                }

                override fun onThemeSettingUpdated(value: Config.Theme) {
                    //nop
                }

                override fun getWebEngineVersionString(): String {
                    val webViewPackage = WebViewCompat.getCurrentWebViewPackage(AppContext.get())
                    return (webViewPackage?.packageName ?: "unknown") + ":" + (webViewPackage?.versionName ?: "unknown")
                }
            }))
        }
    }
}