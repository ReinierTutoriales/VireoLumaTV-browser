package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.widgets.cursor.PointerController
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class VirtualCursorPointerTest {
    private class Surface(activity: Activity) : FrameLayout(activity) {
        val touch = mutableListOf<MotionEvent>()
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            touch += MotionEvent.obtain(event)
            return super.dispatchTouchEvent(event)
        }
    }

    private fun withCursor(test: (PointerController, Surface, IntArray) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val surface = Surface(activity)
        val clicks = intArrayOf(0, 0)
        repeat(2) { index ->
            surface.addView(View(activity).apply {
                setOnClickListener { clicks[index]++ }
            }, FrameLayout.LayoutParams(400, 800).apply { leftMargin = index * 400 })
        }
        activity.setContentView(surface)
        surface.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        surface.layout(0, 0, 800, 800)
        val delegate = PointerController(activity, surface)
        delegate.cursorPosition.set(123f, 456f)
        delegate.fadeIn()
        try { test(delegate, surface, clicks) } finally {
            surface.touch.forEach { it.recycle() }
            controller.pause().stop().destroy()
        }
    }

    private fun key(delegate: PointerController, action: Int, code: Int = KeyEvent.KEYCODE_DPAD_CENTER) {
        assertTrue(delegate.dispatchKeyEvent(KeyEvent(action, code)))
    }

    @Test fun remoteTapClicksOnlyTheControlUnderTheCursor() = withCursor { delegate, surface, clicks ->
        key(delegate, KeyEvent.ACTION_DOWN)
        key(delegate, KeyEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertArrayEquals(intArrayOf(1, 0), clicks)
        delegate.cursorPosition.set(523f, 456f)
        key(delegate, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        key(delegate, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
        shadowOf(Looper.getMainLooper()).idle()
        assertArrayEquals(intArrayOf(1, 1), clicks)
        assertEquals(listOf(0, 1, 0, 1), surface.touch.map { it.actionMasked })
        surface.touch.forEach {
            assertEquals(InputDevice.SOURCE_TOUCHSCREEN, it.source)
            assertEquals(MotionEvent.TOOL_TYPE_FINGER, it.getToolType(0))
            assertEquals(456f, it.y, 0f)
        }
        assertEquals(123f, surface.touch[0].x, 0f)
        assertEquals(523f, surface.touch[2].x, 0f)
        assertEquals(surface.touch[0].downTime, surface.touch[1].downTime)
    }

    @Test fun selectionAliasesDoNotDuplicateTheTap() = withCursor { delegate, surface, clicks ->
        key(delegate, KeyEvent.ACTION_DOWN)
        key(delegate, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)
        key(delegate, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)
        key(delegate, KeyEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertArrayEquals(intArrayOf(1, 0), clicks)
        assertEquals(listOf(0, 1), surface.touch.map { it.actionMasked })
    }

    @Test fun longPressCancelsTapAndTheNextTapStillWorks() = withCursor { delegate, surface, clicks ->
        var longPresses = 0
        delegate.callback = object : PointerController.Callback {
            override fun onLongPress(x: Int, y: Int) {
                assertEquals(123, x)
                assertEquals(456, y)
                longPresses++
            }
        }
        key(delegate, KeyEvent.ACTION_DOWN)
        val runnable = PointerController::class.java.getDeclaredField("holdTimeout")
            .apply { isAccessible = true }.get(delegate) as Runnable
        runnable.run()
        key(delegate, KeyEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, longPresses)
        assertArrayEquals(intArrayOf(0, 0), clicks)
        assertEquals(listOf(0, 3), surface.touch.map { it.actionMasked })
        key(delegate, KeyEvent.ACTION_DOWN)
        key(delegate, KeyEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertArrayEquals(intArrayOf(1, 0), clicks)
    }

    /** Distance covered while Right is held for [holdMs], rendering one frame every [frameMs]. */
    private fun distanceWhileHolding(frameMs: Long, holdMs: Long): Float {
        var distance = 0f
        withCursor { delegate, _, _ ->
            delegate.init()
            delegate.cursorPosition.set(10f, 400f)
            val update = PointerController::class.java.getDeclaredField("pointerTick")
                .apply { isAccessible = true }.get(delegate) as Runnable
            assertTrue(delegate.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
            val end = SystemClock.uptimeMillis() + holdMs
            while (SystemClock.uptimeMillis() < end) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(frameMs))
                update.run()
            }
            distance = delegate.cursorPosition.x - 10f
            delegate.resetInput()
        }
        return distance
    }

    private fun initConfig() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        AppContext.init(app, com.reiniertutoriales.vireolumatv.Config(app.getSharedPreferences("cursor-speed-test", 0)))
    }

    /**
     * Distance along x after [holdMs] at 60 fps, with [input] called 8 ms after every frame. Input
     * events arrive between frames on a real device (key repeats, stick samples).
     */
    private fun distanceWith(holdMs: Long, start: (PointerController) -> Unit, input: (PointerController, Int) -> Unit): Float {
        var distance = 0f
        withCursor { delegate, _, _ ->
            delegate.init()
            delegate.cursorPosition.set(10f, 400f)
            val update = PointerController::class.java.getDeclaredField("pointerTick")
                .apply { isAccessible = true }.get(delegate) as Runnable
            start(delegate)
            var frame = 0
            val end = SystemClock.uptimeMillis() + holdMs
            while (SystemClock.uptimeMillis() < end) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(8))
                input(delegate, frame++)
                ShadowSystemClock.advanceBy(Duration.ofMillis(8))
                update.run()
            }
            distance = delegate.cursorPosition.x - 10f
            delegate.resetInput()
        }
        return distance
    }

    @Test fun keyRepeatsDoNotSlowThePointer() {
        initConfig()
        val press = { d: PointerController ->
            assertTrue(d.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        }
        val steady = distanceWith(600, press) { _, _ -> }
        // Android repeats a held key about every 50 ms; each repeat must not restart the frame clock.
        val repeating = distanceWith(600, press) { d, frame ->
            if (frame % 3 == 2) {
                val now = SystemClock.uptimeMillis()
                d.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, frame / 3 + 1))
            }
        }
        assertTrue("The pointer must move: $steady", steady > 100f)
        assertEquals(steady, repeating, steady * 0.05f)
    }

    @Test fun stickSamplesDoNotSlowThePointer() {
        initConfig()
        val tilt = { d: PointerController -> d.onStick(1f, 0f) }
        val steady = distanceWith(600, tilt) { _, _ -> }
        // A gamepad reports the stick continuously; tiny changes must not shorten the next frame.
        val sampled = distanceWith(600, tilt) { d, frame -> d.onStick(if (frame % 2 == 0) 0.999f else 1f, 0f) }
        assertTrue("The pointer must move: $steady", steady > 100f)
        assertEquals(steady, sampled, steady * 0.05f)
    }

    @Test fun releasingOneOfTwoHeldDirectionsKeepsTheOtherAxisSpeed() {
        initConfig()
        withCursor { delegate, _, _ ->
            delegate.init()
            delegate.cursorPosition.set(10f, 10f)
            val update = PointerController::class.java.getDeclaredField("pointerTick")
                .apply { isAccessible = true }.get(delegate) as Runnable
            val frame = { ShadowSystemClock.advanceBy(Duration.ofMillis(16)); update.run() }
            assertTrue(delegate.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
            assertTrue(delegate.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
            repeat(15) { frame() }
            val before = delegate.cursorPosition.x
            frame()
            val stepBefore = delegate.cursorPosition.x - before
            assertTrue(delegate.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)))
            val y = delegate.cursorPosition.y
            val x = delegate.cursorPosition.x
            frame()
            assertEquals("Down released: the pointer stops moving down", y, delegate.cursorPosition.y, 0.01f)
            assertTrue("Right is still held: it must not restart from rest",
                delegate.cursorPosition.x - x >= stepBefore * 0.95f)
            delegate.resetInput()
        }
    }

    @Test fun cursorSpeedDoesNotDependOnTheDeviceFrameRate() {
        initConfig()
        val at60fps = distanceWhileHolding(frameMs = 16, holdMs = 400)
        val at30fps = distanceWhileHolding(frameMs = 33, holdMs = 400)
        assertTrue("The cursor must move: $at60fps", at60fps > 50f)
        // A slow TV must not move the cursor at half speed just because it renders fewer frames.
        assertEquals(at60fps, at30fps, at60fps * 0.15f)
    }
}
