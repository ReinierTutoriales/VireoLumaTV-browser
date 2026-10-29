package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.activity.history.HistoryPages
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import java.util.Locale

/** One address-bar suggestion; [bookmark] tells the row to show it as a favorite. */
data class AddressSuggestion(val title: String, val url: String, val bookmark: Boolean)

/**
 * Local suggestions for the address being typed: bookmarks first, then history, at most [MAX].
 * Nothing leaves the device. Within each source, addresses whose host starts with the text rank
 * first, then titles that start with it, then any other match, keeping the source's own order.
 */
object AddressSuggestions {
    const val MAX = 6

    fun rank(typed: String, bookmarks: List<Favorite>, history: List<HistoryItem>): List<AddressSuggestion> {
        val query = typed.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<AddressSuggestion>(MAX)
        // Three fixed priority passes preserve stable order without sorting or materializing
        // a second list of every bookmark. Only the (at most six) selected rows are constructed.
        fun <T> take(items: List<T>, bookmark: Boolean, titleOf: (T) -> String?, urlOf: (T) -> String?) {
            for (priority in 0 until NO_MATCH) {
                for (item in items) {
                    if (result.size == MAX) return
                    val url = urlOf(item) ?: continue
                    if (!bookmark && url.isBlank()) continue
                    val title = titleOf(item)?.takeIf { it.isNotBlank() } ?: url
                    if (strength(query, title, url) == priority && seen.add(key(url))) {
                        result += AddressSuggestion(title, url, bookmark)
                    }
                }
            }
        }
        take(bookmarks, true, { it.title }, { it.url })
        take(history, false, { it.title }, { it.url })
        return result
    }

    /** Reads candidates from the database; history is skipped in incognito mode. */
    suspend fun load(typed: String, incognito: Boolean): List<AddressSuggestion> {
        val query = typed.trim()
        if (query.isEmpty()) return emptyList()
        val bookmarks = AppDatabase.db.favoriteDao().getAll()
        val history = if (incognito) emptyList() else
            AppDatabase.db.visitDao().search(HistoryPages.likePattern(query))
        return rank(query, bookmarks, history)
    }

    private const val NO_MATCH = 3

    private fun strength(query: String, title: String, url: String): Int {
        val host = hostOf(url)
        return when {
            host.startsWith(query) -> 0
            title.lowercase(Locale.ROOT).startsWith(query) -> 1
            url.lowercase(Locale.ROOT).contains(query) ||
                title.lowercase(Locale.ROOT).contains(query) -> 2
            else -> NO_MATCH
        }
    }

    /** "https://www.example.com/a" → "example.com/a": what a user types first. */
    private fun hostOf(url: String): String =
        url.lowercase(Locale.ROOT).substringAfter("://").removePrefix("www.")

    /** One suggestion per address, ignoring the scheme, "www." and a trailing slash. */
    private fun key(url: String): String = hostOf(url).trimEnd('/')
}
