package com.reiniertutoriales.vireolumatv.singleton

import android.content.Context
import android.view.KeyEvent
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.KeyBinding
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.Shortcut
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.ShortcutMgr
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.ShortcutMgr.KeyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShortcutMgrTest {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("shortcut-test", Context.MODE_PRIVATE)
        .apply { edit().clear().commit() }

    private fun manager() = ShortcutMgr(prefs)

    private fun key(action: Int, code: Int, repeat: Int = 0, meta: Int = 0, scanCode: Int = 0, longPress: Boolean = false) =
        KeyEvent(0, 0, action, code, repeat, meta, 0, scanCode, if (longPress) KeyEvent.FLAG_LONG_PRESS else 0)

    @Test fun defaultsApplyUntilChanged() {
        assertEquals(KeyEvent.KEYCODE_REFRESH, manager().binding(Shortcut.REFRESH_PAGE)?.code)
        assertNull(manager().binding(Shortcut.NAVIGATE_BACK))
    }

    @Test fun aClearedShortcutStaysClearedAfterRestart() {
        manager().set(Shortcut.REFRESH_PAGE, null)
        assertNull("The default must not come back", manager().binding(Shortcut.REFRESH_PAGE))
        manager().set(Shortcut.NAVIGATE_BACK, KeyBinding(KeyEvent.KEYCODE_F1, KeyEvent.META_CTRL_ON, longPress = true))
        assertEquals(KeyBinding(KeyEvent.KEYCODE_F1, KeyEvent.META_CTRL_ON, true), manager().binding(Shortcut.NAVIGATE_BACK))
    }

    @Test fun aShortPressFiresOnceOnRelease() {
        val shortcuts = manager()
        assertEquals(KeyResult(true), shortcuts.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_REFRESH)))
        assertEquals(KeyResult(true, Shortcut.REFRESH_PAGE), shortcuts.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_REFRESH)))
        assertEquals(KeyResult(false), shortcuts.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_REFRESH)))
    }

    @Test fun aLongPressFiresOnlyTheLongBindingOnce() {
        val shortcuts = manager()
        shortcuts.set(Shortcut.NAVIGATE_HOME, KeyBinding(KeyEvent.KEYCODE_REFRESH, longPress = true))
        shortcuts.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_REFRESH))
        assertEquals(KeyResult(true, Shortcut.NAVIGATE_HOME),
            shortcuts.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_REFRESH, repeat = 1, longPress = true)))
        assertEquals(KeyResult(true), shortcuts.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_REFRESH, repeat = 2)))
        // Refresh is the short binding of the same key: releasing after the long press must not fire it.
        assertEquals(KeyResult(true), shortcuts.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_REFRESH)))
    }

    @Test fun aQuickPressOfALongOnlyKeyIsConsumedWholeAndFiresNothing() {
        val shortcuts = manager()
        shortcuts.set(Shortcut.NAVIGATE_HOME, KeyBinding(KeyEvent.KEYCODE_F2, longPress = true))
        assertEquals(KeyResult(true), shortcuts.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F2)))
        assertEquals(KeyResult(true), shortcuts.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F2)))
    }

    @Test fun aKeyWithoutKeyCodeMatchesByItsScanCode() {
        val shortcuts = manager()
        val unknown = key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, scanCode = 0x2ab)
        shortcuts.set(Shortcut.NAVIGATE_BACK, KeyBinding(KeyBinding.codeOf(unknown)))
        assertTrue(shortcuts.onKeyEvent(unknown).consumed)
        assertEquals(Shortcut.NAVIGATE_BACK,
            shortcuts.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN, scanCode = 0x2ab)).fire)
    }

    @Test fun modifiersMustMatch() {
        val shortcuts = manager()
        shortcuts.set(Shortcut.NAVIGATE_BACK, KeyBinding(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON))
        assertEquals(KeyResult(false), shortcuts.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B)))
        assertTrue(shortcuts.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B, meta = KeyEvent.META_CTRL_ON)).consumed)
    }

    @Test fun bindingsReadAsWords() {
        val context = RuntimeEnvironment.getApplication()
        val text = KeyBinding(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.META_CTRL_ON, longPress = true).describe(context)
        assertTrue(text, text.startsWith(context.getString(com.reiniertutoriales.vireolumatv.R.string.shortcut_long_press) + " CTRL+"))
        assertTrue("No raw constant names: $text", "KEYCODE_" !in text)
    }
}
