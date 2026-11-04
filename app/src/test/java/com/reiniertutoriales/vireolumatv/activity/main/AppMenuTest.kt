package com.reiniertutoriales.vireolumatv.activity.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMenuTest {
    @Test
    fun privateModeRequiresIsolatedWebViewDataDirectories() {
        assertTrue(AppMenuEntry.INCOGNITO !in appMenuEntries(24))
        assertTrue(AppMenuEntry.INCOGNITO !in appMenuEntries(27))
        assertTrue(AppMenuEntry.INCOGNITO in appMenuEntries(28))
        assertTrue(AppMenuEntry.INCOGNITO in appMenuEntries(37))
    }

    @Test
    fun entriesKeepDisplayOrderAndEndWithClosingTheApplication() {
        val entries = appMenuEntries()

        // Favorites saved from a tab must stay reachable to be opened, edited and removed.
        assertEquals(listOf(AppMenuEntry.HISTORY, AppMenuEntry.FAVORITES), entries.take(2))
        assertEquals(AppMenuEntry.entries.toList(), entries)
        assertEquals(AppMenuEntry.CLOSE_APPLICATION, entries.last())
    }
}
