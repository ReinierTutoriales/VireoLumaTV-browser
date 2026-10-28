package com.reiniertutoriales.vireolumatv.webengine

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.reiniertutoriales.vireolumatv.model.WebTabState

/** What the browser needs from its engine module: tabs' engines, the shared cache and a version. */
interface WebEngineProviderCallback {
    fun createWebEngine(tab: WebTabState): WebEngine
    suspend fun clearCache(ctx: Context)
    fun getWebEngineVersionString(): String
}

/**
 * Creates each tab's [WebEngine]. The browser runs on Android WebView only; the app installs that
 * backend at start-up, and tests may replace it.
 */
object WebEngineFactory {
    @Volatile private var backend: WebEngineProviderCallback? = null

    fun install(backend: WebEngineProviderCallback) {
        this.backend = backend
    }

    /** Makes [replacement] the backend; the returned call puts the previous one back. */
    @VisibleForTesting
    fun replaceForTest(replacement: WebEngineProviderCallback): () -> Unit {
        val previous = backend
        backend = replacement
        return { backend = previous }
    }

    private fun backend() = checkNotNull(backend) { "No web engine installed" }

    fun createWebEngine(tab: WebTabState): WebEngine = backend().createWebEngine(tab)

    suspend fun clearCache(ctx: Context) = backend().clearCache(ctx)

    fun getWebEngineVersionString(): String = backend().getWebEngineVersionString()
}
