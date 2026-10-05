package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate
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

    private fun withCursor(test: (CursorDrawerDelegate, Surface, IntArray) -> Unit) {
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
        val delegate = CursorDrawerDelegate(activity, surface)
        delegate.cursorPosition.set(123f, 456f)
        delegate.animateAppearing()
        try { test(delegate, surface, clicks) } finally {
            surface.touch.forEach { it.recycle() }
            controller.pause().stop().destroy()
        }
    }

    private fun key(delegate: CursorDrawerDelegate, action: Int, code: Int = KeyEvent.KEYCODE_DPAD_CENTER) {
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
        delegate.callback = object : CursorDrawerDelegate.Callback {
            override fun onLongPress(x: Int, y: Int) {
                assertEquals(123, x)
                assertEquals(456, y)
                longPresses++
            }
        }
        key(delegate, KeyEvent.ACTION_DOWN)
        val runnable = CursorDrawerDelegate::class.java.getDeclaredField("longPressRunnable")
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
}
