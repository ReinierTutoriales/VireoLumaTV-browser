package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * What the remote and gamepad do to the page cursor, observed only through [CursorLayout]'s
 * public dispatch methods and the cursor position, so it holds for any implementation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class RemoteInputContractTest {
    private fun withLayout(test: (CursorLayout) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val config = AppContext.config
        val axesWereDisabled = config.disableMotionAxesDpadNavigation
        try {
            config.disableMotionAxesDpadNavigation = false
            val layout = CursorLayout(controller.get())
            controller.get().setContentView(layout)
            shadowOf(Looper.getMainLooper()).idle() // The first traversal attaches the layout.
            // From here display frames are stepped by [frames] only, so a moving pointer cannot
            // re-run itself forever inside one looper idle.
            ShadowChoreographer.setPaused(true)
            layout.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            layout.layout(0, 0, 960, 540)
            // Start away from the edges: these tests are about movement, not edge scrolling.
            layout.cursorDrawerDelegate.cursorPosition.set(480f, 270f)
            frameStep = CursorDrawerDelegate::class.java.getDeclaredField("cursorUpdateRunnable")
                .apply { isAccessible = true }.get(layout.cursorDrawerDelegate) as Runnable
            test(layout)
        } finally {
            config.disableMotionAxesDpadNavigation = axesWereDisabled
            controller.pause().stop().destroy()
            ShadowChoreographer.setPaused(false)
        }
    }

    private val CursorLayout.pointerX get() = cursorDrawerDelegate.cursorPosition.x
    private val CursorLayout.pointerY get() = cursorDrawerDelegate.cursorPosition.y

    private var frameStep: Runnable? = null

    /**
     * Runs [ms] of 60 Hz display frames. Robolectric does not deliver display frames by itself here,
     * so each step advances the clock, runs the pointer's per-frame step once and then due tasks.
     */
    private fun frames(ms: Long) = repeat((ms / FRAME_MS).toInt()) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(FRAME_MS))
        frameStep?.run()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun key(layout: CursorLayout, action: Int, code: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        return layout.dispatchKeyEvent(KeyEvent(now, now, action, code, 0, 0, 1, 0, 0, InputDevice.SOURCE_DPAD))
    }

    private fun motion(layout: CursorLayout, source: Int, vararg axes: Pair<Int, Float>): Boolean {
        val now = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_UNKNOWN }
        val coords = MotionEvent.PointerCoords().apply { axes.forEach { (axis, value) -> setAxisValue(axis, value) } }
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 2, 0, source, 0)
        return try { layout.dispatchGenericMotionEvent(event) } finally { event.recycle() }
    }

    private fun hat(layout: CursorLayout, x: Float, y: Float = 0f) =
        motion(layout, InputDevice.SOURCE_JOYSTICK, MotionEvent.AXIS_HAT_X to x, MotionEvent.AXIS_HAT_Y to y)

    @Test fun dpadKeyMovesTheCursorOnlyWhileHeld() = withLayout { layout ->
        val start = layout.pointerX
        assertTrue(key(layout, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        frames(200)
        assertTrue("Right must move the cursor right", layout.pointerX > start + 20f)
        assertTrue(key(layout, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        frames(50)
        val stopped = layout.pointerX
        frames(200)
        assertEquals(stopped, layout.pointerX, 0.5f)
    }

    @Test fun hatMovesLikeTheDpadAndStopsWhenCentred() = withLayout { layout ->
        val start = layout.pointerY
        assertTrue(hat(layout, 0f, 1f))
        frames(200)
        assertTrue("Hat down must move the cursor down", layout.pointerY > start + 20f)
        assertTrue(hat(layout, 0f, 0f))
        frames(50)
        val stopped = layout.pointerY
        frames(200)
        assertEquals(stopped, layout.pointerY, 0.5f)
    }

    @Test fun onePressReportedAsHatAndKeyNeverLeavesTheCursorRunning() = withLayout { layout ->
        // Controllers may report one D-pad press on both channels, in either release order.
        for (keyReleasedFirst in listOf(true, false)) {
            assertTrue(hat(layout, 1f))
            assertTrue(key(layout, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            frames(200)
            if (keyReleasedFirst) {
                key(layout, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
                hat(layout, 0f)
            } else {
                hat(layout, 0f)
                key(layout, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
            }
            frames(50)
            val stopped = layout.pointerX
            frames(200)
            assertEquals("Release order keyFirst=$keyReleasedFirst", stopped, layout.pointerX, 0.5f)
        }
    }

    @Test fun leftStickMovesTheCursor() = withLayout { layout ->
        val start = layout.pointerX
        assertTrue(motion(layout, InputDevice.SOURCE_JOYSTICK, MotionEvent.AXIS_X to -1f))
        frames(200)
        assertTrue("Stick left must move the cursor left", layout.pointerX < start - 20f)
        motion(layout, InputDevice.SOURCE_JOYSTICK, MotionEvent.AXIS_X to 0f)
        frames(50)
        val stopped = layout.pointerX
        frames(200)
        assertEquals(stopped, layout.pointerX, 0.5f)
    }

    @Test fun mouseMotionIsLeftToThePage() = withLayout { layout ->
        val start = layout.pointerX
        assertFalse(motion(layout, InputDevice.SOURCE_MOUSE, MotionEvent.AXIS_X to 900f, MotionEvent.AXIS_Y to 10f))
        frames(200)
        assertEquals(start, layout.pointerX, 0.5f)
    }

    @Test fun turningOffStickNavigationKeepsTheHatAndKeysAsDpad() = withLayout { layout ->
        AppContext.config.disableMotionAxesDpadNavigation = true
        val start = layout.pointerX
        // Still consumed: otherwise Android would synthesize D-pad keys from the stick itself.
        assertTrue(motion(layout, InputDevice.SOURCE_JOYSTICK, MotionEvent.AXIS_X to 1f))
        frames(200)
        assertEquals("The stick is off", start, layout.pointerX, 0.5f)
        motion(layout, InputDevice.SOURCE_JOYSTICK, MotionEvent.AXIS_X to 0f)
        assertTrue(hat(layout, 1f))
        frames(200)
        hat(layout, 0f)
        val afterHat = layout.pointerX
        assertTrue("The hat is the gamepad D-pad and keeps working", afterHat > start + 20f)
        assertTrue(key(layout, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        frames(200)
        key(layout, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue("D-pad keys keep working", layout.pointerX > afterHat + 20f)
    }

    @Test fun keyboardClassMotionIsNotMistakenForAGamepad() = withLayout { layout ->
        // SOURCE_KEYBOARD shares the button class bit with SOURCE_DPAD; it is not a D-pad.
        val start = layout.pointerX
        assertFalse(motion(layout, InputDevice.SOURCE_KEYBOARD, MotionEvent.AXIS_HAT_X to 1f, MotionEvent.AXIS_X to 1f))
        frames(200)
        assertEquals(start, layout.pointerX, 0.5f)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
