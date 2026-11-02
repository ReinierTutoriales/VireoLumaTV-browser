package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Activity
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.widgets.cursor.PointerController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** The pointer talks to the page as a mouse for hover and scrolling, and as a finger for taps. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class CursorPointerMouseTest {
    private class Surface(activity: Activity) : FrameLayout(activity) {
        val touch = mutableListOf<Int>()
        val mouse = mutableListOf<MotionEvent>()
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            touch += event.actionMasked
            return super.dispatchTouchEvent(event)
        }
        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            mouse += MotionEvent.obtain(event)
            return super.dispatchGenericMotionEvent(event)
        }
    }

    private fun withCursor(test: (PointerController, Surface, () -> Unit) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val surface = Surface(controller.get())
        controller.get().setContentView(surface)
        surface.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        surface.layout(0, 0, 960, 540)
        val delegate = PointerController(controller.get(), surface)
        delegate.position.set(480f, 270f)
        delegate.fadeIn()
        val update = PointerController::class.java.getDeclaredField("pointerTick")
            .apply { isAccessible = true }.get(delegate) as Runnable
        val frame = {
            ShadowSystemClock.advanceBy(Duration.ofMillis(16))
            update.run()
        }
        try { test(delegate, surface, frame) } finally {
            delegate.resetInput()
            surface.mouse.forEach { it.recycle() }
            controller.pause().stop().destroy()
        }
    }

    private fun key(delegate: PointerController, action: Int, code: Int) {
        val now = SystemClock.uptimeMillis()
        assertTrue(delegate.onKey(KeyEvent(now, now, action, code, 0)))
    }

    private val MotionEvent.isMouse get() =
        source == InputDevice.SOURCE_MOUSE && getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE

    @Test fun pushingAgainstTheBottomEdgeScrollsDownWithTheWheelAndNoFinger() = withCursor { delegate, surface, frame ->
        delegate.position.set(480f, 530f)
        key(delegate, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        repeat(20) { frame() }
        key(delegate, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)
        val wheel = surface.mouse.filter { it.actionMasked == MotionEvent.ACTION_SCROLL }
        assertTrue("Edge push must scroll", wheel.isNotEmpty())
        wheel.forEach {
            assertTrue(it.isMouse)
            assertTrue("Down scrolls with negative VSCROLL", it.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0f)
            assertEquals(0f, it.getAxisValue(MotionEvent.AXIS_HSCROLL), 0f)
            assertEquals("Wheel at the pointer", delegate.position.x, it.x, 0.5f)
        }
        assertTrue("Scrolling must not fake a finger", surface.touch.isEmpty())
    }

    @Test fun movingHoversThePageAndResetEndsTheHover() = withCursor { delegate, surface, frame ->
        key(delegate, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        repeat(10) { frame() }
        key(delegate, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
        val actions = surface.mouse.map { it.actionMasked }
        assertEquals(MotionEvent.ACTION_HOVER_ENTER, actions.first())
        assertTrue(actions.drop(1).all { it == MotionEvent.ACTION_HOVER_MOVE })
        assertTrue(surface.mouse.all { it.isMouse })
        assertEquals(delegate.position.x, surface.mouse.last().x, 0.5f)
        delegate.resetInput()
        assertEquals(MotionEvent.ACTION_HOVER_EXIT, surface.mouse.last().actionMasked)
        assertTrue(surface.touch.isEmpty())
    }

    @Test fun grabModeDragsThePageWithThePointer() = withCursor { delegate, surface, frame ->
        delegate.startDragging()
        key(delegate, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        repeat(10) { frame() }
        key(delegate, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)
        val wheel = surface.mouse.filter { it.actionMasked == MotionEvent.ACTION_SCROLL }
        assertTrue("Moving in grab mode must drag the page", wheel.isNotEmpty())
        // Pointer moves down, content follows it down: the page scrolls up.
        wheel.forEach { assertTrue(it.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0f) }
        assertTrue(delegate.canGoBackInPage())
    }

    /** Distance after [frames] frames with the stick held at [deflection] to the right. */
    private fun stickDistance(deflection: Float, frames: Int): Float {
        var distance = 0f
        withCursor { delegate, _, frame ->
            delegate.position.set(100f, 270f)
            delegate.onStick(deflection, 0f)
            repeat(frames) { frame() }
            distance = delegate.position.x - 100f
            delegate.onStick(0f, 0f)
            val stopped = delegate.position.x
            repeat(5) { frame() }
            assertEquals("A centred stick stops the pointer", stopped, delegate.position.x, 0f)
        }
        return distance
    }

    @Test fun stickSpeedIsProportionalToItsDeflection() {
        AppContext.config.pointerSpeedPercent = 100
        val half = stickDistance(0.5f, frames = 10)
        val full = stickDistance(1f, frames = 10)
        assertTrue("The stick must move the pointer: $full", full > 50f)
        assertEquals(full / 2, half, full * 0.05f)
    }
}
