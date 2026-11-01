package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.utils.AppLanguages
import org.json.JSONObject
import java.io.IOException

/**
 * The start page in the app's language. Its HTML is a template like the certificate warning: the
 * visible text, lang and dir are filled in, HTML-escaped, when it is served, and the messages its
 * script shows travel in a meta element as JSON, so the page needs no bridge and no extra request.
 */
internal object HomePage {
    const val FILE = "index.html"
    /** The same limit home.js applies to saved shortcuts. */
    private const val MAX_SHORTCUTS = 24

    fun render(context: Context, template: String): String {
        val locale = AppLanguages.localeOf(context)
        val script = JSONObject()
            .put("saveFailed", context.getString(R.string.home_save_failed))
            .put("editPrompt", context.getString(R.string.home_edit_prompt))
            .put("removeConfirm", context.getString(R.string.home_remove_confirm))
            .put("invalid", context.getString(R.string.home_invalid))
            .put("duplicate", context.getString(R.string.home_duplicate))
            .put("edit", context.getString(R.string.home_edit))
            .put("done", context.getString(R.string.home_done))
            .put("youtubeRemoved", context.getString(R.string.home_youtube_removed))
            .put("favoriteManaged", context.getString(R.string.home_favorite_managed, context.getString(R.string.menu_favorites)))
            .put("editItem", context.getString(R.string.home_edit_item))
            .put("youtubeRestore", context.getString(R.string.home_youtube_restore))
            .put("youtubeRemove", context.getString(R.string.home_youtube_remove))
            .put("limit", context.getString(R.string.home_limit, MAX_SHORTCUTS))
            .put("addPrompt", context.getString(R.string.home_add_prompt))
            .put("emptySearch", context.getString(R.string.home_empty_search))
        return CertificateWarningPage.fill(template, mapOf(
            "lang" to locale.toLanguageTag(),
            "dir" to if (AppLanguages.isRtl(locale)) "rtl" else "ltr",
            "title" to context.getString(R.string.home_title),
            "search" to context.getString(R.string.home_search),
            "shortcuts" to context.getString(R.string.home_shortcuts),
            "add" to context.getString(R.string.home_add),
            "edit" to context.getString(R.string.home_edit),
            "editLabel" to context.getString(R.string.home_edit_label),
            "strings" to script.toString(),
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
