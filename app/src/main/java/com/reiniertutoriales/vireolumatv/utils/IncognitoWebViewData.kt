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
    // Callers check Application.getProcessName(), which is API 28 as well.
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.P)
    fun configure(context: Context) = session.configure(context, WebView::setDataDirectorySuffix)
    fun clear(context: Context) = session.clear(context)
}

/**
 * Up to 2.0.19 private mode also ran before Android 9 by renaming each normal WebView directory
 * to "<name>_backup"; a private session killed before it renamed them back left its own data in
 * their place. Puts every backup back over that data; a directory without a backup is untouched.
 */
internal fun restoreLegacyWebViewBackups(directories: List<File>) {
    for (directory in directories) {
        val backup = File(directory.parentFile, directory.name + "_backup")
        if (!backup.exists()) continue
        if (directory.exists() && !directory.deleteRecursively()) continue
        backup.renameTo(directory)
    }
}
