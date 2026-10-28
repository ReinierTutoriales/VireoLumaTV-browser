package com.reiniertutoriales.vireolumatv.activity.main.view.tabs

import com.reiniertutoriales.vireolumatv.model.WebTabState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabsDiffTest {
    private fun tab(id: Long, title: String, url: String = "https://example.test/$id") =
        WebTabState(url = url, title = title, position = 0).apply { this.id = id }

    @Test
    fun sameTabWithANewTitleIsAChangedRow() {
        val before = listOf(tab(1, "Inicio"))
        val after = listOf(tab(1, "Documentación"))

        val callback = TabsAdapter.Diff(before, after)

        assertTrue(callback.areItemsTheSame(0, 0))
        assertFalse("A title change must rebind the row", callback.areContentsTheSame(0, 0))
    }

    @Test
    fun sameTabWithANewUrlIsAChangedRow() {
        val callback = TabsAdapter.Diff(listOf(tab(1, "t", "https://a.test")),
            listOf(tab(1, "t", "https://b.test")))

        assertFalse(callback.areContentsTheSame(0, 0))
    }

    @Test
    fun unchangedTabIsNotRebound() {
        val callback = TabsAdapter.Diff(listOf(tab(1, "t")), listOf(tab(1, "t")))

        assertTrue(callback.areContentsTheSame(0, 0))
    }
}
