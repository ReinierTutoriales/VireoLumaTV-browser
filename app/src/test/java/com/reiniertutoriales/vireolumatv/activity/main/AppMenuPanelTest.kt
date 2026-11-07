package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Looper
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class AppMenuPanelTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun tag(entry: AppMenuEntry) = "menu_${entry.name.lowercase()}"

    @Before fun remote() {
        // A remote, not a touch screen: focus moves as on a TV.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun showMenu(onPick: (AppMenuEntry) -> Unit = {}) {
        compose.setContent { VireoTvTheme { TvAppMenu(appMenuEntries(), onPick) } }
        compose.waitForIdle()
    }

    @Test fun everyEntryIsShownInOrderAndTheFirstHasFocus() {
        showMenu()
        appMenuEntries().forEach { entry ->
            compose.onNodeWithTag(tag(entry)).assertExists()
            compose.onNodeWithText(context.getString(entry.label)).assertExists()
        }
        compose.onNodeWithTag(tag(appMenuEntries().first())).assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun dpadDownMovesToTheNextEntry() {
        showMenu()
        val entries = appMenuEntries()
        compose.onNodeWithTag(tag(entries[0])).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(tag(entries[1])).assertIsFocused()
    }

    @Test fun choosingAnEntryRunsItOnce() {
        val picked = mutableListOf<AppMenuEntry>()
        showMenu { picked += it }
        compose.onNodeWithTag(tag(AppMenuEntry.SETTINGS)).performClick()
        assertEquals(listOf(AppMenuEntry.SETTINGS), picked)
    }

    private fun openPanel(onPick: (AppMenuEntry) -> Unit): AppMenuPanel {
        val host = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.AppTheme)
            setup()
        }
        return AppMenuPanel(host.get(), appMenuEntries(), onPick).apply {
            show()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun pickingClosesThePanelAndRunsTheEntryOnce() {
        val picked = mutableListOf<AppMenuEntry>()
        val panel = openPanel { picked += it }
        panel.pick(AppMenuEntry.SETTINGS)
        panel.pick(AppMenuEntry.HISTORY)
        assertEquals(listOf(AppMenuEntry.SETTINGS), picked)
        assertFalse(panel.isShowing)
    }

    @Test fun backClosesThePanelWithoutChoosing() {
        val picked = mutableListOf<AppMenuEntry>()
        val panel = openPanel { picked += it }
        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(panel.isShowing)
        assertTrue(picked.isEmpty())
    }
}
