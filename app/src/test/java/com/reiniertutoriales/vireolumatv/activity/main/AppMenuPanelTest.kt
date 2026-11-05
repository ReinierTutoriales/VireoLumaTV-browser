package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Looper
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performClick
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
    private val host = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
        get().setTheme(R.style.AppTheme)
        setup()
    }

    private fun open(onPick: (AppMenuEntry) -> Unit): AppMenuPanel {
        lateinit var panel: AppMenuPanel
        compose.runOnIdle {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            panel = AppMenuPanel(host.get(), appMenuEntries(), onPick)
            panel.show()
        }
        compose.waitForIdle()
        return panel
    }

    @Test fun everyEntryIsAvailableAndTheFirstHasFocus() {
        val panel = open {}
        appMenuEntries().forEach { entry ->
            compose.onNodeWithText(host.get().getString(entry.label)).assertExists()
        }
        compose.onNodeWithText(host.get().getString(appMenuEntries().first().label)).assertIsFocused()
        compose.runOnIdle { panel.dismiss() }
    }

    @Test fun dpadMovesToNextEntryAndDoesNotEscapeAboveFirst() {
        val panel = open {}
        val entries = appMenuEntries()
        val first = compose.onNodeWithText(host.get().getString(entries.first().label))
        first.performKeyInput { keyDown(Key.DirectionUp); keyUp(Key.DirectionUp) }
        first.assertIsFocused()
        first.performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        compose.onNodeWithText(host.get().getString(entries[1].label)).assertIsFocused()
        compose.runOnIdle { panel.dismiss() }
    }

    @Test fun choosingAnEntryClosesThePanelAndRunsItOnce() {
        val picked = mutableListOf<AppMenuEntry>()
        val panel = open { picked += it }
        compose.onNodeWithText(host.get().getString(AppMenuEntry.SETTINGS.label)).performClick()
        assertEquals(listOf(AppMenuEntry.SETTINGS), picked)
        assertFalse(panel.isShowing)
    }

    @Test fun backClosesThePanelWithoutChoosing() {
        val picked = mutableListOf<AppMenuEntry>()
        val panel = open { picked += it }
        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(panel.isShowing)
        assertTrue(picked.isEmpty())
    }
}
