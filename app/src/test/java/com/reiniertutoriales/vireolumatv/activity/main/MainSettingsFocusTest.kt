package com.reiniertutoriales.vireolumatv.activity.main

import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.FragmentActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.MainSettingsView
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
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
        val model = AdblockModel(autoLoad = false)
        val mapField = ActiveModelsRepository::class.java.getDeclaredField("holdersMap").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val holders = mapField.get(ActiveModelsRepository) as MutableMap<String, Any>
        val key = AdblockModel::class.qualifiedName!!
        val previous = holders[key]
        val holderClass = Class.forName("com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository\$StateModelHolder")
        holders[key] = holderClass.getDeclaredConstructor(ActiveModel::class.java)
            .apply { isAccessible = true }.newInstance(model)
        val previousAdBlock = model.config.adBlockEnabled
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            model.config.adBlockEnabled = true
            val activity = controller.get()
            val root = FrameLayout(activity)
            activity.setContentView(root)
            val settings = MainSettingsView(activity)
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

            val expected = listOf(
                "spEngine", "spHomePage", "spTitles", "llAdblock", "etAdBlockerListUrl",
                "btnAdBlockerUpdate", "llWebViewAlgorithmicDarkening",
                "llAllowAutoplayMedia", "spVideoMaxQuality", "llWebEngineDebug", "llKeepScreenOn",
                "llNavigateWithJoystickAxes", "sbCursorMaxSpeed", "sbCursorAcceleration", "btnClearWebCache",
            )
            assertEquals(expected, walk(page, View.FOCUS_DOWN))
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
            if (previous == null) holders.remove(key) else holders[key] = previous
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
