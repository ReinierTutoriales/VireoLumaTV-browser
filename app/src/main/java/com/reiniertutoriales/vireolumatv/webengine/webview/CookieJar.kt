package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.CookieManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Cookie operations that must finish before the caller goes on. */
object CookieJar {
    /**
     * Removes every cookie and writes the empty store to disk. removeAllCookies is asynchronous and
     * answers on the calling thread's Looper, so this runs on the main thread and flushes only
     * after the removal has been confirmed.
     */
    suspend fun clear(cookies: CookieManager = CookieManager.getInstance()) {
        suspendCancellableCoroutine { done ->
            cookies.removeAllCookies {
                cookies.flush()
                done.resume(Unit)
            }
        }
    }
}
