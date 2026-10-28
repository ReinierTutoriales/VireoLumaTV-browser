package com.reiniertutoriales.vireolumatv.webengine.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebBackForwardList
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.utils.RemoteInput
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

/**
 * The page view of one tab: the browser's WebView settings, address handling (home page,
 * certificate warning) and the page features around it. The page's requests are handled by
 * [BrowserChromeClient], loading by [BrowserWebViewClient] and permissions by [PagePermissions].
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
open class WebViewEx(context: Context, internal val engine: WebViewWebEngine) : WebView(context) {
    /** The browser window while the tab is shown, null while it is detached. */
    internal val window get() = engine.window


    internal val config = AppContext.config
    internal val consoleLogBudget = StreamLogBudget { SystemClock.elapsedRealtime() }
    internal val streamLogBudget = StreamLogBudget { SystemClock.elapsedRealtime() }
    internal val permissions = PagePermissions({ window?.activity }, engine::requestPermissions)
    private val chromeClient = BrowserChromeClient(this)
    private val youtubeAdblock by lazy { YouTubeAdblockController(this) }
    private val adblockPage by lazy { AdblockPageController(this) }
    private val mediaPolicy by lazy { MediaPolicyController(this, config) }
    private var pointerMode = true
    private var genericInjects: String? = null

    /** The address the browser asked for, before redirects; the base for ad and home page rules. */
    @Volatile var currentOriginalUrl: Uri? = null
    /** The certificate error shown on the warning page, and whether the user chose to trust it once. */
    @Volatile var lastSSLError: SslError? = null
    var trustSsl = false
    @Volatile var certificateErrorPageUrl: String? = null
        private set

    private val ui = Handler(Looper.getMainLooper())
    private val pendingBlockedAds = AtomicInteger(0)
    private val reportBlockedAds = Runnable {
        val count = pendingBlockedAds.getAndSet(0)
        if (count > 0) window?.onAdsBlocked(count)
    }

    init {
        configure(settings)
        // A long press opens the pointer menu instead of WebView's own selection.
        setOnLongClickListener { true }
        webChromeClient = chromeClient
        webViewClient = BrowserWebViewClient(this)
        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            // Blob downloads are read by the injected script; everything else goes to the download service.
            if (!url.startsWith("blob:")) window?.onPageDownload(url, userAgent, contentDisposition, mimetype, contentLength)
        }
        addJavascriptInterface(engine.bridge, JS_INTERFACE_NAME)
        // Before the first navigation, so the first page already sees the codec policy.
        mediaPolicy.refresh()
    }

    private fun configure(settings: WebSettings) = with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        allowContentAccess = false
        cacheMode = WebSettings.LOAD_DEFAULT
        mediaPlaybackRequiresUserGesture = !config.allowAutoplayMedia
        setGeolocationEnabled(true)
        // New windows only through onCreateWindow, where the pop-up rules apply.
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(true)
        setNeedInitialFocus(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        if (config.webEngineDebug) setWebContentsDebuggingEnabled(true)
        // The interface is always dark, so the setting alone decides whether pages are darkened.
        val darken = config.webviewUseAlgorithmicDarkeningWithDarkUiMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, darken)
            }
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDark(this, if (darken) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
        }
    }

    /**
     * Loads [url]: "about:home" opens the home page the settings choose, internal warning pages
     * come from assets, anything else is loaded as it is.
     */
    override fun loadUrl(url: String) {
        if (url != certificateErrorPageUrl) certificateErrorPageUrl = null
        when {
            url == Config.HOME_URL_ALIAS -> loadHome()
            url.startsWith(INTERNAL_SCHEME) -> loadInternal(url.toUri())
            else -> {
                currentOriginalUrl = url.toUri()
                super.loadUrl(url)
            }
        }
    }

    private fun loadHome() {
        when (config.homePageMode) {
            Config.HomePageMode.BLANK -> loadBlank()
            Config.HomePageMode.HOME_PAGE -> {
                currentOriginalUrl = Config.HOME_PAGE_URL.toUri()
                super.loadUrl(Config.HOME_PAGE_URL)
            }
            Config.HomePageMode.CUSTOM, Config.HomePageMode.SEARCH_ENGINE -> try {
                currentOriginalUrl = config.homePage.toUri()
                super.loadUrl(config.homePage)
            } catch (error: Exception) {
                Log.e(TAG, "Home page failed to load", error)
                loadBlank()
            }
        }
    }

    private fun loadBlank() = loadDataWithBaseURL(null, "", "text/html", "UTF-8", null)

    /** The certificate warning is the only internal page, and only for the error being shown. */
    private fun loadInternal(uri: Uri) {
        if (uri.authority != INTERNAL_SCHEME_WARNING_DOMAIN || uri.getQueryParameter("type") != INTERNAL_SCHEME_WARNING_DOMAIN_TYPE_CERT) return
        val error = lastSSLError ?: return
        val template = context.assets.open(CERTIFICATE_WARNING_ASSET).bufferedReader().use { it.readText() }
        loadDataWithBaseURL(ASSET_BASE, CertificateWarningPage.render(context, template, error.url, error.primaryError,
            error.certificate?.toString().orEmpty()), "text/html", "UTF-8",
            uri.getQueryParameter("url"))
    }

    /** The error the certificate warning on screen is about; null on any other page. */
    internal fun shownCertificateError(): SslError? =
        lastSSLError?.takeIf { BridgePagePolicy.isCertificatePage(currentOriginalUrl, certificateErrorPageUrl, true) }

    /** Replaces the page with the certificate warning, which can offer to trust [error] once. */
    internal fun showCertificateErrorPage(error: SslError) {
        window?.onCertificateError()
        lastSSLError = error
        val url = "$INTERNAL_SCHEME$INTERNAL_SCHEME_WARNING_DOMAIN?type=$INTERNAL_SCHEME_WARNING_DOMAIN_TYPE_CERT&url=" +
            URLEncoder.encode(error.url, "UTF-8")
        certificateErrorPageUrl = url
        loadUrl(url)
    }

    /** One blocked request; a burst is reported to the window once, after [BLOCKED_ADS_REPORT_DELAY_MS]. */
    internal fun countBlockedAd() {
        if (pendingBlockedAds.getAndIncrement() == 0) ui.postDelayed(reportBlockedAds, BLOCKED_ADS_REPORT_DELAY_MS)
    }

    internal fun onDocumentStarted(url: String) {
        currentOriginalUrl = url.toUri()
        mediaPolicy.onPageStarted()
        // Setting changes apply from the next document on.
        mediaPolicy.refresh()
        if (url != ASSET_BASE) certificateErrorPageUrl = null
    }

    internal fun onDocumentStartReported(url: String) = youtubeAdblock.onPage(url)

    internal fun onDocumentFinished(url: String?) {
        adblockPage.onPageFinished(url)
        // The static home page needs no media or link scripts; other pages get them.
        if (!BridgePagePolicy.isPackagedHomePage(currentOriginalUrl)) evaluateJavascript(genericJsInjects(), null)
        youtubeAdblock.onPage(url)
    }

    private fun genericJsInjects(): String =
        genericInjects ?: context.assets.open(GENERIC_INJECTS_ASSET).bufferedReader().use { it.readText() }.also { genericInjects = it }

    override fun restoreState(inState: Bundle): WebBackForwardList? =
        super.restoreState(inState).also { currentOriginalUrl = url?.toUri() }

    /** In pointer mode the stick and D-pad drive the pointer, not the page. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        if (pointerMode && RemoteInput.isNavigationSource(event)) false else super.dispatchGenericMotionEvent(event)

    fun setVirtualCursorMode(enabled: Boolean) {
        pointerMode = enabled
    }

    /** A preview of the visible page, at most [PREVIEW_MAX_SIDE] px on its long side; [bitmap] is reused if it fits. */
    fun renderThumbnail(bitmap: Bitmap?): Bitmap? {
        if (width == 0 || height == 0) return null
        val scale = minOf(1f, PREVIEW_MAX_SIDE / maxOf(width, height))
        val previewWidth = maxOf(1, (width * scale).toInt())
        val previewHeight = maxOf(1, (height * scale).toInt())
        val preview = bitmap?.takeIf { !it.isRecycled && it.width == previewWidth && it.height == previewHeight }
            ?: try {
                createBitmap(previewWidth, previewHeight, Bitmap.Config.RGB_565)
            } catch (error: Throwable) {
                Log.w(TAG, "No memory for the tab preview", error)
                return null
            }
        val canvas = Canvas(preview)
        val factor = preview.width / width.toFloat()
        canvas.scale(factor, factor)
        canvas.translate(-scrollX.toFloat(), -scrollY.toFloat())
        super.draw(canvas)
        return preview
    }

    fun hideCustomView() = chromeClient.onHideCustomView()

    fun onFilePicked(resultCode: Int, data: Intent?) = chromeClient.onFilePicked(resultCode, data)

    fun onPermissionsResult(permissions: Array<String>, grantResults: IntArray, typeGeo: Boolean) =
        this.permissions.onAndroidPermissionsResult(permissions, grantResults, typeGeo)

    fun onUpdateAdblockSetting(adblockEnabled: Boolean) {
        youtubeAdblock.setEnabled(adblockEnabled)
        adblockPage.setEnabled(adblockEnabled)
        // Malware and phishing protection does not depend on the ad blocking choice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
    }

    override fun destroy() {
        ui.removeCallbacks(reportBlockedAds)
        youtubeAdblock.destroy()
        adblockPage.destroy()
        mediaPolicy.destroy()
        super.destroy()
    }

    companion object {
        const val TAG = "WebViewEx"
        const val WEB_VIEW_TAG = "VireoLumaTV WebView"
        const val INTERNAL_SCHEME = "internal://"
        const val INTERNAL_SCHEME_WARNING_DOMAIN = "warning"
        const val INTERNAL_SCHEME_WARNING_DOMAIN_TYPE_CERT = "certificate"
        private const val JS_INTERFACE_NAME = "VireoLumaTVApp"
        private const val ASSET_BASE = "file:///android_asset/"
        private const val CERTIFICATE_WARNING_ASSET = "pages/warning-certificate.html"
        private const val GENERIC_INJECTS_ASSET = "generic_injects.js"
        private const val BLOCKED_ADS_REPORT_DELAY_MS = 250L
        private const val PREVIEW_MAX_SIDE = 480f
    }
}
