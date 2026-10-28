package com.reiniertutoriales.vireolumatv.utils

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keys the browser needs for navigation, which the shortcut editor refuses. */
class ReservedKeysTest {
    @Test fun pointerAndBackKeysAreReservedAndMediaKeysAreNot() {
        listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A).forEach {
            assertTrue("key $it", it in RemoteInput.NAVIGATION_KEYS)
        }
        listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE).forEach {
            assertTrue("key $it", it in BackPresses.BACK_KEYS)
        }
        assertFalse(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE in RemoteInput.NAVIGATION_KEYS)
        assertFalse(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE in BackPresses.BACK_KEYS)
    }
}
