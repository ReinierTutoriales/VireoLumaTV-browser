package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Activity
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The browser chrome is dark whatever the system mode, and WebView sees a dark app theme. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class DarkInterfaceTest {
    private fun check() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val activity = controller.get()
            val light = TypedValue()
            assertTrue(activity.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, light, true))
            // Algorithmic page darkening only applies when the app theme is not light.
            assertFalse("App theme must be dark", light.data != 0)
            val background = ContextCompat.getColor(activity, R.color.background)
            val text = ContextCompat.getColor(activity, R.color.on_surface)
            assertTrue("Dark background", ColorUtils.calculateLuminance(background) < 0.05)
            assertTrue("Readable text: ${ColorUtils.calculateContrast(text, background)}",
                ColorUtils.calculateContrast(text, background) >= 7.0)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test @Config(qualifiers = "notnight") fun darkWithLightSystemMode() = check()
    @Test @Config(qualifiers = "night") fun darkWithDarkSystemMode() = check()
}
