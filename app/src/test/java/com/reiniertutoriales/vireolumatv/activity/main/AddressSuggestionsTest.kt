package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressSuggestionsTest {
    private fun bookmark(title: String?, url: String) = Favorite().apply { this.title = title; this.url = url }
    private fun visit(title: String, url: String) = HistoryItem().apply { this.title = title; this.url = url }

    @Test fun nothingTypedSuggestsNothing() {
        assertTrue(AddressSuggestions.rank("  ", listOf(bookmark("Ejemplo", "https://example.test")), emptyList()).isEmpty())
    }

    @Test fun bookmarksComeFirstAndHostPrefixBeatsOtherMatches() {
        val result = AddressSuggestions.rank("you",
            bookmarks = listOf(bookmark("Canal de cocina", "https://videos.test/you-cook"), bookmark("YouTube", "https://www.youtube.com/")),
            history = listOf(visit("Noticias para ti", "https://news.test/for-you"), visit("Música", "https://youtube.com/music")))
        assertEquals(listOf("https://www.youtube.com/", "https://videos.test/you-cook",
            "https://youtube.com/music", "https://news.test/for-you"), result.map { it.url })
        assertEquals(listOf(true, true, false, false), result.map { it.bookmark })
    }

    @Test fun oneSuggestionPerAddressIgnoringSchemeWwwAndTrailingSlash() {
        val result = AddressSuggestions.rank("example",
            bookmarks = listOf(bookmark("Ejemplo", "https://www.example.test/")),
            history = listOf(visit("Ejemplo", "http://example.test"), visit("Otra", "https://example.test/otra")))
        assertEquals(listOf("https://www.example.test/", "https://example.test/otra"), result.map { it.url })
    }

    @Test fun aBlankTitleShowsTheAddressAndNonMatchesAreLeftOut() {
        val result = AddressSuggestions.rank("tv", listOf(bookmark(" ", "https://tv.test"), bookmark("Radio", "https://radio.test")), emptyList())
        assertEquals(listOf(AddressSuggestion("https://tv.test", "https://tv.test", bookmark = true)), result)
    }

    @Test fun atMostSixSuggestions() {
        val history = (1..20).map { visit("Página $it", "https://site$it.test") }
        assertEquals(AddressSuggestions.MAX, AddressSuggestions.rank("site", emptyList(), history).size)
    }
    @Test fun strongestMatchesAtTheEndOfALargeBookmarkSetStillWin() {
        val weaker = (1..10_000).map { bookmark("A page about TV $it", "https://site$it.test") }
        val strongest = (1..6).map { bookmark("Channel $it", "https://tv$it.test") }
        val result = AddressSuggestions.rank("tv", weaker + strongest,
            listOf(visit("TV history", "https://tv-history.test")))
        assertEquals(strongest.map { it.url }, result.map { it.url })
        assertTrue(result.all { it.bookmark })
    }

    @Test fun deduplicationKeepsTheHigherPriorityTitleAndStableOrder() {
        val result = AddressSuggestions.rank("tv", listOf(
            bookmark("A page about tv", "https://example.test"),
            bookmark("TV preferred", "http://www.example.test/"),
            bookmark("TV second", "https://second.test"),
            Favorite().apply { title = "TV folder"; url = null }),
            listOf(visit("TV duplicate", "https://example.test/"), visit("TV third", "https://third.test")))
        assertEquals(listOf("TV preferred", "TV second", "TV third"), result.map { it.title })
        assertEquals(listOf(true, true, false), result.map { it.bookmark })
    }

}
