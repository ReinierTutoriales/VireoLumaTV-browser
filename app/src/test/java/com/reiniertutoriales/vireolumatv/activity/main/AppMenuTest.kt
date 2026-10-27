package com.reiniertutoriales.vireolumatv.activity.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMenuTest {
    @Test
    fun entriesKeepDisplayOrderAndEndWithClosingTheApplication() {
        val entries = appMenuEntries(voiceSearchAvailable = true)

        assertEquals(AppMenuEntry.entries, entries)
        assertEquals(AppMenuEntry.CLOSE_APPLICATION, entries.last())
    }

    @Test
    fun voiceSearchIsHiddenWhenUnavailable() {
        val entries = appMenuEntries(voiceSearchAvailable = false)

        assertFalse(AppMenuEntry.VOICE_SEARCH in entries)
        assertTrue(AppMenuEntry.HISTORY in entries)
    }
}
