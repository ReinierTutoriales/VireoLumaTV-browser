package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvControlColors
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The browser chrome is dark whatever the system mode, and WebView sees a dark app theme. */
@RunWith(RobolectricTestRunner::class)
// android:isLightTheme, which WebView reads for algorithmic darkening, exists from API 29.
@Config(application = VireoLumaTVApp::class, sdk = [33])
class DarkInterfaceTest {
    private fun check() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val activity = controller.get()
            val light = TypedValue()
            assertTrue(activity.theme.resolveAttribute(android.R.attr.isLightTheme, light, true))
            // Algorithmic page darkening only applies when the app theme is not light.
            assertFalse("App theme must be dark", light.data != 0)
            val background = ContextCompat.getColor(activity, R.color.background)
            val text = ContextCompat.getColor(activity, R.color.on_surface)
            assertEquals("The window backdrop must be opaque black", Color.BLACK, background)
            for (style in listOf(R.style.AppTheme, R.style.SidePanel, R.style.SettingsScreen)) {
                val attributes = ContextThemeWrapper(activity, style).obtainStyledAttributes(
                    intArrayOf(android.R.attr.colorBackground, android.R.attr.fontFamily))
                try {
                    assertEquals("Black native backdrop for $style", Color.BLACK, attributes.getColor(0, 0))
                    assertEquals("Use the built-in typeface for $style", "sans-serif", attributes.getString(1))
                } finally { attributes.recycle() }
            }
            fun color(id: Int) = ContextCompat.getColor(activity, id)
            for (surface in listOf(R.color.background, R.color.surface,
                R.color.surface_variant, R.color.surface_high)) {
                for (content in listOf(R.color.on_surface, R.color.on_surface_variant)) {
                    assertTrue("Readable normal/secondary text on $surface", ColorUtils.calculateContrast(
                        color(content), color(surface)) >= 4.5)
                }
            }
            // Compose controls: text stays readable on the focused surface and the focus border stands out.
            val focused = TvControlColors.Focused.toArgb()
            assertTrue("Readable focused text", ColorUtils.calculateContrast(text, focused) >= 4.5)
            assertTrue("Readable focused secondary text", ColorUtils.calculateContrast(color(R.color.on_surface_variant), focused) >= 4.5)
            assertTrue("Visible focus outline", ColorUtils.calculateContrast(
                TvControlColors.BorderFocused.toArgb(), color(R.color.surface_high)) >= 3.0)
            assertTrue("Readable text: ${ColorUtils.calculateContrast(text, background)}",
                ColorUtils.calculateContrast(text, background) >= 7.0)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test @Config(qualifiers = "notnight") fun darkWithLightSystemMode() = check()
    @Test @Config(qualifiers = "night") fun darkWithDarkSystemMode() = check()
}
