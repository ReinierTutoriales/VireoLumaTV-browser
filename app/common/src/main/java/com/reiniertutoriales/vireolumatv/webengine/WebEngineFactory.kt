package com.reiniertutoriales.vireolumatv.webengine

import android.content.Context
import androidx.annotation.UiThread
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorLayout

interface WebEngineProviderCallback {
    suspend fun initialize(context: Context, webViewContainer: CursorLayout)
    fun createWebEngine(tab: WebTabState): WebEngine
    suspend fun clearCache(ctx: Context)
    fun onThemeSettingUpdated(value: Config.Theme)
    fun getWebEngineVersionString(): String
}

data class WebEngineProvider(
    val name: String,
    val callback: WebEngineProviderCallback
)



object WebEngineFactory {
    const val TAG = "WebEngineFactory"
    private val engineProviders = mutableListOf<WebEngineProvider>()
    private lateinit var initializedProvider: WebEngineProvider

    fun registerProvider(provider: WebEngineProvider) {
        engineProviders.add(provider)
    }

    fun getProviders(): List<WebEngineProvider> {
        return engineProviders
    }

    @UiThread
    suspend fun initialize(context: Context, webViewContainer: CursorLayout) {
        val webEngineProvider = engineProviders.firstOrNull()
            ?: throw IllegalArgumentException("No WebEngineProvider registered")
        webEngineProvider.callback.initialize(context, webViewContainer)
        initializedProvider = webEngineProvider
    }

    fun createWebEngine(tab: WebTabState): WebEngine {
        return initializedProvider.callback.createWebEngine(tab)
    }

    suspend fun clearCache(ctx: Context) {
        initializedProvider.callback.clearCache(ctx)
    }

    fun onThemeSettingUpdated(value: Config.Theme) {
        initializedProvider.callback.onThemeSettingUpdated(value)
    }

    fun getWebEngineVersionString(): String {
        return initializedProvider.callback.getWebEngineVersionString()
    }
}
