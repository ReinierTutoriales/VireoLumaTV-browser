package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.activity.history.HistoryPages
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import java.util.Locale

/** One address-bar suggestion from the history. */
data class AddressSuggestion(val title: String, val url: String)

/**
 * Local suggestions for the address being typed, from the history, at most [MAX]. Nothing leaves
 * the device. Addresses whose host starts with the text rank first, then titles that start with
 * it, then any other match, keeping the history's own order.
 */
object AddressSuggestions {
    const val MAX = 6

    fun rank(typed: String, history: List<HistoryItem>): List<AddressSuggestion> {
        val query = typed.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<AddressSuggestion>(MAX)
        // Three fixed priority passes keep a stable order without sorting every candidate.
        for (priority in 0 until NO_MATCH) {
            for (item in history) {
                if (result.size == MAX) return result
                val url = item.url.takeIf { it.isNotBlank() } ?: continue
                val title = item.title.takeIf { it.isNotBlank() } ?: url
                if (strength(query, title, url) == priority && seen.add(key(url))) result += AddressSuggestion(title, url)
            }
        }
        return result
    }

    /** Reads candidates from the history; private mode has none, so it suggests nothing. */
    suspend fun load(typed: String, incognito: Boolean): List<AddressSuggestion> {
        val query = typed.trim()
        if (query.isEmpty() || incognito) return emptyList()
        return rank(query, AppDatabase.db.visitDao().search(HistoryPages.likePattern(query)))
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
