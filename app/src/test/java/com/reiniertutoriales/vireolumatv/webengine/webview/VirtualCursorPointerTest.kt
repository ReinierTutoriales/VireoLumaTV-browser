package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Application
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class VirtualCursorPointerTest {
    private class Surface : View(RuntimeEnvironment.getApplication()) {
        val touch = mutableListOf<MotionEvent>()
        val hover = mutableListOf<MotionEvent>()
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            touch += MotionEvent.obtain(event)
            return true
        }
        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            hover += MotionEvent.obtain(event)
            return true
        }
        fun recycle() = (touch + hover).forEach { it.recycle() }
    }

    private fun send(delegate: CursorDrawerDelegate, action: Int) {
        CursorDrawerDelegate::class.java.getDeclaredMethod("dispatchCursorEvent",
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(delegate, 123f, 456f, action)
    }

    @Test fun webCursorHasExactMouseCoordinatesAndPrimaryButtonWithIndependentHover() {
        val surface = Surface()
        val delegate = CursorDrawerDelegate(surface.context, surface)
        delegate.callback = object : CursorDrawerDelegate.Callback {
            override fun onLongPress(x: Int, y: Int) {}
            override fun usesMousePointer() = true
        }
        try {
            send(delegate, MotionEvent.ACTION_HOVER_MOVE)
            send(delegate, MotionEvent.ACTION_DOWN)
            send(delegate, MotionEvent.ACTION_MOVE)
            send(delegate, MotionEvent.ACTION_UP)
            send(delegate, MotionEvent.ACTION_DOWN)
            send(delegate, MotionEvent.ACTION_CANCEL)
            assertEquals(1, surface.hover.size)
            assertEquals(5, surface.touch.size)
            (surface.touch + surface.hover).forEach {
                assertEquals(InputDevice.SOURCE_MOUSE, it.source)
                assertEquals(MotionEvent.TOOL_TYPE_MOUSE, it.getToolType(0))
                assertEquals(123f, it.x, 0f)
                assertEquals(456f, it.y, 0f)
            }
            assertEquals(MotionEvent.BUTTON_PRIMARY, surface.touch[0].buttonState)
            assertEquals(MotionEvent.BUTTON_PRIMARY, surface.touch[1].buttonState)
            assertEquals(0, surface.touch[2].buttonState)
            assertEquals(0, surface.touch[4].buttonState)
            assertEquals(surface.touch[0].downTime, surface.touch[2].downTime)
            assertEquals(0, surface.hover.single().buttonState)
        } finally { surface.recycle() }
    }

    @Test fun nonWebControlsKeepTouchSemantics() {
        val surface = Surface()
        val delegate = CursorDrawerDelegate(surface.context, surface)
        try {
            send(delegate, MotionEvent.ACTION_DOWN)
            send(delegate, MotionEvent.ACTION_UP)
            assertTrue(surface.hover.isEmpty())
            surface.touch.forEach {
                assertEquals(InputDevice.SOURCE_TOUCHSCREEN, it.source)
                assertEquals(MotionEvent.TOOL_TYPE_FINGER, it.getToolType(0))
            }
        } finally { surface.recycle() }
    }
}
