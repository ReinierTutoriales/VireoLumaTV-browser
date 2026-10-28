package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Looper
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class AppMenuPanelTest {
    private val host = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
        get().setTheme(R.style.AppTheme)
        setup()
    }

    private fun open(onPick: (AppMenuEntry) -> Unit): Pair<AppMenuPanel, LinearLayout> {
        // A remote, not a touch screen: focus moves as on a TV.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val panel = AppMenuPanel(host.get(), appMenuEntries(), onPick)
        panel.show()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return panel to panel.findViewById<LinearLayout>(R.id.menuEntries)!!
    }

    @Test fun everyEntryIsARowInOrderAndTheFirstHasFocus() {
        val (_, list) = open {}
        val rows = (1 until list.childCount).map { list.getChildAt(it) }
        assertEquals(AppMenuEntry.entries.size, rows.size)
        AppMenuEntry.entries.forEachIndexed { index, entry ->
            assertEquals(host.get().getString(entry.label), rows[index].findViewById<TextView>(R.id.tvLabel).text)
            assertTrue("Every entry is a focus stop", rows[index].isFocusable)
        }
        assertTrue("Focus starts on the first entry", rows[0].isFocused)
    }

    @Test fun choosingAnEntryClosesThePanelAndRunsItOnce() {
        val picked = mutableListOf<AppMenuEntry>()
        val (panel, list) = open { picked += it }
        list.getChildAt(1 + AppMenuEntry.SETTINGS.ordinal).performClick()
        assertEquals(listOf(AppMenuEntry.SETTINGS), picked)
        assertFalse(panel.isShowing)
    }

    @Test fun backClosesThePanelWithoutChoosing() {
        val picked = mutableListOf<AppMenuEntry>()
        val (panel, _) = open { picked += it }
        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(panel.isShowing)
        assertTrue(picked.isEmpty())
    }
}
