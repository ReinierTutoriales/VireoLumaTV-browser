package com.reiniertutoriales.vireolumatv.adblock

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resources handed to adblock-rust (see `adblock::resources::Resource`): bodies for `$redirect`
 * rules and scriptlet templates for `##+js(...)` rules. Everything here is written for
 * VireoLumaTV; no third-party resource files are bundled.
 *
 * Scriptlet templates do not contain code: each renders to a JSON array `["name",["arg1",...]]`
 * between VIREO/END comment markers, which assets/adblock/page_filters.js executes with its own
 * implementations, so no eval() is needed and page CSP does not apply.
 */
object AdblockResources {
    /** Canonical scriptlet name (as implemented in page_filters.js) to its uBO aliases. */
    internal val scriptlets = linkedMapOf(
        "set-constant" to listOf("set"),
        "abort-on-property-read" to listOf("aopr"),
        "abort-on-property-write" to listOf("aopw"),
        "abort-current-script" to listOf("acs", "abort-current-inline-script", "acis"),
        "abort-on-stack-trace" to listOf("aost"),
        "no-setTimeout-if" to listOf("nostif", "setTimeout-defuser", "std", "prevent-setTimeout"),
        "no-setInterval-if" to listOf("nosiif", "setInterval-defuser", "sid", "prevent-setInterval"),
        "nano-setInterval-booster" to listOf("nano-sib"),
        "nano-setTimeout-booster" to listOf("nano-stb"),
        "addEventListener-defuser" to listOf("aeld", "prevent-addEventListener"),
        "no-window-open-if" to listOf("nowoif", "window.open-defuser", "prevent-window-open"),
        "remove-node-text" to listOf("rmnt"),
        "replace-node-text" to listOf("rpnt", "trusted-rpnt", "trusted-replace-node-text"),
        "no-fetch-if" to listOf("prevent-fetch", "trusted-prevent-fetch"),
        "no-xhr-if" to listOf("prevent-xhr", "trusted-prevent-xhr"),
        "noeval-if" to listOf("prevent-eval-if"),
        "noeval" to listOf("silent-noeval"),
        "json-prune" to emptyList(),
        "remove-attr" to listOf("ra"),
        "remove-class" to listOf("rc"),
        "nowebrtc" to emptyList(),
        "set-cookie" to emptyList(),
        "set-local-storage-item" to emptyList(),
        "popads-dummy" to listOf("popads.net"),
        "nofab" to listOf("fuckadblock.js-3.2.0"),
        "nobab" to listOf("bab-defuser", "nobab2"),
        "json-prune-fetch-response" to emptyList(),
        "json-prune-xhr-response" to emptyList(),
        "trusted-replace-fetch-response" to listOf("trusted-rpfr"),
        "trusted-replace-xhr-response" to emptyList(),
        "xml-prune" to emptyList(),
        "m3u-prune" to emptyList(),
        "trusted-set-constant" to listOf("trusted-set"),
        "set-session-storage-item" to emptyList(),
        "trusted-set-session-storage-item" to emptyList(),
        "trusted-set-local-storage-item" to emptyList(),
        "trusted-set-cookie" to emptyList(),
        "trusted-set-cookie-reload" to emptyList(),
        "remove-cookie" to listOf("cookie-remover"),
        "set-attr" to emptyList(),
        "trusted-set-attr" to emptyList(),
        "href-sanitizer" to emptyList(),
        "refresh-defuser" to emptyList(),
        "disable-newtab-links" to emptyList(),
        "window-close-if" to emptyList(),
        "alert-buster" to emptyList(),
        "no-requestAnimationFrame-if" to listOf("norafif", "requestAnimationFrame-defuser", "prevent-requestAnimationFrame"),
        "prevent-canvas" to emptyList(),
        "prevent-innerHTML" to emptyList(),
        "trusted-replace-argument" to emptyList(),
        "trusted-suppress-native-method" to emptyList(),
        "trusted-override-element-method" to emptyList(),
        "spoof-css" to emptyList(),
        "trusted-click-element" to emptyList(),
        "trusted-create-html" to emptyList()
    )

    private const val GIF_1X1 = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
    private const val PNG_2X2 = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAC0lEQVR42mNgQAcAABIAAeRVjecAAAAASUVORK5CYII="
    private const val PNG_3X2 = "iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAYAAACddGYaAAAAC0lEQVR42mNgwAUAABoAAS+Yl6YAAAAASUVORK5CYII="
    private const val PNG_32X32 = "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAAGklEQVR42u3BAQEAAACCIP+vbkhAAQAAAO8GECAAAcm1w7EAAAAASUVORK5CYII="

    @Volatile private var cached: String? = null

    fun json(context: Context): String = cached ?: synchronized(this) {
        cached ?: build(context).also { cached = it }
    }

    private fun build(context: Context): String {
        val list = JSONArray()
        fun text(name: String, mime: String, body: String, vararg aliases: String) =
            list.put(resource(name, aliases.toList(), JSONObject().put("mime", mime), base64(body.toByteArray())))
        fun binary(name: String, mime: String, base64Body: String, vararg aliases: String) =
            list.put(resource(name, aliases.toList(), JSONObject().put("mime", mime), base64Body))
        fun asset(name: String, path: String, vararg aliases: String) = text(name, "application/javascript",
            context.assets.open(path).bufferedReader().use { it.readText() }, *aliases)
        fun media(name: String, mime: String, path: String, vararg aliases: String) =
            binary(name, mime, base64(context.assets.open(path).use { it.readBytes() }), *aliases)

        text("noopjs", "application/javascript", "(function(){})();", "noop.js", "blank-js")
        text("nooptext", "text/plain", "", "noop.txt", "blank-text")
        text("noopcss", "text/css", "", "noop.css", "blank-css")
        text("noopjson", "application/json", "{}", "noop.json")
        text("noopframe", "text/html", "<!DOCTYPE html><html><head></head><body></body></html>", "noop.html", "blank-html")
        text("empty", "text/plain", "")
        val vast = { version: String -> "<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"$version\"></VAST>" }
        // Empty ad responses: video players then go straight to the content.
        list.put(resource("noop-vast2.xml", listOf("noopvast-2.0"), JSONObject().put("mime", "text/xml"), base64(vast("2.0").toByteArray())))
        list.put(resource("noop-vast3.xml", listOf("noopvast-3.0"), JSONObject().put("mime", "text/xml"), base64(vast("3.0").toByteArray())))
        list.put(resource("noop-vast4.xml", listOf("noopvast-4.0"), JSONObject().put("mime", "text/xml"), base64(vast("4.0").toByteArray())))
        text("noop-vmap1.xml", "text/xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><vmap:VMAP xmlns:vmap=\"http://www.iab.net/videosuite/vmap\" version=\"1.0\"></vmap:VMAP>", "noopvmap-1.0")
        binary("1x1.gif", "image/gif", GIF_1X1, "1x1-transparent.gif")
        binary("2x2.png", "image/png", PNG_2X2, "2x2-transparent.png")
        binary("3x2.png", "image/png", PNG_3X2, "3x2-transparent.png")
        binary("32x32.png", "image/png", PNG_32X32, "32x32-transparent.png")
        // Silent clips for blocked ad media: players see a playable file that ends at once.
        media("noop-0.1s.mp3", "audio/mp3", "adblock/noop-0.1s.mp3", "noopmp3-0.1s", "abp-resource:blank-mp3")
        media("noop-1s.mp4", "video/mp4", "adblock/noop-1s.mp4", "noopmp4-1s", "abp-resource:blank-mp4")
        // IMA SDK stand-in: answers every ad request with "no ads", players start the content.
        asset("google-ima.js", "surrogates/google-ima.js", "google-ima3", "ima3.js")
        text("fingerprint2.js", "application/javascript", "(function(){var h='74656c6c6d65';" +
            "function F(){}F.prototype.get=function(a,b){var c=typeof a==='function'?a:b;" +
            "if(typeof c==='function')setTimeout(function(){c(h,[])},1)};" +
            "F.get=function(a,b){var c=typeof a==='function'?a:b;if(typeof c==='function')setTimeout(function(){c([])},1)};" +
            "F.getPromise=function(){return Promise.resolve([])};F.getV18=F.get;" +
            "F.x64hash128=function(){return h};F.VERSION='2.1.0';window.Fingerprint2=F;})();", "fingerprint2")
        asset("googlesyndication_adsbygoogle.js", "surrogates/adsbygoogle.js", "googlesyndication.com/adsbygoogle.js")
        asset("googletagservices_gpt.js", "surrogates/gpt.js", "googletagservices.com/gpt.js")
        asset("google-analytics_analytics.js", "surrogates/analytics.js", "google-analytics.com/analytics.js", "googletagmanager_gtag.js")
        asset("googletagmanager_gtm.js", "surrogates/gtm.js", "googletagmanager.com/gtm.js")
        text("prebid-ads.js", "application/javascript", "(function(){window.canRunAds=true;window.isAdBlockActive=false;})();")
        text("amazon_apstag.js", "application/javascript", "(function(){var noop=function(){};" +
            "window.apstag={init:noop,setDisplayBids:noop,targetingKeys:function(){return[]}," +
            "fetchBids:function(c,cb){if(typeof cb==='function')setTimeout(function(){cb([])},1)}};})();",
            "amazon-adsystem.com/aax2/amzn_ads.js")

        for ((name, aliases) in scriptlets) {
            val placeholders = (1..9).joinToString(",") { "\"{{$it}}\"" }
            val template = "/*@VIREO@*/[\"$name\",[$placeholders]]/*@END@*/"
            val names = (listOf(name) + aliases).flatMap { listOf(it, "$it.js") }.distinct()
            list.put(resource("$name.js", names.filter { it != "$name.js" }, "template", base64(template.toByteArray())))
        }
        return list.toString()
    }

    private fun resource(name: String, aliases: List<String>, kind: Any, content: String) =
        JSONObject().put("name", name).put("aliases", JSONArray(aliases)).put("kind", kind).put("content", content)

    private fun base64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
