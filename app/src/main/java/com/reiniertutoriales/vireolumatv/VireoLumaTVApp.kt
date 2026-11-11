package com.reiniertutoriales.vireolumatv

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.getSystemService
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.singleton.Favicons
import com.reiniertutoriales.vireolumatv.utils.FrameStats
import com.reiniertutoriales.vireolumatv.utils.IncognitoWebViewData
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.webengine.Engines
import com.reiniertutoriales.vireolumatv.webengine.webview.MediaPolicy
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewPageEngine
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Process start-up for both the normal and the private process: settings, the page engine,
 * favicon storage and the downloads notification channel. The private process ends with its
 * browser window, so nothing of a private session outlives it in memory.
 */
class VireoLumaTVApp : Application() {
    /** Set by the private browser window when it closes for good: its process then ends too. */
    @Volatile var exitProcessOnFinish = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        keepLegacyDarkening()
        // The private process gets its own WebView data directory before any WebView exists.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && getProcessName().endsWith(PRIVATE_PROCESS_SUFFIX)) {
            IncognitoWebViewData.configure(this)
        }
        // Before Android 9, private mode moves WebView data when its window starts; do not
        // initialize the provider first in that process. Android 9+ has already set its suffix.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) try {
            WebViewCompat.startUpWebView(this, WebViewStartUpConfig.Builder(webViewStartupExecutor).build(),
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult) {
                        if (!BuildConfig.DEBUG) return
                        Log.d(TAG, "WebView startup UI time=${result.totalTimeInUiThreadMillis} ms, " +
                            "largest task=${result.maxTimePerTaskInUiThreadMillis} ms, " +
                            "blocking locations=${result.uiThreadBlockingStartUpLocations?.size}")
                        result.uiThreadBlockingStartUpLocations?.forEach { location ->
                            Log.d(TAG, "Blocking WebView startup", location.stackInformation)
                        }
                    }
                    override fun onError(error: WebViewStartupException) {
                        Log.w(TAG, "Asynchronous WebView startup failed; normal page startup remains available", error)
                    }
                })
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to start WebView asynchronously; normal page startup remains available", error)
        }
        // Each process (main and :incognito) reads the settings file when it starts. MODE_MULTI_PROCESS
        // only re-read the file on this one call, which happens at process start anyway, and Android
        // deprecates it as unreliable; MODE_PRIVATE keeps the same behavior.
        AppContext.init(this, Config(getSharedPreferences(PREFS_FILE, MODE_PRIVATE)))
        Engines.install(WebViewPageEngine.Backend)
        // Connections opened by the app itself (favicons, filter lists) keep their own cookies.
        java.net.CookieHandler.setDefault(java.net.CookieManager())
        Favicons.store = SiteFavicons
        thread(name = "VireoMediaPolicy", isDaemon = true) { MediaPolicy.prewarm() }
        createDownloadChannel()
        ScreenModels.init(this)
        registerActivityLifecycleCallbacks(EndPrivateProcess)
        FrameStats.install(this)
    }

    /**
     * WebView's own page darkening for Android 9 and older (WebSettingsCompat.setForceDark) is
     * honoured only for apps that declare a target below 33; newer systems use algorithmic darkening.
     */
    private fun keepLegacyDarkening() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) applicationInfo.targetSdkVersion = LEGACY_DARKENING_TARGET
    }

    private fun createDownloadChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(DOWNLOAD_CHANNEL, getString(R.string.menu_downloads), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = getString(R.string.downloads_channel_description) })
    }

    /** Site icons are stored with the site's settings row. */
    private object SiteFavicons : Favicons.Store {
        override suspend fun find(host: String): SiteSettings? = AppDatabase.db.siteSettingsDao().findByHostName(host)
        override suspend fun update(row: SiteSettings) = AppDatabase.db.siteSettingsDao().storeFavicon(row.hostName, row.favicon)
        override suspend fun insert(row: SiteSettings) = AppDatabase.db.siteSettingsDao().storeFavicon(row.hostName, row.favicon)
    }

    private object EndPrivateProcess : ActivityLifecycleCallbacks {
        override fun onActivityDestroyed(activity: Activity) {
            if (activity is MainActivity && instance.exitProcessOnFinish) exitProcess(0)
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    companion object {
        private const val TAG = "VireoLumaTVApp"
        private val webViewStartupExecutor = Executors.newSingleThreadExecutor { work ->
            Thread(work, "VireoWebViewStartup").apply { isDaemon = true }
        }
        lateinit var instance: VireoLumaTVApp
        const val DOWNLOAD_CHANNEL = "downloads"
        const val PREFS_FILE = "main.xml"
        private const val PRIVATE_PROCESS_SUFFIX = ":incognito"
        private const val LEGACY_DARKENING_TARGET = 32
    }
}
