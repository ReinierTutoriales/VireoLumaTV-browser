package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class CursorLayoutRecoveryTest {
    @Test fun focusedTextEditorReceivesEnterAndDpadWithoutImeInsets() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val layout = CursorLayout(activity)
            val keys = mutableListOf<Int>()
            val editor = object : View(activity) {
                override fun onCheckIsTextEditor() = true
                override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
                    keys += keyCode
                    return true
                }
                override fun onKeyUp(keyCode: Int, event: KeyEvent) = true
            }.apply { isFocusableInTouchMode = true }
            layout.addView(editor)
            activity.setContentView(layout)
            layout.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            layout.layout(0, 0, 800, 600)
            assertTrue(editor.requestFocus())
            assertTrue(layout.isTextInputActive())
            for (code in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT)) {
                assertTrue("Text editor must receive DOWN for $code", layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)))
                assertTrue("Text editor must receive UP for $code", layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)))
            }
            assertEquals(listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT), keys)
            assertTrue("Editing must preserve the cursor mode for after submission", layout.cursorEnabled)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun openingKeyboardDuringOkPressStillFinishesTheClick() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val layout = CursorLayout(activity)
            var editing = false
            var clicks = 0
            val touches = mutableListOf<Int>()
            val keys = mutableListOf<Int>()
            val editor = object : View(activity) {
                override fun onCheckIsTextEditor() = editing
                override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
                    keys += keyCode
                    return true
                }
                override fun onKeyUp(keyCode: Int, event: KeyEvent) = true
            }.apply {
                isFocusableInTouchMode = true
                setOnClickListener { clicks++ }
                setOnTouchListener { _, event ->
                    touches += event.actionMasked
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) editing = true
                    false
                }
            }
            layout.addView(editor)
            activity.setContentView(layout)
            layout.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            layout.layout(0, 0, 800, 600)
            editor.requestFocus()
            layout.cursorDrawerDelegate.animateAppearing()
            assertTrue(layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
            assertTrue(layout.isTextInputActive())
            assertTrue(layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), touches)
            assertEquals(1, clicks)
            assertFalse(layout.cursorDrawerDelegate.isSelectionPressed)
            assertTrue(layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)))
            assertTrue(layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)))
            assertEquals(listOf(KeyEvent.KEYCODE_ENTER), keys)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun cursorSurvivesDrawingFlagChangesAndResizesWhileDisabled() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val layout = CursorLayout(activity)
        var clicks = 0
        val touches = mutableListOf<Int>()
        val target = View(activity).apply {
            isFocusableInTouchMode = true
            setOnClickListener { clicks++ }
            setOnTouchListener { _, event -> touches += event.actionMasked; false }
        }
        layout.addView(target)
        activity.setContentView(layout)
        fun resize(size: Int) {
            layout.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
            layout.layout(0, 0, size, size)
        }
        fun key(action: Int) = layout.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER))
        try {
            resize(800)
            target.requestFocus()
            layout.setWillNotDraw(true)
            assertTrue(layout.cursorEnabled)
            layout.cursorEnabled = false
            resize(1000)
            assertEquals(500f, layout.cursorDrawerDelegate.cursorPosition.x, 0f)
            layout.cursorEnabled = true
            layout.cursorDrawerDelegate.animateAppearing()
            assertTrue(key(KeyEvent.ACTION_DOWN))
            // Dialog steals the window before the remote releases OK. This must cancel, never click.
            layout.onWindowFocusChanged(false)
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), touches)
            layout.onWindowFocusChanged(true)
            layout.cursorDrawerDelegate.animateAppearing()
            assertTrue(key(KeyEvent.ACTION_DOWN))
            assertTrue(key(KeyEvent.ACTION_UP))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, clicks)
        } finally { controller.pause().stop().destroy() }
    }
}
