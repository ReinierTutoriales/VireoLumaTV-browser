package com.reiniertutoriales.vireolumatv.activity.main.home

import android.net.Uri
import org.json.JSONArray
import java.net.IDN
import java.net.URI
import java.util.Locale

/** The existing origin-local shortcut format; native controls do not move or duplicate its data. */
internal data class HomeShortcuts(val urls: List<String> = emptyList(), val youtubeHidden: Boolean = false) {
    companion object {
        const val MAX = 24
        const val KEY = "vireo.home.shortcuts.v1"
        const val HIDDEN_KEY = "vireo.home.youtube.hidden.v1"
        const val YOUTUBE = "https://m.youtube.com/"

        fun read(raw: String?, hidden: Boolean): HomeShortcuts {
            val data = if (raw != null && raw.length <= 65536) runCatching { JSONArray(raw) }.getOrNull() else null
            val urls = (0 until minOf(data?.length() ?: 0, MAX)).mapNotNull { index ->
                (data?.opt(index) as? String)?.let(::address)
            }.distinct()
            return HomeShortcuts(urls, hidden)
        }

        /** Only ordinary web destinations, with no embedded credentials or executable scheme. */
        fun address(raw: String): String? = runCatching {
            require(raw.length <= 2048)
            val value = raw.trim()
            require(value.isNotEmpty() && value.none { it.isISOControl() || it == '\\' })
            val text = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(value)) value else "https://$value"
            val uri = URI(text)
            val scheme = uri.scheme.lowercase(Locale.ROOT)
            require(scheme == "https" || scheme == "http")
            require(uri.rawUserInfo == null && !uri.rawAuthority.isNullOrEmpty() && !uri.rawAuthority.contains('@'))
            val host = uri.host ?: IDN.toASCII(uri.rawAuthority, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            require(host.isNotBlank())
            val port = uri.port
            require(port in -1..65535)
            val authority = host.lowercase(Locale.ROOT) + if (port != -1 && !(scheme == "https" && port == 443) && !(scheme == "http" && port == 80)) ":$port" else ""
            val path = uri.rawPath.orEmpty().ifEmpty { "/" }
            "$scheme://$authority$path" + (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
        }.getOrNull()?.takeIf { it.length <= 2048 }

        fun isYoutube(url: String) = url in setOf(YOUTUBE, "https://www.youtube.com/", "https://youtube.com/")

        fun search(query: String): String? = query.trim().takeIf { it.isNotEmpty() }?.let {
            "https://duckduckgo.com/?q=${Uri.encode(it)}"
        }
    }
}
