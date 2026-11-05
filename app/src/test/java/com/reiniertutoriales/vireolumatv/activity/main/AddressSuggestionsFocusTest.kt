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

    @Test fun remotePointerCanPickASuggestionWithoutCancellingTheSearch() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val activity = controller.get()
            val bar = BrowserControls(activity)
            val suggestions = AddressSuggestionsView(activity)
            val searched = mutableListOf<String>()
            bar.callback = object : BrowserControls.Callback {
                override fun showAppMenu() {}
                override fun search(text: String) { searched += text }
                override fun onUrlInputDone() { suggestions.clear() }
                override fun focusSuggestions() = suggestions.focusPointer()
                override fun suggestionsHaveFocus() = suggestions.hasFocus()
            }
            suggestions.onPick = { bar.submit(it.url) }
            val screen = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(bar)
                addView(suggestions)
            }
            activity.setContentView(screen)
            val address = bar.findViewById<EditText>(R.id.address_field)
            address.requestFocus()
            address.performClick()
            address.setText("news")
            suggestions.show(listOf(AddressSuggestion("News", "https://news.test", false),
                AddressSuggestion("Other", "https://other.test", false)))
            screen.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            screen.layout(0, 0, 960, 540)
            fun press(view: View, code: Int) {
                view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
            press(address, KeyEvent.KEYCODE_DPAD_DOWN)
            ShadowLooper.idleMainLooper()
            assertTrue("The native list must own the pointer", suggestions.isFocused)
            assertTrue("Entering suggestions preserves the query", bar.isEditingAddress)
            val second = suggestions.row(1)!!
            suggestions.pointerController.position.set(100f, second.top + second.height / 2f)
            press(suggestions, KeyEvent.KEYCODE_DPAD_CENTER)
            ShadowLooper.idleMainLooper()
            assertEquals(listOf("https://other.test"), searched)
            assertFalse(bar.isEditingAddress)
            assertFalse(suggestions.hasSuggestions)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun pointerAtTheTopCanReturnToTheAddress() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val activity = controller.get()
            val address = EditText(activity).apply { id = R.id.address_field }
            val suggestions = AddressSuggestionsView(activity)
            activity.setContentView(LinearLayout(activity).apply { addView(address); addView(suggestions) })
            suggestions.show(listOf(AddressSuggestion("News", "https://news.test", false)))
            assertTrue(suggestions.focusPointer())
            suggestions.pointerController.position.y = 0f
            suggestions.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
            assertTrue(address.hasFocus())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun downEntersSuggestionsUpReturnsAndOkOpensTheSuggestion() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val activity = controller.get()
            val bar = BrowserControls(activity)
            val suggestions = AddressSuggestionsView(activity)
            val searched = mutableListOf<String>()
            val typed = mutableListOf<String>()
            bar.callback = object : BrowserControls.Callback {
                override fun showAppMenu() {}
                override fun search(text: String) { searched += text }
                override fun onUrlInputDone() {}
                override fun onAddressTyped(text: String) { typed += text }
                override fun focusSuggestions() = suggestions.focusFirst()
            }
            suggestions.onPick = { bar.submit(it.url) }
            activity.setContentView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(bar)
                addView(suggestions)
            })
            val viewRoot = activity.window.decorView.parent
            viewRoot.javaClass.getDeclaredMethod("ensureTouchMode", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(viewRoot, false)
            val address = bar.findViewById<EditText>(R.id.address_field)
            // The keyboard may offer its own words, and must not cover the page in full screen.
            assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, address.inputType)
            assertTrue(address.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)

            address.requestFocus()
            address.performClick() // OK on the address starts editing.
            assertTrue(bar.isEditingAddress)
            address.setText("you")
            assertEquals("Typing reports the text for lookup", "you", typed.last())
            suggestions.show(listOf(AddressSuggestion("YouTube", "https://youtube.com", true),
                AddressSuggestion("Noticias", "https://news.test/you", false)))
            ShadowLooper.idleMainLooper()

            fun press(view: View, code: Int) {
                val now = SystemClock.uptimeMillis()
                view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
                view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
            }
            press(address, KeyEvent.KEYCODE_DPAD_DOWN)
            assertTrue("Down enters the suggestions", suggestions.row(0)!!.isFocused)
            assertTrue("Leaving the field for its suggestions keeps the edit", bar.isEditingAddress)
            // Arrow navigation runs in ViewRootImpl; check where Up lands on the laid-out screen.
            val screen = bar.parent as View
            screen.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            screen.layout(0, 0, 960, 540)
            assertSame("Up from the first suggestion returns to the address", address,
                FocusFinder.getInstance().findNextFocus(screen as ViewGroup, suggestions.row(0), View.FOCUS_UP))

            suggestions.row(1)!!.requestFocus()
            press(suggestions.row(1)!!, KeyEvent.KEYCODE_DPAD_DOWN)
            assertTrue("Down stops at the last suggestion", suggestions.row(1)!!.isFocused)

            suggestions.row(1)!!.performClick()
            assertEquals(listOf("https://news.test/you"), searched)
            assertFalse(bar.isEditingAddress)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
