package com.reiniertutoriales.vireolumatv.webengine.webview

import android.util.Log
import android.webkit.WebResourceResponse
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites.TabFavorites
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * The favorites as read-only data for the home page, served at its own address (favorites.json)
 * only while the home page is the page shown: the page reads it like any of its files, and
 * nothing it does can change a favorite. Other origins cannot read it: the response carries no
 * CORS permission.
 */
internal object HomeFavorites {
    const val PATH = "favorites.json"
    /** Enough to fill the screen; the Favorites panel lists them all. */
    const val MAX = 48

    /** In the order they were added; only web addresses, which the page can open. */
    fun json(favorites: List<Favorite>): String {
        val list = JSONArray()
        favorites.asReversed().asSequence()
            .mapNotNull { item -> TabFavorites.webAddress(item.url)?.let { item.title.orEmpty().trim() to it } }
            .distinctBy { it.second }
            .take(MAX)
            .forEach { (title, url) -> list.put(JSONObject().put("title", title).put("url", url)) }
        return list.toString()
    }

    /** Runs on WebView's interception thread, never the main one: a short read of the table. */
    fun response(read: suspend () -> List<Favorite> = { AppDatabase.db.favoriteDao().getAll() }): WebResourceResponse {
        val body = try {
            json(runBlocking { read() })
        } catch (error: Exception) {
            Log.w(BrowserWebView.TAG, "Favorites for the home page could not be read", error)
            "[]"
        }
        return WebResourceResponse("application/json", "UTF-8", 200, "OK",
            mapOf("Cache-Control" to "no-store"), body.byteInputStream())
    }
}
