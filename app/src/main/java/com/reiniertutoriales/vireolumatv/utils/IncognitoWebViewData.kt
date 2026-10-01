package com.reiniertutoriales.vireolumatv.utils

import android.content.Context
import android.webkit.WebView
import java.io.File

internal class IncognitoWebViewSession {
    private var configured = false

    @Synchronized
    fun configure(context: Context, setSuffix: (String) -> Unit) {
        if (configured) return
        clear(context)
        setSuffix("incognito")
        configured = true
    }

    fun clear(context: Context) {
        val root = requireNotNull(context.filesDir.parentFile)
        val directories = listOf(File(root, "app_webview_incognito"),
            File(context.cacheDir, "WebView_incognito"), File(context.cacheDir, "webview_incognito"))
        for (directory in directories) {
            check(!directory.exists() || directory.deleteRecursively()) {
                "Could not clear previous private WebView session"
            }
        }
    }
}

internal object IncognitoWebViewData {
    private val session = IncognitoWebViewSession()
    fun configure(context: Context) = session.configure(context, WebView::setDataDirectorySuffix)
    fun clear(context: Context) = session.clear(context)
}
