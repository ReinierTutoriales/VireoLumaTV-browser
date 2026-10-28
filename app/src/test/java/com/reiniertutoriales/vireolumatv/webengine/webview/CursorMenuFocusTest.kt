package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.view.CursorMenuView
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.BackNavigationEventsAdapter
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class CursorMenuFocusTest {
    @Test fun selectingDirectNavigationReturnsFocusToThePage() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        val root = FrameLayout(activity)
        val cursor = CursorLayout(activity)
        val page = View(activity).apply { isFocusableInTouchMode = true }
        cursor.addView(page)
        root.addView(cursor)
        val menu = CursorMenuView(activity)
        root.addView(menu)
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 800, 800)
        var directMode = false
        val engine = Proxy.newProxyInstance(PageEngine::class.java.classLoader, arrayOf(PageEngine::class.java)) { _, method, args ->
            when (method.name) {
                "getView" -> page
                "setPointerMode" -> { directMode = args!![0] == false; null }
                else -> null
            }
        } as PageEngine
        val provider = Proxy.newProxyInstance(PageWindow::class.java.classLoader,
            arrayOf(PageWindow::class.java)) { _, _, _ -> null } as PageWindow
        val tab = WebTabState()
        WebTabState::class.java.getDeclaredField("engine\$delegate").apply { isAccessible = true }
            .set(tab, lazyOf(engine))
        val config = AppContext.config
        val previous = config.directNavigationModeHintSuppress
        config.directNavigationModeHintSuppress = true
        try {
            menu.show(tab, provider, cursor.cursorDrawerDelegate, com.reiniertutoriales.vireolumatv.webengine.PagePoint(null, null, 400, 400),
                BackNavigationEventsAdapter(onEmulatedBackEvent = {}))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            menu.findViewById<View>(R.id.btnDPADMode).requestFocus()
            assertFalse(page.hasFocus())
            menu.findViewById<View>(R.id.btnDPADMode).performClick()
            assertEquals(View.GONE, menu.visibility)
            assertTrue("The closed cursor menu must return focus to the page", page.hasFocus())
            assertTrue(directMode)
        } finally {
            config.directNavigationModeHintSuppress = previous
            controller.pause().stop().destroy()
        }
    }

    @Test fun theCrossLeadsFromTheCentreToEachArmAndBack() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        val menu = CursorMenuView(activity)
        activity.setContentView(menu)
        fun id(view: Int, direction: Int) = when (direction) {
            View.FOCUS_UP -> menu.findViewById<View>(view).nextFocusUpId
            View.FOCUS_DOWN -> menu.findViewById<View>(view).nextFocusDownId
            View.FOCUS_LEFT -> menu.findViewById<View>(view).nextFocusLeftId
            else -> menu.findViewById<View>(view).nextFocusRightId
        }
        assertEquals(R.id.btnContextMenu, id(R.id.btnGrabMode, View.FOCUS_UP))
        assertEquals(R.id.btnDPADMode, id(R.id.btnGrabMode, View.FOCUS_DOWN))
        assertEquals(R.id.btnZoomOut, id(R.id.btnGrabMode, View.FOCUS_LEFT))
        assertEquals(R.id.btnZoomIn, id(R.id.btnGrabMode, View.FOCUS_RIGHT))
        assertEquals(R.id.btnGrabMode, id(R.id.btnContextMenu, View.FOCUS_DOWN))
        assertEquals(R.id.btnGrabMode, id(R.id.btnDPADMode, View.FOCUS_UP))
        assertEquals(R.id.btnGrabMode, id(R.id.btnZoomOut, View.FOCUS_RIGHT))
        assertEquals(R.id.btnGrabMode, id(R.id.btnZoomIn, View.FOCUS_LEFT))
    }
}
