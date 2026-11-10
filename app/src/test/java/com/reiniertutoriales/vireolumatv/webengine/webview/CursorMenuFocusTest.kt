package com.reiniertutoriales.vireolumatv.webengine.webview

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.view.PointerMenu
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.BackPresses
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import com.reiniertutoriales.vireolumatv.webengine.PagePoint
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import com.reiniertutoriales.vireolumatv.widgets.cursor.PointerLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class CursorMenuFocusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var page: View
    private lateinit var cursor: PointerLayout
    private lateinit var menu: PointerMenu
    private var directMode = false

    @Before fun build() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.runOnUiThread {
            val activity = compose.activity
            val root = FrameLayout(activity)
            cursor = PointerLayout(activity)
            page = View(activity).apply { isFocusableInTouchMode = true; isFocusable = true }
            cursor.addView(page)
            root.addView(cursor)
            menu = PointerMenu(activity).apply { visibility = View.GONE }
            root.addView(menu)
            activity.setContentView(root)
        }
        compose.waitForIdle()
    }

    private fun open() {
        val engine = Proxy.newProxyInstance(PageEngine::class.java.classLoader, arrayOf(PageEngine::class.java)) { _, method, args ->
            when (method.name) {
                "getView" -> page
                "setPointerMode" -> { directMode = args!![0] == false; null }
                "getShowsCertificateWarning" -> false
                else -> null
            }
        } as PageEngine
        val provider = Proxy.newProxyInstance(PageWindow::class.java.classLoader,
            arrayOf(PageWindow::class.java)) { _, _, _ -> null } as PageWindow
        val tab = BrowserTab()
        BrowserTab::class.java.getDeclaredField("engine\$delegate").apply { isAccessible = true }.set(tab, lazyOf(engine))
        compose.runOnUiThread {
            menu.show(tab, provider, cursor.pointerController, PagePoint(null, null, 400, 400), BackPresses(onBackKey = {}))
        }
        compose.waitForIdle()
    }

    private fun focusedTag(): String =
        compose.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag]

    private fun press(key: Key) = compose.onNode(isFocused()).performKeyInput { pressKey(key) }

    @Test fun selectingDirectNavigationReturnsFocusToThePage() {
        val config = AppContext.config
        val previous = config.keyModeHintSeen
        config.keyModeHintSeen = true
        try {
            open()
            assertEquals("Grab, in the centre, has the focus", "pointer_move_page", focusedTag())
            assertFalse(page.hasFocus())
            compose.onNodeWithTag("pointer_dpad_mode").requestFocus()
            press(Key.DirectionCenter)
            assertEquals(View.GONE, menu.visibility)
            assertTrue("The closed pointer menu must return focus to the page", page.hasFocus())
            assertTrue(directMode)
        } finally {
            config.keyModeHintSeen = previous
        }
    }

    @Test fun theCrossLeadsFromTheCentreToEachArmAndBack() {
        open()
        for ((key, arm, back) in listOf(
            Triple(Key.DirectionUp, "pointer_link_options", Key.DirectionDown),
            Triple(Key.DirectionDown, "pointer_dpad_mode", Key.DirectionUp),
            Triple(Key.DirectionLeft, "pointer_zoom_out", Key.DirectionRight),
            Triple(Key.DirectionRight, "pointer_zoom_in", Key.DirectionLeft),
        )) {
            press(key)
            assertEquals(arm, focusedTag())
            press(back)
            assertEquals("pointer_move_page", focusedTag())
        }
    }
}
