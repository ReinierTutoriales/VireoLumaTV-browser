package com.reiniertutoriales.vireolumatv.webengine.webview

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.reiniertutoriales.vireolumatv.Config

/** How every page view is set up, grouped by what each choice is for. */
internal object PageSettings {
    fun apply(settings: WebSettings, config: Config) {
        content(settings, config)
        sandbox(settings)
        windows(settings)
        darkening(settings, config.darkenPages)
        // Process-wide: every new page view follows the current choice, so turning it off sticks.
        WebView.setWebContentsDebuggingEnabled(config.pageDebugging)
    }

    /** Pages run as on a mobile browser, zoomable with the remote, with their own storage. */
    @SuppressLint("SetJavaScriptEnabled") // A web browser: pages need their scripts.
    private fun content(settings: WebSettings, config: Config) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = !config.allowAutoplayMedia
        // Location still needs the user's answer for each request (PagePermissions).
        settings.setGeolocationEnabled(true)
        // Visible rows already load icons through ViewFaviconLoader with their own bounded cache.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOWNLOAD_FAVICONS_ENABLED)) {
            WebSettingsCompat.setDownloadFaviconsEnabled(settings, false)
        }
    }

    /** No access to the device's files or other apps' content; Safe Browsing always on. */
    private fun sandbox(settings: WebSettings) {
        settings.allowContentAccess = false
        settings.allowFileAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
    }

    /** New windows only through onCreateWindow, where the pop-up rules apply. */
    private fun windows(settings: WebSettings) {
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(true)
        settings.setNeedInitialFocus(false)
    }

    /** The interface is always dark, so the setting alone decides whether pages are darkened. */
    private fun darkening(settings: WebSettings, darken: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darken)
            }
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDark(settings,
                if (darken) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
        }
    }
}
