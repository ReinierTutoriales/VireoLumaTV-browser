package com.reiniertutoriales.vireolumatv.activity.main

import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.GeneralPage
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class MainSettingsLifecycleTest {
    @Test fun reopeningSettingsReleasesObserversAndKeepsRefreshInTheFocusPath() {
        // Use a model with networking disabled: this test exercises the actual settings hierarchy.
        val model = AdBlocker(autoLoad = false)
        val restore = ScreenModels.replaceForTest(AdBlocker::class, model)
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val sourceObservers = model.config.blockListUrl.watchers.size
        try {
            repeat(3) {
                val settings = GeneralPage(activity)
                root.addView(settings)
                assertEquals(1, model.listLoading.watchers.size)
                assertEquals(1, model.updateResult.watchers.size)
                assertEquals(sourceObservers + 1, model.config.blockListUrl.watchers.size)
                model.listLoading.value = true
                val button = settings.findViewById<View>(R.id.btnAdBlockerUpdate)
                assertEquals(View.VISIBLE, button.visibility)
                assertTrue(button.isEnabled)
                assertTrue(settings.findViewById<android.widget.TextView>(R.id.tvAdBlockerListInfo).maxLines >= 3)
                root.removeView(settings)
                assertTrue(model.listLoading.watchers.isEmpty())
                assertTrue(model.updateResult.watchers.isEmpty())
                assertEquals(sourceObservers, model.config.blockListUrl.watchers.size)
                model.listLoading.value = false
            }
        } finally {
            controller.pause().stop().destroy()
            restore()
            model.clear()
        }
    }
}
