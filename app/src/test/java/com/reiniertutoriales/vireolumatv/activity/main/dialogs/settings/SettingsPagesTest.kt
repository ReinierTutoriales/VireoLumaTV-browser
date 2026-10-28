package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.FragmentActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class SettingsPagesTest {
    @Test fun remoteFocusDoesNotChangePageAndReturningKeepsUnsavedView() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val tabs = RadioGroup(activity)
            val main = RadioButton(activity).apply { id = R.id.general_page_tab }
            val version = RadioButton(activity).apply { id = R.id.about_page_tab }
            tabs.addView(main)
            tabs.addView(version)
            tabs.check(main.id)
            val content = FrameLayout(activity)
            val created = mutableMapOf<Int, Int>()
            SettingsPages(tabs, content, setOf(main.id, version.id)) { id ->
                created[id] = (created[id] ?: 0) + 1
                View(activity)
            }
            val original = content.getChildAt(0)
            activity.setContentView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(tabs)
                addView(content)
            })

            version.requestFocus()
            assertSame(original, content.getChildAt(0))
            assertTrue(main.isActivated)
            assertFalse(version.isActivated)

            tabs.check(version.id)
            assertTrue(version.isActivated)
            assertFalse(main.isActivated)
            tabs.check(main.id)
            assertSame(original, content.getChildAt(0))
            assertEquals(1, created[main.id])
            assertEquals(1, created[version.id])
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
