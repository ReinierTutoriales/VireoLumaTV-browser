package com.reiniertutoriales.vireolumatv.activity.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMenuTest {
    @Test
    fun entriesKeepDisplayOrderAndEndWithClosingTheApplication() {
        val entries = appMenuEntries()

        assertEquals(AppMenuEntry.entries.toList(), entries)
        assertEquals(AppMenuEntry.CLOSE_APPLICATION, entries.last())
    }
}
