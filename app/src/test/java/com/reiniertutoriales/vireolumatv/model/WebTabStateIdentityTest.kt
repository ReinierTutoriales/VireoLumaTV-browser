package com.reiniertutoriales.vireolumatv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Two tabs on the same address are two tabs. */
class WebTabStateIdentityTest {
    @Test fun equalLookingTabsStayDistinctInLists() {
        val first = BrowserTab(url = "https://same.test", title = "Same")
        val second = BrowserTab(url = "https://same.test", title = "Same")
        assertNotEquals(first, second)
        val tabs = arrayListOf(first, second)
        assertEquals(1, tabs.indexOf(second))
        tabs.remove(second)
        assertSame(first, tabs.single())
    }

    @Test fun aCopyIsADetachedSnapshotWithTheSameStoredValues() {
        val tab = BrowserTab(id = 7, url = "https://a.test", selected = true, position = 3)
        val copy = tab.copy()
        assertNotEquals(tab, copy)
        assertEquals(listOf(7L, "https://a.test", true, 3), listOf(copy.id, copy.url, copy.selected, copy.position))
    }
}
