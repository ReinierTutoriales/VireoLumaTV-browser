package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.model.dao.FavoriteDao

/** Saves one tab as a bookmark, with the tab's own title and address. */
internal object TabFavorites {
    enum class Result { ADDED, ALREADY_SAVED, NOT_A_WEB_PAGE }

    /**
     * Only web pages are saved: not the browser's own start page, nor blank, data or script
     * addresses. An address already among the bookmarks is not saved twice; its entry is kept.
     */
    suspend fun add(favorites: FavoriteDao, title: String?, url: String?): Result {
        val address = webAddress(url) ?: return Result.NOT_A_WEB_PAGE
        val name = title?.trim()?.takeIf { it.isNotEmpty() } ?: address.toUri().host ?: address
        val item = Favorite().apply {
            this.title = name
            this.url = address
        }
        return if (favorites.insertIfAbsent(item)) Result.ADDED else Result.ALREADY_SAVED
    }

    fun webAddress(url: String?): String? {
        val value = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith(Config.HOME_PAGE_URL, ignoreCase = true)) return null
        val uri = runCatching { value.toUri() }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrEmpty()) return null
        return value
    }
}
