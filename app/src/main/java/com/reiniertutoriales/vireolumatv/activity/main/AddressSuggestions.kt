package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.activity.history.HistoryModel
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
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

    fun rank(typed: String, bookmarks: List<FavoriteItem>, history: List<HistoryItem>): List<AddressSuggestion> {
        val query = typed.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<AddressSuggestion>(MAX)
        fun take(candidates: List<AddressSuggestion>) {
            for (candidate in candidates.sortedBy { strength(query, it) }) {
                if (result.size == MAX) return
                if (strength(query, candidate) < NO_MATCH && seen.add(key(candidate.url))) result += candidate
            }
        }
        take(bookmarks.mapNotNull { item ->
            item.url?.let { AddressSuggestion(item.title?.takeIf { it.isNotBlank() } ?: it, it, bookmark = true) }
        })
        take(history.mapNotNull { item ->
            item.url.takeIf { it.isNotBlank() }?.let { AddressSuggestion(item.title.ifBlank { it }, it, bookmark = false) }
        })
        return result
    }

    /** Reads candidates from the database; history is skipped in incognito mode. */
    suspend fun load(typed: String, incognito: Boolean): List<AddressSuggestion> {
        val query = typed.trim()
        if (query.isEmpty()) return emptyList()
        val bookmarks = AppDatabase.db.favoritesDao().getAll()
        val history = if (incognito) emptyList() else
            HistoryModel.likePattern(query).let { AppDatabase.db.historyDao().search(it, it) }
        return rank(query, bookmarks, history)
    }

    private const val NO_MATCH = 3

    private fun strength(query: String, suggestion: AddressSuggestion): Int {
        val host = hostOf(suggestion.url)
        return when {
            host.startsWith(query) -> 0
            suggestion.title.lowercase(Locale.ROOT).startsWith(query) -> 1
            suggestion.url.lowercase(Locale.ROOT).contains(query) ||
                suggestion.title.lowercase(Locale.ROOT).contains(query) -> 2
            else -> NO_MATCH
        }
    }

    /** "https://www.example.com/a" → "example.com/a": what a user types first. */
    private fun hostOf(url: String): String =
        url.lowercase(Locale.ROOT).substringAfter("://").removePrefix("www.")

    /** One suggestion per address, ignoring the scheme, "www." and a trailing slash. */
    private fun key(url: String): String = hostOf(url).trimEnd('/')
}
