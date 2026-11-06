package com.reiniertutoriales.vireolumatv.activity.main

import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.view.BrowserControls
import com.reiniertutoriales.vireolumatv.activity.main.view.AddressSuggestionsView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The remote reaches address suggestions without cancelling the edit, and OK opens one. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class AddressSuggestionsFocusTest {
    @Test fun updatingOneSuggestionDoesNotRewriteTheOtherTexts() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val suggestions = AddressSuggestionsView(controller.get())
            controller.get().setContentView(suggestions)
            val first = AddressSuggestion("First", "https://first.test", false)
            val second = AddressSuggestion("Second", "https://second.test", false)
            suggestions.show(listOf(first, second))
            val writes = IntArray(4)
            for (index in 0..1) {
                suggestions.row(index)!!.findViewById<android.widget.TextView>(R.id.title_text)
                    .doAfterTextChanged { writes[index * 2]++ }
                suggestions.row(index)!!.findViewById<android.widget.TextView>(R.id.address_text)
                    .doAfterTextChanged { writes[index * 2 + 1]++ }
            }
            suggestions.show(listOf(first, second.copy(title = "Updated")))
            assertEquals("Only the changed title needs a text update", listOf(0, 0, 1, 0), writes.toList())
            suggestions.show(listOf(first, second.copy(title = "Updated")))
            assertEquals(listOf(0, 0, 1, 0), writes.toList())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun rtlToolbarKeepsComposeHostAndDisabledHistoryState() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val bar = BrowserControls(controller.get()).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                updateNavigation(back = false, forward = false)
            }
            controller.get().setContentView(bar)
            assertEquals(View.LAYOUT_DIRECTION_RTL, bar.layoutDirection)
            assertEquals(1, bar.childCount)
            assertTrue(bar.getChildAt(0) is androidx.compose.ui.platform.ComposeView)
            assertFalse(bar.snapshot.canGoBack)
            assertFalse(bar.snapshot.canGoForward)
            assertFalse(bar.isEditingAddress)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun refreshedSuggestionsReuseRowsAndKeepTheFocusedAddress() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val suggestions = AddressSuggestionsView(controller.get())
            controller.get().setContentView(suggestions)
            val first = AddressSuggestion("First", "https://first.test", false)
            val second = AddressSuggestion("Second", "https://second.test", false)
            suggestions.show(listOf(first, second))
            val row = suggestions.row(0)
            assertTrue(suggestions.row(1)!!.requestFocus())
            suggestions.show(listOf(second.copy(title = "Updated second"), first))
            assertSame("Updating results must reuse existing rows", row, suggestions.row(0))
            assertTrue("Focus follows the address, not its old index", suggestions.row(0)!!.isFocused)
            val picked = mutableListOf<String>()
            suggestions.onPick = { picked += it.url }
            suggestions.row(0)!!.performClick()
            assertEquals(listOf(second.url), picked)
            // A row that used to be last must no longer swallow Down after the list grows.
            suggestions.show(listOf(second, first, AddressSuggestion("Third", "https://third.test", false)))
            val formerLast = suggestions.row(1)!!
            assertFalse(formerLast.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
            suggestions.row(2)!!.requestFocus()
            suggestions.show(listOf(second))
            assertTrue("Removing the focused result keeps focus inside the remaining results", suggestions.row(0)!!.isFocused)
        } finally { controller.pause().stop().destroy() }
    }
}
