package com.reiniertutoriales.vireolumatv.activity.main

import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.MainSettingsView
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
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
        val model = AdblockModel(autoLoad = false)
        val mapField = ActiveModelsRepository::class.java.getDeclaredField("holdersMap").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val holders = mapField.get(ActiveModelsRepository) as MutableMap<String, Any>
        val key = AdblockModel::class.qualifiedName!!
        val previous = holders[key]
        val holderClass = Class.forName("com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository\$StateModelHolder")
        holders[key] = holderClass.getDeclaredConstructor(ActiveModel::class.java)
            .apply { isAccessible = true }.newInstance(model)
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val sourceObservers = model.config.adBlockListURL.observers.size
        try {
            repeat(3) {
                val settings = MainSettingsView(activity)
                root.addView(settings)
                assertEquals(1, model.clientLoading.observers.size)
                assertEquals(1, model.updateResult.observers.size)
                assertEquals(sourceObservers + 1, model.config.adBlockListURL.observers.size)
                model.clientLoading.value = true
                val button = settings.findViewById<View>(R.id.btnAdBlockerUpdate)
                assertEquals(View.VISIBLE, button.visibility)
                assertTrue(button.isEnabled)
                assertTrue(settings.findViewById<android.widget.TextView>(R.id.tvAdBlockerListInfo).maxLines >= 3)
                root.removeView(settings)
                assertTrue(model.clientLoading.observers.isEmpty())
                assertTrue(model.updateResult.observers.isEmpty())
                assertEquals(sourceObservers, model.config.adBlockListURL.observers.size)
                model.clientLoading.value = false
            }
        } finally {
            controller.pause().stop().destroy()
            if (previous == null) holders.remove(key) else holders[key] = previous
            model.clear()
        }
    }
}
