package com.reiniertutoriales.vireolumatv.utils

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackNavigationEventsAdapterTest {
    private fun key(time: Long, action: Int, repeats: Int = 0, source: Int = InputDevice.SOURCE_KEYBOARD,
                    flags: Int = 0): KeyEvent = KeyEvent(
        time, time, action, KeyEvent.KEYCODE_BACK, repeats, 0, 0, 0, flags, source,
    )

    private fun motion(time: Long, buttons: Int, source: Int,
                       action: Int = MotionEvent.ACTION_MOVE): MotionEvent = MotionEvent.obtain(
        time, time, action, 1,
        arrayOf(MotionEvent.PointerProperties().apply { id = 0 }),
        arrayOf(MotionEvent.PointerCoords().apply { x = 0f; y = 0f }),
        0, buttons, 1f, 1f, 0, 0, source, 0,
    )

    @Test fun consecutiveRemotePressesEachNavigateAndCanceledPressDoesNot() {
        var navigations = 0
        val adapter = BackPresses({ navigations++ })
        val time = SystemClock.uptimeMillis() + 1000
        adapter.dispatchKeyEvent(key(time, KeyEvent.ACTION_DOWN))
        assertEquals(0, navigations)
        adapter.dispatchKeyEvent(key(time + 10, KeyEvent.ACTION_UP))
        adapter.dispatchKeyEvent(key(time + 70, KeyEvent.ACTION_DOWN))
        adapter.dispatchKeyEvent(key(time + 80, KeyEvent.ACTION_UP))
        assertEquals(2, navigations)
        adapter.dispatchKeyEvent(key(time + 90, KeyEvent.ACTION_DOWN))
        adapter.dispatchKeyEvent(key(time + 100, KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED))
        assertEquals(2, navigations)
    }

    @Test fun controllerShortPressIsIgnoredAndLongPressFiresOnlyOnce() {
        var navigations = 0
        val adapter = BackPresses({ navigations++ },
            holdBGoesBack = true)
        val time = SystemClock.uptimeMillis() + 1000
        adapter.dispatchKeyEvent(key(time, KeyEvent.ACTION_DOWN, source = InputDevice.SOURCE_GAMEPAD))
        adapter.dispatchKeyEvent(key(time + 100, KeyEvent.ACTION_UP, source = InputDevice.SOURCE_GAMEPAD))
        assertEquals(0, navigations)
        adapter.dispatchKeyEvent(key(time + 200, KeyEvent.ACTION_DOWN, source = InputDevice.SOURCE_GAMEPAD))
        adapter.dispatchKeyEvent(key(time + 800, KeyEvent.ACTION_DOWN, 1, InputDevice.SOURCE_GAMEPAD))
        adapter.dispatchKeyEvent(key(time + 850, KeyEvent.ACTION_DOWN, 2, InputDevice.SOURCE_GAMEPAD))
        adapter.dispatchKeyEvent(key(time + 900, KeyEvent.ACTION_UP, source = InputDevice.SOURCE_GAMEPAD))
        assertEquals(1, navigations)
    }

    @Test fun sameGestureAcrossKeyAndMotionChannelsNavigatesOnce() {
        var navigations = 0
        val adapter = BackPresses({ navigations++ })
        val time = SystemClock.uptimeMillis() + 1000
        adapter.dispatchKeyEvent(key(time, KeyEvent.ACTION_DOWN))
        val pressed = motion(time + 10, MotionEvent.BUTTON_BACK, InputDevice.SOURCE_GAMEPAD)
        val released = motion(time + 20, 0, InputDevice.SOURCE_GAMEPAD)
        try {
            assertTrue(adapter.dispatchGenericMotionEvent(pressed))
            assertTrue(adapter.dispatchGenericMotionEvent(released))
            adapter.dispatchKeyEvent(key(time + 30, KeyEvent.ACTION_UP))
            assertEquals(1, navigations)
        } finally {
            pressed.recycle()
            released.recycle()
        }
    }

    @Test fun systemCallbackDuplicatesAnActiveKeyPressOnlyOnce() {
        var navigations = 0
        val adapter = BackPresses({ navigations++ })
        val time = SystemClock.uptimeMillis()
        adapter.dispatchKeyEvent(key(time, KeyEvent.ACTION_DOWN))
        assertFalse(adapter.sendSystemBack())
        adapter.dispatchKeyEvent(key(time + 10, KeyEvent.ACTION_UP))
        assertEquals(1, navigations)
    }

    @Test fun motionLongPressAndCancellationNeverFireOnRelease() {
        var navigations = 0
        val adapter = BackPresses({ navigations++ }, 500,
            holdBGoesBack = true)
        val time = SystemClock.uptimeMillis() + 1000
        val events = listOf(
            motion(time, MotionEvent.BUTTON_SECONDARY, InputDevice.SOURCE_GAMEPAD),
            motion(time + 600, MotionEvent.BUTTON_SECONDARY, InputDevice.SOURCE_GAMEPAD),
            motion(time + 700, MotionEvent.BUTTON_SECONDARY, InputDevice.SOURCE_GAMEPAD),
            motion(time + 800, 0, InputDevice.SOURCE_GAMEPAD),
            motion(time + 900, MotionEvent.BUTTON_SECONDARY, InputDevice.SOURCE_GAMEPAD),
            motion(time + 950, 0, InputDevice.SOURCE_GAMEPAD, MotionEvent.ACTION_CANCEL),
        )
        try {
            events.forEach { assertTrue(adapter.dispatchGenericMotionEvent(it)) }
            assertEquals(1, navigations)
        } finally {
            events.forEach { it.recycle() }
        }
    }

    @Test fun mouseSecondaryButtonIsNotBackButDedicatedBackButtonIs() {
        var navigations = 0
        val adapter = BackPresses({ navigations++ })
        val time = SystemClock.uptimeMillis() + 1000
        val right = motion(time, MotionEvent.BUTTON_SECONDARY, InputDevice.SOURCE_MOUSE)
        val back = motion(time + 100, MotionEvent.BUTTON_BACK, InputDevice.SOURCE_MOUSE)
        val released = motion(time + 110, 0, InputDevice.SOURCE_MOUSE)
        try {
            assertFalse(adapter.dispatchGenericMotionEvent(right))
            assertEquals(0, navigations)
            assertTrue(adapter.dispatchGenericMotionEvent(back))
            assertTrue(adapter.dispatchGenericMotionEvent(released))
            assertEquals(1, navigations)
        } finally {
            right.recycle()
            back.recycle()
            released.recycle()
        }
    }
}
