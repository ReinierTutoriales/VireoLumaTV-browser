package com.reiniertutoriales.vireolumatv

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.singleton.Favicons
import com.reiniertutoriales.vireolumatv.utils.IncognitoWebViewData
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.webengine.Engines
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewPageEngine
import java.net.CookieHandler
import java.net.CookieManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Created by PDT on 09.09.2016.
 */
class VireoLumaTVApp : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        lateinit var instance: VireoLumaTVApp
        const val DOWNLOAD_CHANNEL: String = "downloads"
        const val PREFS_FILE = "main.xml"
        val TAG = VireoLumaTVApp::class.simpleName
    }

    lateinit var threadPool: ThreadPoolExecutor
        private set

    var exitProcessOnFinish = false
    var restartAfterExit = false
    override fun onCreate() {
        Log.i(TAG, "onCreate")
        super.onCreate()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            //we need this since when targetSdkVersion >= 33 then
            //deprecated WebSettingsCompat.setForceDark stops working on android 9 and below
            applicationInfo.targetSdkVersion = 32
        }

        instance = this

        // Configure the private process before any WebView/provider initialization.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Application.getProcessName().endsWith(":incognito")) {
            IncognitoWebViewData.configure(this)
        }

        AppContext.init(this, Config(getSharedPreferences(PREFS_FILE, MODE_MULTI_PROCESS)))

        val backgroundThreads = Runtime.getRuntime().availableProcessors()
        threadPool = ThreadPoolExecutor(0, backgroundThreads, 20,
                TimeUnit.SECONDS, ArrayBlockingQueue(backgroundThreads))

        setUpEngine()
        threadPool.execute { com.reiniertutoriales.vireolumatv.webengine.webview.MediaPolicy.prewarm() }

        initNotificationChannels()

        ScreenModels.init(this)


        registerActivityLifecycleCallbacks(this)
    }

    @Suppress("KotlinConstantConditions")
    private fun setUpEngine() {
        Log.i(TAG, "setUpEngine")

        Engines.install(WebViewPageEngine.Backend)

        val cookieManager = CookieManager()
        CookieHandler.setDefault(cookieManager)
        Favicons.store = object : Favicons.Store {
            override fun find(host: String): SiteSettings? {
                return AppDatabase.db.siteSettingsDao().findByHostName(host)
            }

            override suspend fun update(row: SiteSettings) {
                AppDatabase.db.siteSettingsDao().storeFavicon(row.hostName, row.favicon)
            }

            override suspend fun insert(row: SiteSettings) {
                AppDatabase.db.siteSettingsDao().storeFavicon(row.hostName, row.favicon)
            }
        }
    }

    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.menu_downloads)
            val descriptionText = getString(R.string.downloads_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(DOWNLOAD_CHANNEL, name, importance)
            channel.description = descriptionText
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        Log.i(TAG, "onActivityDestroyed: " + activity.javaClass.simpleName)
        if (exitProcessOnFinish && activity is MainActivity) {
            Log.i(TAG, "onActivityDestroyed: exiting process")
            if (restartAfterExit) {
                Log.i(TAG, "onActivityDestroyed: restarting main activity")
                val intent = Intent(this@VireoLumaTVApp, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
            exitProcess(0)
        }
    }
}
