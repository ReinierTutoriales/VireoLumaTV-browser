package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** Small origin-scoped content layer; native URL filtering remains independent. */
internal class YouTubeAdblockController(private val view: WebView) {
    private var enabled = false
    private var registration: ScriptHandler? = null
    private fun script(): String = source ?: synchronized(Companion) {
        source ?: AdblockPageController.compact(
            view.context.assets.open("youtube_adblock.js").bufferedReader().use { it.readText() }
        ).also { source = it }
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            registration?.remove()
            registration = null
            if (isYouTube(view.url)) {
                view.evaluateJavascript("window.__vireoYouTubeAdblock && window.__vireoYouTubeAdblock.setEnabled(false)", null)
            }
            return
        }
        if (registration == null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            registration = WebViewCompat.addDocumentStartJavaScript(view, script(), setOf(
                "https://youtube.com", "https://*.youtube.com",
                "https://youtube-nocookie.com", "https://*.youtube-nocookie.com"
            ))
        }
        if (isYouTube(view.url)) view.evaluateJavascript(script(), null)
    }

    /** Best-effort fallback on old providers; document-start registration handles modern providers. */
    fun onPage(url: String?) {
        if (enabled && registration == null && isYouTube(url)) view.evaluateJavascript(script(), null)
    }

    fun destroy() {
        registration?.remove()
        registration = null
    }

    companion object {
        @Volatile private var source: String? = null

        internal fun isYouTube(url: String?): Boolean {
            val uri = url?.let(Uri::parse) ?: return false
            if (uri.scheme != "https") return false
            val host = uri.host?.lowercase(java.util.Locale.ROOT) ?: return false
            return host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
        }
    }
}
