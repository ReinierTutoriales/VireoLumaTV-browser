package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.CookieManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Cookie operations that must finish before the caller goes on. */
object CookieJar {
    /**
     * Removes every cookie and writes the empty store to disk. removeAllCookies is asynchronous and
     * answers on the calling thread's Looper, so this runs on the main thread and flushes only
     * after the removal has been confirmed.
     */
    suspend fun clear(cookies: CookieManager = CookieManager.getInstance()) {
        // Cancellation of the caller must not interrupt the irreversible cookie removal/flush.
        withContext(NonCancellable) { suspendCancellableCoroutine { done ->
            cookies.removeAllCookies {
                cookies.flush()
                if (done.isActive) done.resume(Unit)
            }
        } }
    }
}
