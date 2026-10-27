package com.reiniertutoriales.vireolumatv.activity.main

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabStripKeysTest {
    @Test
    fun downFromFocusedTabsWhileOverlayIsOpenLeavesTheStrip() {
        assertTrue(downLeavesTabStrip(true, true, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0))
    }

    @Test
    fun repeatsReleasesAndOtherKeysDoNotLeaveTheStrip() {
        assertFalse(downLeavesTabStrip(true, true, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 1))
        assertFalse(downLeavesTabStrip(true, true, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_UP, 0))
        assertFalse(downLeavesTabStrip(true, true, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN, 0))
    }

    @Test
    fun onlyTheOpenOverlayWithFocusInTabsIsAffected() {
        assertFalse(downLeavesTabStrip(false, true, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0))
        assertFalse(downLeavesTabStrip(true, false, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0))
    }
}
