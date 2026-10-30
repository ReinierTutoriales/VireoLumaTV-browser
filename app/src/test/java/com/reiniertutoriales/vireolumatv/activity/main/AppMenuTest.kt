package com.reiniertutoriales.vireolumatv.activity.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMenuTest {
    @Test
    fun entriesKeepDisplayOrderAndEndWithClosingTheApplication() {
        val entries = appMenuEntries()

        assertFalse(entries.contains(AppMenuEntry.FAVORITES))
        assertEquals(AppMenuEntry.entries.filterNot { it == AppMenuEntry.FAVORITES }, entries)
        assertEquals(AppMenuEntry.CLOSE_APPLICATION, entries.last())
    }
}
