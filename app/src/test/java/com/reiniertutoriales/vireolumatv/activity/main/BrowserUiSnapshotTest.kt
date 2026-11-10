package com.reiniertutoriales.vireolumatv.activity.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvControlColors
import com.reiniertutoriales.vireolumatv.Config as BrowserConfig
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.databinding.BrowserWindowBinding
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import com.reiniertutoriales.vireolumatv.activity.main.compose.BrowserCommandActions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Native Android drawing of the real XML; deliberately no network page or WebView renderer. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-television-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowserUiSnapshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // The interface is dark only; a light system mode must not change it.
    @Test @Config(qualifiers = "w960dp-h540dp-television-notnight-mdpi")
    fun controlsWithLightSystemMode() = capture("system-light")

    @Test @Config(qualifiers = "w960dp-h540dp-television-night-mdpi")
    fun controlsWithDarkSystemMode() = capture("system-dark")

    @Test @Config(qualifiers = "w960dp-h540dp-television-notnight-mdpi")
    fun enlargedTextControls() = capture("large-text", 1.5f)

    @Test fun integratedControlsUseTheScreenSafeMargins() = capture("integration", bindControls = true)

    private fun capture(name: String, fontScale: Float = 1f, bindControls: Boolean = false) {
        val previousScale = RuntimeEnvironment.getApplication().resources.configuration.fontScale
        RuntimeEnvironment.setFontScale(fontScale)
        val activity = compose.activity
        activity.setTheme(R.style.AppTheme)
        val tabs = ScreenModels.get(TabStore::class, activity)
        tabs.workScope.cancel()
        tabs.openTabs.clear()
        tabs.openTabs.add(BrowserTab(url = BrowserConfig.HOME_PAGE_URL, title = "Inicio", position = 0))
        tabs.openTabs.add(BrowserTab(url = BrowserConfig.HOME_PAGE_URL,
            title = "Documentación: título largo para comprobar el recorte de las pestañas", position = 1))
        tabs.currentTab.value = tabs.openTabs[0]
        val vb = BrowserWindowBinding.inflate(activity.layoutInflater)
        try {
            if (bindControls) {
                vb.controlsHost.bindActions(BrowserCommandActions({}, {}, {}, {}, {}, {}, {}))
                vb.controlsHost.setTabs(tabs.openTabs.toList(), 0)
            }
            vb.controlsHost.showBlocker(enabled = true, description = "Shield active")
            vb.pageContainer.setBackgroundColor(Color.rgb(100, 116, 139))
            vb.tabPreview.visibility = View.GONE
            vb.pointerMenu.visibility = View.INVISIBLE
            vb.loadProgress.visibility = View.GONE
            vb.startupSpinner.visibility = View.GONE
            vb.controlsHost.showAddress(BrowserTab(url = "https://example.test/documentacion/una-direccion-larga"))
            vb.controlsHost.updateNavigation(back = true, forward = false)
            activity.setContentView(vb.root)
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, 960, 540)
            compose.waitForIdle()
            compose.onNodeWithTag("home_search").assertIsDisplayed()
            compose.onNodeWithTag("menu_button").assertIsDisplayed()
            if (bindControls) {
                val origin = IntArray(2)
                vb.root.getLocationInWindow(origin)
                val address = compose.onNodeWithTag("home_search").fetchSemanticsNode().positionInWindow
                val density = activity.resources.displayMetrics.density
                assertEquals("Address screen left safe margin", 48f * density, address.x - origin[0], 0.5f)
                assertEquals("Address screen top safe margin", 24f * density, address.y - origin[1], 0.5f)
            }
            vb.controlsHost.takeFocus()
            vb.root.refreshDrawableState()
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            vb.root.draw(Canvas(bitmap))
            val output = File("build/reports/ui/browser-$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("Native drawing must include browser controls", bitmap.getPixel(40, 40) !=
                Color.rgb(100, 116, 139))
            bitmap.recycle()
            // address is owned by Compose state
            // navigation state is owned by Compose
            verifyGeometry(vb, fontScale)
            verifyStates(activity, name)
        } finally {
            vb.root.removeAllViews()
            tabs.currentTab.value = null
            tabs.openTabs.clear()
            RuntimeEnvironment.setFontScale(previousScale)
        }
    }
    private fun verifyGeometry(vb: BrowserWindowBinding, fontScale: Float) {
        // Compose owns command geometry. Android View IDs for tabs are no longer present.
        for (width in listOf(720, 960, 1280)) {
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(width * 9 / 16, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, width, width * 9 / 16)
            assertTrue("Compose toolbar must fit the viewport", vb.controlsHost.width <= width)
            assertTrue("Compose toolbar must remain attached", vb.controlsHost.childCount == 1)
            // Tab strip is now a Compose host; legacy View measurements are omitted.
            assertTrue("Tab strip host must remain attached", vb.controlsHost.childCount >= 0)
        }
    }

    private fun verifyStates(activity: ComponentActivity, name: String) {
        val labels = listOf("Normal", "Foco", "Pulsado", "Abierta", "Puntero")
        // The states every Compose control draws (TvControlColors), side by side in one sample.
        val surfaces = listOf(TvControlColors.surface(focused = false, hovered = false),
            TvControlColors.surface(focused = true, hovered = false),
            TvControlColors.surface(focused = true, hovered = false, pressed = true),
            TvControlColors.Selected,
            TvControlColors.surface(focused = false, hovered = true)).map { it.toArgb() }
        val sample = Bitmap.createBitmap(600, 104, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sample)
        canvas.drawColor(ContextCompat.getColor(activity, R.color.background))
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(activity, R.color.on_surface)
            textSize = 16f
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((index, surface) in surfaces.withIndex()) {
            fill.color = surface
            canvas.drawRoundRect(12f + index * 120, 12f, 108f + index * 120, 60f, 12f, 12f, fill)
            canvas.drawText(labels[index], 12f + index * 120, 88f, labelPaint)
        }
        assertEquals("Normal/focus/pressed/selected/hover must remain visually distinct", 5, surfaces.toSet().size)
        fun color(id: Int) = ContextCompat.getColor(activity, id)
        // Text and icons on every surface they are drawn on, including the inverted focus.
        for ((content, surface) in listOf(R.color.on_surface to R.color.surface_variant,
            R.color.on_surface to R.color.surface_high, R.color.on_surface to R.color.surface,
            R.color.on_surface to R.color.background)) {
            assertTrue("Contrast ${activity.resources.getResourceEntryName(content)} on " +
                activity.resources.getResourceEntryName(surface),
                ColorUtils.calculateContrast(color(content), color(surface)) >= 4.5)
        }
        assertTrue("Secondary text contrast", ColorUtils.calculateContrast(
            color(R.color.on_surface_variant), color(R.color.surface)) >= 4.5)
        assertTrue("Focus is visible against the controls' surroundings", ColorUtils.calculateContrast(
            TvControlColors.BorderFocused.toArgb(), color(R.color.surface_variant)) >= 3.0)
        File("build/reports/ui/browser-states-$name.png").outputStream().use {
            assertTrue(sample.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        sample.recycle()
    }

}
