package com.reiniertutoriales.vireolumatv.webengine

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.reiniertutoriales.vireolumatv.model.BrowserTab

/** The engine implementation: each tab's [PageEngine], the shared cache and a version to show. */
interface EngineBackend {
    fun create(tab: BrowserTab): PageEngine
    suspend fun clearCache(context: Context)
    val version: String
    /** Remote debugging of every page view in this process; settings call it as the switch changes. */
    fun setPageDebugging(enabled: Boolean) {}
}

/** The installed [EngineBackend]. The app installs Android WebView's at start-up; tests may swap it. */
object Engines {
    @Volatile private var backend: EngineBackend? = null

    fun install(backend: EngineBackend) {
        this.backend = backend
    }

    /** Makes [replacement] the backend; the returned call puts the previous one back. */
    @VisibleForTesting
    fun replaceForTest(replacement: EngineBackend): () -> Unit {
        val previous = backend
        backend = replacement
        return { backend = previous }
    }

    private val installed get() = checkNotNull(backend) { "No page engine installed" }

    fun create(tab: BrowserTab): PageEngine = installed.create(tab)

    suspend fun clearCache(context: Context) = installed.clearCache(context)

    val version: String get() = installed.version

    fun setPageDebugging(enabled: Boolean) = installed.setPageDebugging(enabled)
}
