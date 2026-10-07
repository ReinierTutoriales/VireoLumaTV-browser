package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Adblock page scripts: site-specific element hiding (top documents) and the window.open decoy
 * (every frame, assets/window_open_decoy.js) that keeps players working when popups are blocked.
 *
 * Site-specific element hiding. A tiny document-start script asks the native side once per top
 * document for that host's CSS (a map lookup), so pages without rules cost one bridge call.
 */
internal class CosmeticFilterController(private val view: WebView) {
    private var enabled = false
    private var registration: ScriptHandler? = null
    private var decoyRegistration: ScriptHandler? = null
    private var decoySource: String? = null
    private fun decoyScript(): String = decoySource ?: view.context.assets.open("window_open_decoy.js")
        .bufferedReader().use { it.readText() }.also { decoySource = it }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            registration?.remove()
            registration = null
            decoyRegistration?.remove()
            decoyRegistration = null
            view.evaluateJavascript(REMOVE_SCRIPT, null)
            return
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            registration = WebViewCompat.addDocumentStartJavaScript(view, INJECT_SCRIPT, setOf("*"))
            // Must run before the page's own scripts in every frame (players live in iframes).
            decoyRegistration = WebViewCompat.addDocumentStartJavaScript(view, decoyScript(), setOf("*"))
        }
    }

    /** Fallback for WebView providers without document-start scripts; idempotent. */
    fun onPageFinished(url: String?) {
        if (enabled && registration == null && url != null &&
            (url.startsWith("http://") || url.startsWith("https://"))) {
            view.evaluateJavascript(decoyScript(), null)
            view.evaluateJavascript(INJECT_SCRIPT, null)
        }
    }

    fun destroy() {
        registration?.remove()
        registration = null
        decoyRegistration?.remove()
        decoyRegistration = null
    }

    companion object {
        private const val STYLE_ID = "__vireoLumaTVCosmetic"
        internal const val INJECT_SCRIPT = """(function(){try{
if(window.top!==window||typeof VireoLumaTVApp==='undefined')return;
var h=location.hostname;if(!h||document.getElementById('$STYLE_ID'))return;
var css=VireoLumaTVApp.cosmeticCss(h);if(!css)return;
var add=function(){var r=document.head||document.documentElement;if(!r)return false;
if(!document.getElementById('$STYLE_ID')){var s=document.createElement('style');s.id='$STYLE_ID';
s.textContent=css;r.appendChild(s);}return true;};
if(!add()){var o=new MutationObserver(function(){if(add())o.disconnect();});
o.observe(document,{childList:true,subtree:true});}
}catch(e){}})();"""
        private const val REMOVE_SCRIPT =
            "(function(){var s=document.getElementById('$STYLE_ID');if(s)s.remove();})();"
    }
}
