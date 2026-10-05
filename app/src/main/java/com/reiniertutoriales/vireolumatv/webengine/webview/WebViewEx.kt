package com.reiniertutoriales.vireolumatv.webengine.webview

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaDrm
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.utils.DPADNavigationEventsAdapter
import com.reiniertutoriales.vireolumatv.utils.Utils
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger


/**
 * Copyright (c) 2016 Fedir Tsapana.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
open class WebViewEx(context: Context, val callback: Callback, val jsInterface: AndroidJSInterface) : WebView(context) {
    companion object {
        const val TAG = "WebViewEx"
        const val WEB_VIEW_TAG = "VireoLumaTV WebView"
        const val INTERNAL_SCHEME = "internal://"
        //blocked requests are reported to the UI at most once per this interval
        private const val BLOCKED_ADS_REPORT_DELAY_MS = 250L
        const val INTERNAL_SCHEME_WARNING_DOMAIN = "warning"
        const val INTERNAL_SCHEME_WARNING_DOMAIN_TYPE_CERT = "certificate"
        val WIDEVINE_UUID = UUID(-0x121074568629b532L,-0x5c37d8232ae2de13L)

        /**
         * Compares the actual certificates (DER bytes). SslCertificate.toString() only contains the
         * issued-to/issued-by names, so two different certificates with the same names were treated as equal.
         */
        fun isSameCertificate(a: SslCertificate?, b: SslCertificate?): Boolean {
            if (a == null || b == null) return false
            val encodedA = encodedCertificate(a)
            val encodedB = encodedCertificate(b)
            if (encodedA != null && encodedB != null) return encodedA.contentEquals(encodedB)
            //no access to the certificate bytes on this device: keep the previous (weaker) behaviour
            return a.toString() == b.toString()
        }

        private fun encodedCertificate(cert: SslCertificate): ByteArray? {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cert.x509Certificate?.encoded
                } else {
                    //undocumented but stable AOSP key used by SslCertificate.saveState() since API 14
                    SslCertificate.saveState(cert)?.getByteArray("x509-certificate")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Can not get encoded certificate: $e")
                null
            }
        }
    }

    private var virtualCursorMode: Boolean = true
    private var genericInjects: String? = null
    private var webChromeClient_: WebChromeClient
    private var fullscreenViewCallback: WebChromeClient.CustomViewCallback? = null
    private var pickFileCallback: ValueCallback<Array<Uri>>? = null
    private var permRequestDialog: AlertDialog? = null
    private var webPermissionsRequest: PermissionRequest? = null
    private var requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions: ArrayList<String>? = null
    //the web request the user approved and that now waits for the Android runtime permission result
    private var androidPermissionsPendingRequest: PermissionRequest? = null
    private var geoPermissionOrigin: String? = null
    private var geoPermissionsCallback: GeolocationPermissions.Callback? = null
    @Volatile var lastSSLError: SslError? = null
    @Volatile var certificateErrorPageUrl: String? = null
        private set
    var trustSsl: Boolean = false
    @Volatile var currentOriginalUrl: Uri? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private val pendingBlockedAds = AtomicInteger(0)
    private val reportBlockedAdsRunnable = Runnable {
        val count = pendingBlockedAds.getAndSet(0)
        if (count > 0) callback.onBlockedAds(count)
    }
    private val config = AppContext.provideConfig()

    interface Callback {
        fun getActivity(): Activity?
        fun onOpenInNewTabRequested(url: String)
        fun onDownloadRequested(url: String)
        fun onThumbnailError()
        fun onShowCustomView(view: View)
        fun onHideCustomView()
        fun onProgressChanged(newProgress: Int)
        fun onReceivedTitle(title: String)
        fun onShowFileChooser(intent: Intent): Boolean
        fun onReceivedIcon(icon: Bitmap)
        fun requestPermissions(array: Array<String>, geo: Boolean)
        fun shouldOverrideUrlLoading(url: String): Boolean
        fun onPageStarted(url: String?)
        fun onPageFinished(url: String?)
        fun onPageCertificateError(url: String?)
        fun onRenderProcessGone(): Boolean
        fun isAdBlockingEnabled(): Boolean
        fun isDialogsBlockingEnabled(): Boolean
        fun isAd(request: WebResourceRequest, baseUri: Uri): Boolean
        fun onBlockedAds(count: Int)
        fun onBlockedDialog(newTab: Boolean)
        fun onCreateWindow(dialog: Boolean, userGesture: Boolean): WebViewEx?
        fun closeWindow(window: WebView)
        fun onDownloadStart(url: String, userAgent: String, contentDisposition: String, mimetype: String?, contentLength: Long)
        fun onScaleChanged(oldScale: Float, newScale: Float)
        fun onCopyTextToClipboardRequested(url: String)
        fun onShareUrlRequested(url: String)
        fun onOpenInExternalAppRequested(url: String)
        fun onVisited(url: String)
        fun onContextMenu(baseUrl: String?, href: String?, x: Int, y: Int)
    }

    init {
        with(settings) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            domStorageEnabled = true
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = !config.allowAutoplayMedia
            setGeolocationEnabled(true)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            setNeedInitialFocus(false)

            if (config.webEngineDebug) {
                setWebContentsDebuggingEnabled(true)
            }

            val allowDarkening = config.webviewUseAlgorithmicDarkeningWithDarkUiMode
            val uiNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    if (uiNightMode == Configuration.UI_MODE_NIGHT_YES && allowDarkening) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, true)
                    } else {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, false)
                    }
                }
            } else {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    if (uiNightMode == Configuration.UI_MODE_NIGHT_YES && allowDarkening) {
                        WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_ON)
                    } else {
                        WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_OFF)
                    }
                }
            }
        }

        setOnLongClickListener { v ->
            true
        }

        webChromeClient_ = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                return if (callback.isDialogsBlockingEnabled()) {
                    callback.onBlockedDialog(false)
                    result.cancel()
                    true
                } else super.onJsAlert(view, url, message, result)
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                return if (callback.isDialogsBlockingEnabled()) {
                    callback.onBlockedDialog(false)
                    result.cancel()
                    true
                } else super.onJsConfirm(view, url, message, result)
            }

            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                return if (callback.isDialogsBlockingEnabled()) {
                    callback.onBlockedDialog(false)
                    result.cancel()
                    true
                } else super.onJsPrompt(view, url, message, defaultValue, result)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                Log.i(TAG, "onShowCustomView requested view=" + view.javaClass.name + " hasCallback=" + (fullscreenViewCallback != null))
                if (fullscreenViewCallback != null) {
                    Log.i(TAG, "onShowCustomView rejected: fullscreen callback already present")
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenViewCallback = callback
                this@WebViewEx.callback.onShowCustomView(view)
            }

            override fun onHideCustomView() {
                Log.i(TAG, "onHideCustomView hasCallback=" + (fullscreenViewCallback != null))
                callback.onHideCustomView()
                fullscreenViewCallback?.onCustomViewHidden()
                fullscreenViewCallback = null
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                callback.onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                callback.onReceivedTitle(title)
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.resources.size == 1 &&
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID == request.resources[0]) {
                    //fast path for grant/deny RESOURCE_PROTECTED_MEDIA_ID
                    if (MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID)) {
                        val widevineKeyDrm = MediaDrm(WIDEVINE_UUID)
                        val version = widevineKeyDrm.getPropertyString(MediaDrm.PROPERTY_VERSION)
                        Log.i(TAG, "DRM widevine version = " + version)
                        request.grant(request.resources)
                    } else {
                        request.deny()
                    }
                    return
                }

                val activity = callback.getActivity()
                if (activity == null) {
                    request.deny()
                    return
                }
                //a newer request replaces the previous one: close its dialog and forget its state, so neither
                //its buttons nor its pending Android permission result can grant this new request
                permRequestDialog?.dismiss()
                permRequestDialog = null
                requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions = null
                androidPermissionsPendingRequest = null
                webPermissionsRequest?.deny()
                webPermissionsRequest = request
                permRequestDialog = AlertDialog.Builder(activity)
                        .setMessage(activity.getString(R.string.web_perm_request_confirmation, TextUtils.join("\n", request.resources)))
                        .setCancelable(false)
                        .setNegativeButton(R.string.deny) { _, _ ->
                            webPermissionsRequest?.deny()
                            permRequestDialog = null
                            webPermissionsRequest = null
                        }
                        .setPositiveButton(R.string.allow) { dialog, which ->
                            val webPermissionsRequest = this@WebViewEx.webPermissionsRequest
                            if (webPermissionsRequest == null) {
                                return@setPositiveButton
                            }

                            val neededPermissions = ArrayList<String>()
                            val resourcesThatDoNotNeedToGrantPerms = ArrayList<String>()
                            for (resource in webPermissionsRequest.resources) {
                                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE == resource) {
                                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                        neededPermissions.add(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        resourcesThatDoNotNeedToGrantPerms.add(resource)
                                    }
                                } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE == resource) {
                                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                        neededPermissions.add(Manifest.permission.CAMERA)
                                    } else {
                                        resourcesThatDoNotNeedToGrantPerms.add(resource)
                                    }
                                } else {
                                    resourcesThatDoNotNeedToGrantPerms.add(resource)
                                }
                            }

                            if (neededPermissions.isNotEmpty()) {
                                requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions = resourcesThatDoNotNeedToGrantPerms
                                androidPermissionsPendingRequest = webPermissionsRequest
                                callback.requestPermissions(neededPermissions.toTypedArray(), false)
                            } else {
                                webPermissionsRequest.grant(webPermissionsRequest.resources)
                                this@WebViewEx.webPermissionsRequest = null
                            }

                            permRequestDialog = null
                        }
                        .create()
                permRequestDialog!!.show()
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (webPermissionsRequest !== request) return
                permRequestDialog?.apply {
                    dismiss()
                    permRequestDialog = null
                }
                webPermissionsRequest = null
                requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions = null
                androidPermissionsPendingRequest = null
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val activity = this@WebViewEx.callback.getActivity() ?: return
                geoPermissionOrigin = origin
                geoPermissionsCallback = callback
                permRequestDialog = AlertDialog.Builder(activity)
                        .setMessage(activity.getString(R.string.web_perm_request_confirmation, activity.getString(R.string.location)))
                        .setCancelable(false)
                        .setNegativeButton(R.string.deny) { dialog, which ->
                            geoPermissionsCallback!!.invoke(geoPermissionOrigin, false, false)
                            permRequestDialog = null
                            geoPermissionsCallback = null
                        }
                        .setPositiveButton(R.string.allow) { dialog, which ->
                            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                this@WebViewEx.callback.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), true)
                            } else {
                                geoPermissionsCallback!!.invoke(geoPermissionOrigin, true, true)
                                geoPermissionsCallback = null
                            }
                            permRequestDialog = null
                        }
                        .create()
                permRequestDialog!!.show()
            }

            override fun onGeolocationPermissionsHidePrompt() {
                if (permRequestDialog != null) {
                    permRequestDialog!!.dismiss()
                    permRequestDialog = null
                }
                geoPermissionsCallback = null
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val msg: String = "(" + consoleMessage.sourceId() + "[" + consoleMessage.lineNumber() + "]): " + consoleMessage.message()
                when (consoleMessage.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.e(WEB_VIEW_TAG, msg)
                    ConsoleMessage.MessageLevel.WARNING -> Log.w(WEB_VIEW_TAG, msg)
                    else -> Log.i(WEB_VIEW_TAG, msg)
                }
                return true
            }


            override fun onShowFileChooser(mWebView: WebView, callback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                pickFileCallback = callback

                val result = this@WebViewEx.callback.onShowFileChooser(fileChooserParams.createIntent())
                if (!result) {
                    pickFileCallback = null
                }
                return result
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap) {
                Log.d(TAG, "onReceivedIcon: ${icon.width}x${icon.height}")
                callback.onReceivedIcon(icon)
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val webView = callback.onCreateWindow(isDialog, isUserGesture) ?: return false
                (resultMsg.obj as WebView.WebViewTransport).webView = webView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                callback.closeWindow(window)
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return callback.shouldOverrideUrlLoading(request.url.toString())
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val currentPageUrl = currentOriginalUrl

                if (currentPageUrl != null && currentPageUrl.toString().startsWith(Config.HOME_PAGE_URL,
                        ignoreCase = true)) {
                    HomePageHelper.shouldInterceptRequest(view, request)?.let {
                        return it
                    }
                    if (request.url.toString().startsWith(Config.HOME_PAGE_URL)) {
                        var relativePath = request.url.toString().substring(Config.HOME_PAGE_URL.length)
                        if (relativePath.isEmpty() || relativePath == "/") {
                            relativePath = "index.html"
                        }
                        val assetsPath = "pages/home/$relativePath"
                        val response = Utils.getWebResourceResponseFromAssets(view.context, assetsPath)
                        if (response != null) {
                            Log.d(TAG, "shouldInterceptRequest url: ${request.url} -> $assetsPath")
                            return response
                        } else {
                            Log.w(TAG, "shouldInterceptRequest url: ${request.url} -> not found in assets")
                        }
                        return response ?: super.shouldInterceptRequest(view, request)
                    }
                }

                if (!callback.isAdBlockingEnabled()) {
                    return super.shouldInterceptRequest(view, request)
                }

                val ad = currentPageUrl?.let { callback.isAd(request, it)} ?: false
                return if (ad) {
                    //coalesce: only the first blocked request of a burst schedules a UI report
                    if (pendingBlockedAds.getAndIncrement() == 0) {
                        uiHandler.postDelayed(reportBlockedAdsRunnable, BLOCKED_ADS_REPORT_DELAY_MS)
                    }
                    val response = WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                    response.setStatusCodeAndReasonPhrase(403, "Blocked")
                    response
                } else super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "onPageStarted url: $url")
                currentOriginalUrl = url.toUri()
                if (url != "file:///android_asset/") certificateErrorPageUrl = null
                callback.onPageStarted(url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "onPageFinished url: $url")
                callback.onPageFinished(url)
                evaluateJavascript(getGenericJSInjects(), null)
            }

            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)
                //Log.d(TAG, "onLoadResource url: $url")
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                return callback.onRenderProcessGone()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                Log.e(TAG, "onReceivedSslError url: ${error.url}")
                if (trustSsl && isSameCertificate(lastSSLError?.certificate, error.certificate)) {
                    trustSsl = false
                    lastSSLError = null
                    handler.proceed()
                    return
                }
                handler.cancel()
                val errUrl = error.url ?: return
                val origUrl = currentOriginalUrl ?: return
                if (Uri.parse(errUrl).host == origUrl.host) {//skip ssl errors during loading non-page resources (Chrome did like this too)
                    showCertificateErrorPage(error)
                }
            }

            override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
                super.onScaleChanged(view, oldScale, newScale)
                callback.onScaleChanged(oldScale, newScale)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String, isReload: Boolean) {
                if (!isReload) {
                    callback.onVisited(url)
                }
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                val userNameEdit = EditText(context).also {
                    it.hint = context.getString(com.reiniertutoriales.vireolumatv.common.R.string.username)
                    it.isSingleLine = true
                }
                val passwordEdit = EditText(context).also {
                    it.hint = context.getString(com.reiniertutoriales.vireolumatv.common.R.string.password)
                    it.isSingleLine = true
                    it.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                val container = LinearLayout(context).also {
                    it.orientation = LinearLayout.VERTICAL
                    it.addView(userNameEdit)
                    it.addView(passwordEdit)
                }
                AlertDialog.Builder(context)
                    .setTitle(R.string.http_auth_title)
                    .setCancelable(false)
                    .setView(container)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface, _:Int ->
                        handler?.proceed(userNameEdit.text.toString(), passwordEdit.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel) { _: DialogInterface, _:Int ->
                        handler?.cancel()
                    }
                    .show()
            }
        }

        webChromeClient = webChromeClient_

        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            Log.i(TAG, "DownloadListener.onDownloadStart url: $url")
            if (url.startsWith("blob:")) {
                //nop. we handle this by injected js on onPageFinished
            } else {
                callback.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength)
            }
        }

        addJavascriptInterface(jsInterface, "VireoLumaTVApp")
    }

    override fun restoreState(inState: Bundle): WebBackForwardList? {
        val result = super.restoreState(inState)
        currentOriginalUrl = url?.toUri()
        return result
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (virtualCursorMode && DPADNavigationEventsAdapter.isNavigationGenericMotionSource(event.source))
            return false
        return super.dispatchGenericMotionEvent(event)
    }

    private fun showCertificateErrorPage(error: SslError) {
        callback.onPageCertificateError(error.url)
        lastSSLError = error
        val url = INTERNAL_SCHEME + INTERNAL_SCHEME_WARNING_DOMAIN +
                "?type=" + INTERNAL_SCHEME_WARNING_DOMAIN_TYPE_CERT +
                "&url=" + URLEncoder.encode(error.url, "UTF-8")
        certificateErrorPageUrl = url
        loadUrl(url)
    }

    override fun loadUrl(url: String) {
        if (url != certificateErrorPageUrl) certificateErrorPageUrl = null
        when {
            Config.HOME_URL_ALIAS == url -> {
                when (config.homePageMode) {
                    Config.HomePageMode.BLANK -> {
                        loadDataWithBaseURL(null, "", "text/html", "UTF-8", null)
                    }
                    Config.HomePageMode.CUSTOM, Config.HomePageMode.SEARCH_ENGINE -> {
                        try {
                            currentOriginalUrl = config.homePage.toUri()
                            super.loadUrl(config.homePage)
                        } catch (e: Exception) {
                            Log.e(TAG, "LoadUrl error", e)
                            loadDataWithBaseURL(null, "", "text/html", "UTF-8", null)
                        }

                    }
                    Config.HomePageMode.HOME_PAGE -> {
                        currentOriginalUrl = Config.HOME_PAGE_URL.toUri()
                        super.loadUrl(Config.HOME_PAGE_URL)
                    }
                }

            }
            url.startsWith(INTERNAL_SCHEME) -> {
                val uri = Uri.parse(url)
                when (uri.authority) {
                    INTERNAL_SCHEME_WARNING_DOMAIN -> {
                        when (uri.getQueryParameter("type")) {
                            INTERNAL_SCHEME_WARNING_DOMAIN_TYPE_CERT -> {
                                val data = context.assets.open("pages/warning-certificate.html").bufferedReader().use { it.readText() }
                                loadDataWithBaseURL("file:///android_asset/", data, "text/html", "UTF-8", uri.getQueryParameter("url"))
                            }
                        }
                    }
                }
            }
            else -> {
                currentOriginalUrl = Uri.parse(url)
                super.loadUrl(url)
            }
        }
    }

    private fun getGenericJSInjects(): String {
        var injects = genericInjects
        if (injects == null) {
            injects =
                context.assets.open("generic_injects.js").bufferedReader().use { it.readText() }
            genericInjects = injects
        }
        return injects
    }

    fun renderThumbnail(bitmap: Bitmap?): Bitmap? {
        if (width == 0 || height == 0) return null
        // This is a menu preview, not a full-resolution screenshot. Avoid a multi-MiB 4K bitmap.
        val previewScale = minOf(1f, 480f / maxOf(width, height))
        val previewWidth = maxOf(1, (width * previewScale).toInt())
        val previewHeight = maxOf(1, (height * previewScale).toInt())
        var thumbnail = bitmap?.takeIf {
            !it.isRecycled && it.width == previewWidth && it.height == previewHeight
        }
        if (thumbnail == null) {
            try {
                thumbnail = createBitmap(previewWidth, previewHeight, Bitmap.Config.RGB_565)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        if (thumbnail == null) {
            return null
        }
        val canvas = Canvas(thumbnail)
        val scaleFactor = thumbnail.width / width.toFloat()
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(-scrollX.toFloat(), -scrollY.toFloat())
        super.draw(canvas)
        return thumbnail
    }

    fun hideCustomView() {
        webChromeClient_.onHideCustomView()
    }

    fun onFilePicked(resultCode: Int, data: Intent?) {
        val callback = pickFileCallback ?: return
        pickFileCallback = null
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
    }

    fun onPermissionsResult(permissions: Array<String>, grantResults: IntArray, typeGeo: Boolean) {
        if (typeGeo) geoPermissionsCallback?.apply {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                this.invoke(geoPermissionOrigin, true, true)
            } else {
                this.invoke(geoPermissionOrigin, false, false)
            }
            geoPermissionsCallback = null
            geoPermissionOrigin = null


        } else {
            val pendingRequest = androidPermissionsPendingRequest ?: return
            androidPermissionsPendingRequest = null
            //the request was replaced (and denied) or cancelled while the system dialog was open
            if (pendingRequest !== webPermissionsRequest) return
            // If request is cancelled, the result arrays are empty.
            val resources = ArrayList<String>()
            for (i in permissions.indices) {
                if (grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED) {
                    if (Manifest.permission.CAMERA == permissions[i]) {
                        resources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    } else if (Manifest.permission.RECORD_AUDIO == permissions[i]) {
                        resources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    }
                }
            }
            requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions?.apply {
                resources.addAll(this)
                requestedWebResourcesThatDoNotNeedToGrantAndroidPermissions = null
            }
            if (resources.isEmpty()) {
                pendingRequest.deny()
            } else {
                pendingRequest.grant(resources.toTypedArray())
            }
            webPermissionsRequest = null
        }
    }

    fun onUpdateAdblockSetting(adblockEnabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Malware/phishing protection is independent of the advertising preference.
            settings.safeBrowsingEnabled = true
        }
    }

    fun setVirtualCursorMode(enabled: Boolean) {
        this.virtualCursorMode = enabled
    }
}
