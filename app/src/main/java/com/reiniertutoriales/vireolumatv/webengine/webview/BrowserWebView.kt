package com.reiniertutoriales.vireolumatv.webengine.webview

import com.reiniertutoriales.vireolumatv.utils.AppLanguages
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
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
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
open class BrowserWebView(context: Context, internal val engine: WebViewPageEngine) : WebView(context) {
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
    private var pageScript: String? = null
    internal val usesWebMessageBridge =WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    /** The address the browser asked for, before redirects; the base for ad and home page rules. */
    @Volatile var currentOriginalUrl: Uri? = null
    /** The certificate error shown on the warning page. */
    @Volatile var lastSSLError: SslError? = null
    /**
     * The certificate error the user chose to trust once on the warning page: it lets exactly that
     * certificate through for its own host, and is dropped by the next committed page or by a load
     * of another host, so an unused approval can never be spent later or elsewhere.
     */
    @Volatile internal var trustedCertificate: CertificateTrust.Approval? = null
    /**
     * The address whose certificate error WebView now lets through by itself: after proceed() it
     * remembers that host and error for the whole process, which on a TV can mean days. The visit
     * ends on a page of another host or when the view goes, and then that memory is cleared.
     */
    private var proceededUrl: String? = null
    @VisibleForTesting internal var forgetProceededCertificates: () -> Unit = { clearSslPreferences() }

    /** WebView was told to proceed past the certificate error of [url]. */
    internal fun onCertificateProceeded(url: String?) {
        proceededUrl = url
    }

    private fun endCertificateVisit(next: String?) {
        val proceeded = proceededUrl ?: return
        if (next != null && CertificateTrust.sameHost(proceeded, next)) return
        proceededUrl = null
        forgetProceededCertificates()
    }
    @Volatile var certificateErrorPageUrl: String? = null
        private set

    private val ui = Handler(Looper.getMainLooper())
    private val pendingBlockedAds = AtomicInteger(0)
    private val reportBlockedAds = Runnable {
        val count = pendingBlockedAds.getAndSet(0)
        if (count > 0) window?.onAdsBlocked(count)
    }

    init {
        PageSettings.apply(settings, config)
        // A long press opens the pointer menu instead of WebView's own selection.
        setOnLongClickListener { true }
        webChromeClient = chromeClient
        val pageClient = BrowserWebViewClient(this)
        webViewClient = pageClient
        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            pageClient.onTopLevelLoadEnded()
            // Blob downloads are read by the injected script; everything else goes to the download service.
            if (!url.startsWith("blob:")) window?.onPageDownload(url, userAgent, contentDisposition, mimetype, contentLength)
        }
        // Filters stay synchronous for every frame; privileged calls join them only where no
        // WebMessage listener can tell the main frame from an embedded one.
        addJavascriptInterface(if (usesWebMessageBridge) FilterBridge(engine) else LegacyPrivilegedBridge(engine),
            JS_INTERFACE_NAME)
        if (usesWebMessageBridge) {
            // Top-level warning only. An embedded frame cannot approve another site's certificate.
            WebViewCompat.addWebMessageListener(this, "VireoCertificate", setOf("*")) {
                    _, message, _, isMainFrame, _ ->
                if (mayApproveCertificateMessage(isMainFrame, message.data, url, ASSET_BASE,
                        shownCertificateError() != null)) {
                    engine.privileged.approveCertificate()
                }
            }
            // Blob downloads of the visible page's own main frame; see DownloadMessage.
            WebViewCompat.addWebMessageListener(this, "VireoDownload", setOf("*")) {
                    _, message, sourceOrigin, isMainFrame, reply ->
                val document = downloadMessageDocument(isMainFrame, sourceOrigin, url?.toUri())
                    ?: return@addWebMessageListener
                val request = DownloadMessage.parse(message.data) ?: return@addWebMessageListener
                engine.privileged.onDownloadMessage(request, document, { webOrigin(url?.toUri()) }) {
                    reply.postMessage(it)
                }
            }
        }
        // Before the first navigation, so the first page already sees the codec policy.
        mediaPolicy.refresh()
    }

    /**
     * Loads [url]: "about:home" opens the home page the settings choose, internal warning pages
     * come from assets, anything else is loaded as it is.
     */
    override fun loadUrl(url: String) {
        if (url != certificateErrorPageUrl) certificateErrorPageUrl = null
        trustedCertificate?.let { if (!CertificateTrust.sameHost(it.url, url)) trustedCertificate = null }
        if (url.startsWith(APP_SCHEME)) return loadInternal(url.toUri())
        val target = if (url == Config.HOME_URL_ALIAS) homeAddress() else url
        if (target == null) return loadBlank()
        // The address asked for, before any redirect: the base for ad and home page rules.
        currentOriginalUrl = target.toUri()
        super.loadUrl(target)
    }

    /** Where "about:home" leads with the current settings; null for a blank page. */
    private fun homeAddress(): String? = when (config.homePageMode) {
        Config.HomePageMode.BLANK -> null
        Config.HomePageMode.HOME_PAGE -> Config.HOME_PAGE_URL
        Config.HomePageMode.CUSTOM, Config.HomePageMode.SEARCH_ENGINE ->
            config.homePage.takeIf { it.isNotBlank() } ?: Config.HOME_PAGE_URL
    }

    private fun loadBlank() = loadDataWithBaseURL(null, "", "text/html", "UTF-8", null)

    /** The certificate warning is the only internal page, and only for the error being shown. */
    private fun loadInternal(uri: Uri) {
        if (uri.authority != WARNING_HOST || uri.getQueryParameter("type") != WARNING_CERT_PATH) return
        val error = lastSSLError ?: return
        val template = context.assets.open(CERTIFICATE_WARNING_ASSET).bufferedReader().use { it.readText() }
        loadDataWithBaseURL(ASSET_BASE, CertificateWarningPage.render(AppLanguages.localized(context), template, error.url, error.primaryError,
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
        val url = "$APP_SCHEME$WARNING_HOST?type=$WARNING_CERT_PATH&url=" +
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
        // Refresh cached policy before the fallback injection, so a changed setting
        // cannot inject the previous document policy on older WebView providers.
        mediaPolicy.refresh()
        mediaPolicy.onPageStarted()
        if (url != ASSET_BASE) {
            certificateErrorPageUrl = null
            // A page committed: the approval was used for it or is no longer needed.
            trustedCertificate = null
            endCertificateVisit(url)
        }
    }

    internal fun onDocumentStartReported(url: String) = youtubeAdblock.onPage(url)

    internal fun onDocumentFinished(url: String?) {
        adblockPage.onPageFinished(url)
        // The static home page needs no media or link scripts; other pages get them.
        if (!BridgePagePolicy.isPackagedHomePage(currentOriginalUrl)) evaluateJavascript(genericJsInjects(), null)
        youtubeAdblock.onPage(url)
    }

    private fun genericJsInjects(): String =
        pageScript ?: context.assets.open(GENERIC_INJECTS_ASSET).bufferedReader().use { it.readText() }.also { pageScript = it }

    override fun restoreState(inState: Bundle): WebBackForwardList? =
        super.restoreState(inState).also { currentOriginalUrl = url?.toUri() }

    /** In pointer mode the stick and D-pad drive the pointer, not the page. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        if (pointerMode && RemoteInput.isNavigationSource(event)) false else super.dispatchGenericMotionEvent(event)

    fun usePointer(enabled: Boolean) {
        pointerMode = enabled
    }

    /** A preview of the visible page, at most [PREVIEW_MAX_SIDE] px on its long side; [bitmap] is reused if it fits. */
    fun drawPreview(bitmap: Bitmap?): Bitmap? {
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

    fun onPermissionsResult(permissions: Array<String>, grantResults: IntArray, typeGeo: Boolean, generation: Long) =
        this.permissions.onAndroidPermissionsResult(permissions, grantResults, typeGeo, generation)

    fun onBlockerToggled(adblockEnabled: Boolean) {
        youtubeAdblock.setEnabled(adblockEnabled)
        adblockPage.setEnabled(adblockEnabled)
        // Malware and phishing protection does not depend on the ad blocking choice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
    }

    override fun destroy() {
        endCertificateVisit(null)
        permissions.destroy()
        ui.removeCallbacks(reportBlockedAds)
        youtubeAdblock.destroy()
        adblockPage.destroy()
        mediaPolicy.destroy()
        super.destroy()
    }

    companion object {
        const val TAG = "BrowserWebView"
        const val LOG_TAG = "VireoLumaTV WebView"
        const val APP_SCHEME = "internal://"
        const val WARNING_HOST = "warning"
        const val WARNING_CERT_PATH = "certificate"
        private const val JS_INTERFACE_NAME = "VireoLumaTVApp"
        private const val ASSET_BASE = "file:///android_asset/"
        private const val CERTIFICATE_WARNING_ASSET = "pages/warning-certificate.html"
        private const val GENERIC_INJECTS_ASSET = "generic_injects.js"
        private const val BLOCKED_ADS_REPORT_DELAY_MS = 250L
        private const val PREVIEW_MAX_SIDE = 480f
    }
}

/** One-way gate for privileged warning-page messages, never trust a web subframe. */
internal fun mayApproveCertificateMessage(
    isMainFrame: Boolean,
    command: String?,
    documentUrl: String?,
    warningPageUrl: String,
    warningActive: Boolean,
): Boolean = isMainFrame && command == "trustCertificateOnce" &&
    documentUrl == warningPageUrl && warningActive
