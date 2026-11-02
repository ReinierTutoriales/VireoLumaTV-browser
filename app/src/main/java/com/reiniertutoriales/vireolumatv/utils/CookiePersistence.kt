package com.reiniertutoriales.vireolumatv.utils

import android.util.Log
import android.webkit.CookieManager
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Writes the WebView cookies to storage when the browser leaves the screen, so a session signed
 * in just before the TV is switched off is still there afterwards. WebView also writes them by
 * itself from time to time, at intervals Android does not document. CookieManager.flush() blocks
 * while it writes, so it runs off the main thread. Private mode is skipped: its cookies are deleted
 * when it ends and are never meant to be kept.
 */
class CookiePersistence(
    private val executor: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "VireoCookies").apply { isDaemon = true }
    },
    private val flush: () -> Unit = { CookieManager.getInstance().flush() },
) {
    fun onLeftScreen(privateMode: Boolean) {
        if (privateMode) return
        executor.execute {
            try {
                flush()
            } catch (error: Exception) {
                // No usable WebView: there are no cookies to keep.
                Log.w("CookiePersistence", "Cookies could not be written", error)
            }
        }
    }
}
