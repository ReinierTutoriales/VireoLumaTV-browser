package com.reiniertutoriales.vireolumatv.activity.main

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.view.AddressSuggestionsView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The remote reaches address suggestions without cancelling the edit, and OK opens one. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class AddressSuggestionsFocusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val first = AddressSuggestion("First", "https://first.test", false)
    private val second = AddressSuggestion("Second", "https://second.test", true)
    private val third = AddressSuggestion("Third", "https://third.test", false)

    @Before fun remote() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun suggestions(): AddressSuggestionsView {
        lateinit var view: AddressSuggestionsView
        compose.runOnUiThread {
            view = AddressSuggestionsView(compose.activity)
            compose.activity.setContentView(view)
        }
        return view
    }

    private fun show(view: AddressSuggestionsView, list: List<AddressSuggestion>) {
        compose.runOnUiThread { view.show(list) }
        compose.waitForIdle()
    }

    private fun focusedTag(): String =
        compose.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag]

    @Test fun anUpdatedTitleIsShownAndTheListHidesWhenEmpty() {
        val view = suggestions()
        show(view, listOf(first, second))
        assertEquals(View.VISIBLE, view.visibility)
        show(view, listOf(first, second.copy(title = "Updated")))
        compose.onNodeWithTag("suggestion_${second.url}").assertTextContains("Updated")
        show(view, emptyList())
        assertEquals(View.GONE, view.visibility)
        assertFalse(view.hasSuggestions)
    }

    @Test fun refreshedSuggestionsKeepTheFocusedAddressAndDownStopsAtTheLast() {
        val view = suggestions()
        show(view, listOf(first, second))
        compose.runOnUiThread { assertTrue(view.focusFirst()) }
        compose.waitForIdle()
        assertEquals("suggestion_${first.url}", focusedTag())
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals("suggestion_${second.url}", focusedTag())
        // Down on the last row stays there.
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals("suggestion_${second.url}", focusedTag())

        show(view, listOf(second.copy(title = "Updated second"), first))
        assertEquals("Focus follows the address, not its old index", "suggestion_${second.url}", focusedTag())

        // A row that used to be last no longer swallows Down after the list grows.
        show(view, listOf(second, first, third))
        compose.onNodeWithTag("suggestion_${first.url}").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals("suggestion_${third.url}", focusedTag())

        show(view, listOf(second))
        assertEquals("Removing the focused result keeps focus inside the remaining results",
            "suggestion_${second.url}", focusedTag())

        // OK, or a click, opens the suggestion (a click leaves remote mode, so it comes last).
        val picked = mutableListOf<String>()
        view.onPick = { picked += it.url }
        compose.onNodeWithTag("suggestion_${second.url}").performClick()
        assertEquals(listOf(second.url), picked)
    }
}
