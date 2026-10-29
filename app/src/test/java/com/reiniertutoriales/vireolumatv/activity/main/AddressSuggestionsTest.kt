package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.model.HistoryItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressSuggestionsTest {
    private fun visit(title: String, url: String) = HistoryItem().apply { this.title = title; this.url = url }

    @Test fun nothingTypedSuggestsNothing() {
        assertTrue(AddressSuggestions.rank("  ", listOf(visit("Ejemplo", "https://example.test"))).isEmpty())
    }

    @Test fun hostPrefixBeatsTitlePrefixAndOtherMatches() {
        val result = AddressSuggestions.rank("you", listOf(
            visit("Noticias para ti", "https://news.test/for-you"),
            visit("Canal de cocina", "https://videos.test/you-cook"),
            visit("Música", "https://youtube.com/music"),
            visit("YouTube", "https://www.youtube.com/")))
        assertEquals(listOf("https://youtube.com/music", "https://www.youtube.com/", "https://news.test/for-you",
            "https://videos.test/you-cook"),
            result.map { it.url })
    }

    @Test fun oneSuggestionPerAddressIgnoringSchemeWwwAndTrailingSlash() {
        val result = AddressSuggestions.rank("example", listOf(
            visit("Ejemplo", "https://www.example.test/"), visit("Ejemplo", "http://example.test"),
            visit("Otra", "https://example.test/otra")))
        assertEquals(listOf("https://www.example.test/", "https://example.test/otra"), result.map { it.url })
    }

    @Test fun aBlankTitleShowsTheAddressAndNonMatchesAreLeftOut() {
        val result = AddressSuggestions.rank("tv", listOf(visit(" ", "https://tv.test"), visit("Radio", "https://radio.test"),
            visit("TV", "")))
        assertEquals(listOf(AddressSuggestion("https://tv.test", "https://tv.test")), result)
    }

    @Test fun atMostSixSuggestions() {
        val history = (1..20).map { visit("Página $it", "https://site$it.test") }
        assertEquals(AddressSuggestions.MAX, AddressSuggestions.rank("site", history).size)
    }

    @Test fun strongestMatchesAtTheEndOfALargeHistoryStillWin() {
        val weaker = (1..10_000).map { visit("A page about TV $it", "https://site$it.test") }
        val strongest = (1..6).map { visit("Channel $it", "https://tv$it.test") }
        assertEquals(strongest.map { it.url }, AddressSuggestions.rank("tv", weaker + strongest).map { it.url })
    }

    @Test fun privateModeSuggestsNothing() = runBlocking {
        assertTrue(AddressSuggestions.load("tv", incognito = true).isEmpty())
    }
}
