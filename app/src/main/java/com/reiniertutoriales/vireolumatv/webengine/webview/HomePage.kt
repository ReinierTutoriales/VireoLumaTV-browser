package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.utils.AppLanguages
import java.io.IOException

/** The history/storage document beneath the native Home; it renders no application controls. */
internal object HomePage {
    const val FILE = "index.html"

    fun render(context: Context, template: String): String {
        val locale = AppLanguages.localeOf(context)
        return CertificateWarningPage.fill(template, mapOf(
            "lang" to locale.toLanguageTag(),
            "dir" to if (AppLanguages.isRtl(locale)) "rtl" else "ltr",
            "title" to context.getString(R.string.home_title),
        ))
    }

    /** Runs on WebView's interception thread; null lets WebView report the missing page. */
    fun response(context: Context, path: String): WebResourceResponse? = try {
        val template = context.assets.open(path).bufferedReader().use { it.readText() }
        WebResourceResponse("text/html", "UTF-8", render(context, template).byteInputStream())
    } catch (error: IOException) {
        Log.w(BrowserWebView.TAG, "Home page not found: $path", error)
        null
    }
}
