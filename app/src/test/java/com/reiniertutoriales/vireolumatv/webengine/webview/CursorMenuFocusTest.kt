package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.view.PointerMenu
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.BackPresses
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import com.reiniertutoriales.vireolumatv.widgets.cursor.PointerLayout
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
        val cursor = PointerLayout(activity)
        val page = View(activity).apply { isFocusableInTouchMode = true }
        cursor.addView(page)
        root.addView(cursor)
        val menu = PointerMenu(activity)
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
        val tab = BrowserTab()
        BrowserTab::class.java.getDeclaredField("engine\$delegate").apply { isAccessible = true }
            .set(tab, lazyOf(engine))
        val config = AppContext.config
        val previous = config.keyModeHintSeen
        config.keyModeHintSeen = true
        try {
            menu.show(tab, provider, cursor.pointerController, com.reiniertutoriales.vireolumatv.webengine.PagePoint(null, null, 400, 400),
                BackPresses(onBackKey = {}))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            menu.findViewById<View>(R.id.dpad_mode_button).requestFocus()
            assertFalse(page.hasFocus())
            menu.findViewById<View>(R.id.dpad_mode_button).performClick()
            assertEquals(View.GONE, menu.visibility)
            assertTrue("The closed cursor menu must return focus to the page", page.hasFocus())
            assertTrue(directMode)
        } finally {
            config.keyModeHintSeen = previous
            controller.pause().stop().destroy()
        }
    }

    @Test fun theCrossLeadsFromTheCentreToEachArmAndBack() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        val menu = PointerMenu(activity)
        activity.setContentView(menu)
        fun id(view: Int, direction: Int) = when (direction) {
            View.FOCUS_UP -> menu.findViewById<View>(view).nextFocusUpId
            View.FOCUS_DOWN -> menu.findViewById<View>(view).nextFocusDownId
            View.FOCUS_LEFT -> menu.findViewById<View>(view).nextFocusLeftId
            else -> menu.findViewById<View>(view).nextFocusRightId
        }
        assertEquals(R.id.link_options_button, id(R.id.move_page_button, View.FOCUS_UP))
        assertEquals(R.id.dpad_mode_button, id(R.id.move_page_button, View.FOCUS_DOWN))
        assertEquals(R.id.zoom_out_button, id(R.id.move_page_button, View.FOCUS_LEFT))
        assertEquals(R.id.zoom_in_button, id(R.id.move_page_button, View.FOCUS_RIGHT))
        assertEquals(R.id.move_page_button, id(R.id.link_options_button, View.FOCUS_DOWN))
        assertEquals(R.id.move_page_button, id(R.id.dpad_mode_button, View.FOCUS_UP))
        assertEquals(R.id.move_page_button, id(R.id.zoom_out_button, View.FOCUS_RIGHT))
        assertEquals(R.id.move_page_button, id(R.id.zoom_in_button, View.FOCUS_LEFT))
    }
}
