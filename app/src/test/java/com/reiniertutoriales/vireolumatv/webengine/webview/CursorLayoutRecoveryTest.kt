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
            assertEquals(1, clicks)
        } finally { controller.pause().stop().destroy() }
    }
}
