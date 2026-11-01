package com.reiniertutoriales.vireolumatv.activity.main

import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.FragmentActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.GeneralPage
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** D-pad order of the main settings page, measured on the real hierarchy at TV dialog width. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainSettingsFocusTest {
    @Test fun downAndUpVisitEveryControlOnceAndRowsToggleTheirSetting() {
        val model = AdBlocker(autoLoad = false)
        val restore = ScreenModels.replaceForTest(AdBlocker::class, model)
        val previousAdBlock = model.config.adBlockEnabled
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            model.config.adBlockEnabled = true
            val activity = controller.get()
            val root = FrameLayout(activity)
            activity.setContentView(root)
            val settings = GeneralPage(activity)
            root.addView(settings)
            // A remote key leaves touch mode; only then are spinners, switches and sliders focus stops.
            val viewRoot = activity.window.decorView.parent
            viewRoot.javaClass.getDeclaredMethod("ensureTouchMode", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(viewRoot, false)
            // Lay out the whole page so every row has its real position, as when scrolled into view.
            val page = settings.getChildAt(0) as ViewGroup
            page.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            page.layout(0, 0, 960, page.measuredHeight)
            assertFalse(page.isInTouchMode)

            // Grouped as Browsing, Protection, Video and display, Remote and pointer, Advanced; the
            // section headings are not focus stops.
            val expected = listOf(
                "spEngine", "spHomePage", "spTitles",
                "llAdblock", "btnAdBlockerUpdate",
                "llWebViewAlgorithmicDarkening", "llAllowAutoplayMedia", "spVideoMaxQuality", "llKeepScreenOn",
                "llNavigateWithJoystickAxes", "sbCursorMaxSpeed", "sbCursorAcceleration",
                "etAdBlockerListUrl", "llWebEngineDebug", "btnClearWebCache",
            )
            assertEquals(expected, walk(page, View.FOCUS_DOWN))
            val headings = (0 until page.childCount).map { page.getChildAt(it) }
                .filter { it is android.widget.TextView && it.isAccessibilityHeading }
                .map { (it as android.widget.TextView).text.toString() }
            assertEquals(listOf(R.string.settings_section_browsing, R.string.settings_section_protection,
                R.string.settings_section_display, R.string.settings_section_remote, R.string.settings_section_advanced)
                .map(activity::getString), headings)
            assertEquals(expected.reversed(), walk(page, View.FOCUS_UP, page.findViewById(R.id.btnClearWebCache)))

            // Activating a row flips its switch and stores the setting at once.
            val autoplay = model.config.allowAutoplayMedia
            try {
                val row = page.findViewById<ViewGroup>(R.id.llAllowAutoplayMedia)
                row.performClick()
                assertEquals(!autoplay, model.config.allowAutoplayMedia)
                assertEquals(!autoplay, (row.getChildAt(1) as SwitchCompat).isChecked)
            } finally {
                model.config.allowAutoplayMedia = autoplay
            }
        } finally {
            model.config.adBlockEnabled = previousAdBlock
            controller.pause().stop().destroy()
            restore()
            model.clear()
        }
    }

    @Test fun turningDebuggingOffClosesItForPagesAlreadyOpen() {
        val model = AdBlocker(autoLoad = false)
        val restoreModel = ScreenModels.replaceForTest(AdBlocker::class, model)
        val calls = mutableListOf<Boolean>()
        val restoreEngine = com.reiniertutoriales.vireolumatv.webengine.Engines.replaceForTest(object :
            com.reiniertutoriales.vireolumatv.webengine.EngineBackend {
            override fun create(tab: com.reiniertutoriales.vireolumatv.model.BrowserTab) = error("unused")
            override suspend fun clearCache(context: android.content.Context) {}
            override val version = "test"
            override fun setPageDebugging(enabled: Boolean) { calls += enabled }
        })
        val previous = model.config.pageDebugging
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            model.config.pageDebugging = true
            val settings = GeneralPage(controller.get())
            controller.get().setContentView(settings)
            settings.findViewById<View>(R.id.llWebEngineDebug).performClick()
            assertFalse(model.config.pageDebugging)
            assertEquals(listOf(false), calls)
        } finally {
            model.config.pageDebugging = previous
            controller.pause().stop().destroy()
            restoreEngine()
            restoreModel()
            model.clear()
        }
    }

    private fun walk(page: ViewGroup, direction: Int, start: View? = null): List<String> {
        val finder = FocusFinder.getInstance()
        val visited = mutableListOf<String>()
        var current = start ?: finder.findNextFocus(page, null, direction)
        while (current != null && visited.size <= 32) {
            visited += page.resources.getResourceEntryName(current.id)
            current = finder.findNextFocus(page, current, direction)
        }
        return visited
    }
}
