package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Adblock scripts registered at document start in every frame (players usually live in iframes):
 * - assets/adblock/page_filters.js: element hiding and scriptlets chosen by adblock-rust for the
 *   frame's URL (one JavaBridge lookup per document);
 * - assets/window_open_decoy.js: keeps players working when the browser blocks a popup.
 */
internal class AdblockPageController(private val view: WebView) {
    private var enabled = false
    private val registrations = ArrayList<ScriptHandler>(2)

    private fun scripts(): List<String> = sources ?: synchronized(Companion) {
        sources ?: listOf("window_open_decoy.js", "adblock/page_filters.js")
            .map { path -> compact(view.context.assets.open(path).bufferedReader().use { it.readText() }) }
            .also { sources = it }
    }

    companion object {
        // Shared by every WebView: the text is parsed in each frame, so it is read and trimmed once.
        @Volatile private var sources: List<String>? = null

        /**
         * Drops indentation and whole-line comments (about a quarter of the text). Safe for these
         * assets: they contain no multi-line strings or template literals, and line breaks are kept.
         */
        internal fun compact(script: String): String = script.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .joinToString("\n")
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            removeRegistrations()
            return
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            for (script in scripts()) {
                registrations.add(WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*")))
            }
        }
    }

    /** Fallback for WebView providers without document-start scripts (top frame only). */
    fun onPageFinished(url: String?) {
        if (enabled && registrations.isEmpty() && url != null &&
            (url.startsWith("http://") || url.startsWith("https://"))) {
            for (script in scripts()) view.evaluateJavascript(script, null)
        }
    }

    fun destroy() = removeRegistrations()

    private fun removeRegistrations() {
        registrations.forEach { it.remove() }
        registrations.clear()
    }
}
