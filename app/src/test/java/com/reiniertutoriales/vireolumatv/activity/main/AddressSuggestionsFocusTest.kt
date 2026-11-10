package com.reiniertutoriales.vireolumatv.activity.main

import android.view.View
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.BrowserWindowBinding
import android.widget.LinearLayout
import com.reiniertutoriales.vireolumatv.activity.main.compose.BrowserCommandActions
import com.reiniertutoriales.vireolumatv.activity.main.view.AddressSuggestionsView
import com.reiniertutoriales.vireolumatv.activity.main.view.ComposeControlsHost
import com.reiniertutoriales.vireolumatv.model.BrowserTab
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

    @Test fun upFromTheFirstSuggestionReturnsToTheAddressBeingEdited() {
        lateinit var host: ComposeControlsHost
        lateinit var view: AddressSuggestionsView
        compose.runOnUiThread {
            host = ComposeControlsHost(compose.activity).apply {
                bindActions(BrowserCommandActions({}, {}, {}, {}, {}, {}, {}))
                setTabs(listOf(BrowserTab(title = "Uno")), 0)
            }
            view = AddressSuggestionsView(compose.activity)
            view.onExitUp = { host.focusAddressEditor() }
            host.onFocusSuggestions = { view.focusFirst() }
            compose.activity.setContentView(LinearLayout(compose.activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(host)
                addView(view)
            })
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home_search").performClick()
        compose.runOnIdle { assertTrue(host.isEditingAddress) }
        show(view, listOf(first, second))
        compose.runOnUiThread { assertTrue(view.focusFirst()) }
        compose.waitForIdle()
        assertEquals("suggestion_${first.url}", focusedTag())

        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        assertEquals("home_search", focusedTag())
        compose.runOnIdle {
            assertTrue("Up keeps the edit open", host.isEditingAddress)
            assertFalse(view.hasFocus())
        }
    }

    @Config(qualifiers = "w960dp-h540dp-television-mdpi", fontScale = 1.5f)
    @Test fun allSixEnlargedSuggestionsRemainReachableInsideTheRealBrowserOverlay() {
        lateinit var binding: BrowserWindowBinding
        val items = (1..6).map { AddressSuggestion("History result $it", "https://result$it.test/page", false) }
        val picked = mutableListOf<String>()
        compose.runOnUiThread {
            compose.activity.setTheme(R.style.AppTheme)
            binding = BrowserWindowBinding.inflate(compose.activity.layoutInflater)
            binding.controlsHost.bindActions(BrowserCommandActions({}, {}, {}, {}, {}, {}, {}))
            binding.controlsHost.showTabStrip(false)
            binding.controlsHost.onFocusSuggestions = { binding.vSuggestions.focusFirst() }
            binding.vSuggestions.onExitUp = { binding.controlsHost.focusAddressEditor() }
            binding.vSuggestions.onPick = { picked += it.url }
            binding.startupSpinner.visibility = View.GONE
            compose.activity.setContentView(binding.root)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("home_search").performClick()
        show(binding.vSuggestions, items)
        compose.onNodeWithTag("home_search").performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals("suggestion_${items.first().url}", focusedTag())
        val fullRowHeight = compose.onNodeWithTag("suggestion_${items.first().url}")
            .fetchSemanticsNode().boundsInWindow.height
        repeat(5) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        }
        assertEquals("suggestion_${items.last().url}", focusedTag())
        val last = compose.onNodeWithTag("suggestion_${items.last().url}").fetchSemanticsNode().boundsInWindow
        assertTrue("The last suggestion has a nonempty hit target", last.height > 0f)
        assertEquals("Focusing the last result must reveal its full row", fullRowHeight, last.height, 1f)
        val viewport = IntArray(2)
        compose.runOnUiThread { binding.vSuggestions.getLocationInWindow(viewport) }
        assertTrue("The row stays inside the suggestion viewport", last.top >= viewport[1] &&
            last.bottom <= viewport[1] + binding.vSuggestions.height)

        // The remote's virtual pointer uses View coordinates after D-pad has scrolled the list.
        compose.runOnUiThread {
            assertTrue(binding.vSuggestions.focusPointer())
            binding.vSuggestions.pointerController.position.set(last.center.x - viewport[0], last.center.y - viewport[1])
            binding.vSuggestions.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            binding.vSuggestions.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        }
        compose.waitForIdle()
        assertEquals("Pointer OK hits the visible sixth row exactly once", listOf(items.last().url), picked)
    }
}
