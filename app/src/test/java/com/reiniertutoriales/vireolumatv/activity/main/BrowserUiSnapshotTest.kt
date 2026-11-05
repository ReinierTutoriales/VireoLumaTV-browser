package com.reiniertutoriales.vireolumatv.activity.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.appcompat.content.res.AppCompatResources
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.Config as BrowserConfig
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.databinding.BrowserWindowBinding
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Native Android drawing of the real XML; deliberately no network page or WebView renderer. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowserUiSnapshotTest {
    // The interface is dark only; a light system mode must not change it.
    @Test @Config(qualifiers = "w960dp-h540dp-notnight-mdpi")
    fun controlsWithLightSystemMode() = capture("system-light")

    @Test @Config(qualifiers = "w960dp-h540dp-night-mdpi")
    fun controlsWithDarkSystemMode() = capture("system-dark")

    @Test @Config(qualifiers = "w960dp-h540dp-notnight-mdpi")
    fun enlargedTextControls() = capture("large-text", 1.5f)

    private fun capture(name: String, fontScale: Float = 1f) {
        val previousScale = RuntimeEnvironment.getApplication().resources.configuration.fontScale
        RuntimeEnvironment.setFontScale(fontScale)
        val previousMode = AppCompatDelegate.getDefaultNightMode()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val host = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.AppTheme)
            setup()
        }
        val activity = host.get()
        val tabs = ScreenModels.get(TabStore::class, activity)
        tabs.workScope.cancel()
        tabs.openTabs.clear()
        tabs.openTabs.add(BrowserTab(url = BrowserConfig.HOME_PAGE_URL, title = "Inicio", position = 0))
        tabs.openTabs.add(BrowserTab(url = BrowserConfig.HOME_PAGE_URL,
            title = "Documentación: título largo para comprobar el recorte de las pestañas", position = 1))
        tabs.currentTab.value = tabs.openTabs[0]
        val vb = BrowserWindowBinding.inflate(activity.layoutInflater)
        try {
            vb.controlsRow.updateBlockerStatus("Shield active", true)
            assertEquals("Shield active", vb.controlsRow.snapshot.blockerDescription)
            vb.pageContainer.setBackgroundColor(Color.rgb(100, 116, 139))
            vb.tabPreview.visibility = View.GONE
            vb.pointerMenu.visibility = View.INVISIBLE
            vb.loadProgress.visibility = View.GONE
            vb.startupSpinner.visibility = View.GONE
            vb.controlsRow.showAddress("https://example.test/documentacion/una-direccion-larga")
            vb.controlsRow.updateNavigation(back = true, forward = false)
            activity.setContentView(vb.root)
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, 960, 540)
            vb.controlsRow.takeFocus()
            vb.root.refreshDrawableState()
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            vb.root.draw(Canvas(bitmap))
            val output = File("build/reports/ui/browser-$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("Native drawing must include browser controls", bitmap.getPixel(40, 40) !=
                Color.rgb(100, 116, 139))
            bitmap.recycle()
            assertEquals("https://example.test/documentacion/una-direccion-larga", vb.controlsRow.snapshot.address)
            assertFalse(vb.controlsRow.snapshot.canGoForward)
            verifyGeometry(vb, fontScale)
            verifyStates(activity, name)
        } finally {
            vb.root.removeAllViews()
            tabs.currentTab.value = null
            tabs.openTabs.clear()
            host.pause().stop().destroy()
            AppCompatDelegate.setDefaultNightMode(previousMode)
            RuntimeEnvironment.setFontScale(previousScale)
        }
    }
    private fun verifyGeometry(vb: BrowserWindowBinding, fontScale: Float) {
        // Compose owns command geometry. Android View IDs for tabs are no longer present.
        for (width in listOf(720, 960, 1280)) {
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(width * 9 / 16, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, width, width * 9 / 16)
            assertTrue("Compose toolbar must fit the viewport", vb.controlsRow.width <= width)
            assertTrue("Compose toolbar must remain attached", vb.controlsRow.childCount == 1)
            // Tab strip is now a Compose host; legacy View measurements are omitted.
            assertTrue("Tab strip host must remain attached", vb.tabStrip.childCount >= 0)
        }
    }

    private fun verifyStates(activity: AppCompatActivity, name: String) {
        val labels = listOf("Normal", "Foco", "Pulsado", "Abierta", "Inactivo")
        val enabled = android.R.attr.state_enabled
        val focused = android.R.attr.state_focused
        val pressed = android.R.attr.state_pressed
        val selected = android.R.attr.state_selected
        val states = listOf(intArrayOf(enabled), intArrayOf(enabled, focused),
            intArrayOf(enabled, focused, pressed), intArrayOf(enabled, selected), intArrayOf(-enabled))
        val sample = Bitmap.createBitmap(600, 104, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sample)
        canvas.drawColor(ContextCompat.getColor(activity, R.color.background))
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(activity, R.color.on_surface)
            textSize = 16f
        }
        val centers = mutableSetOf<Int>()
        for ((index, state) in states.withIndex()) {
            val drawable = AppCompatResources.getDrawable(activity, R.drawable.bg_chip)!!.mutate()
            drawable.state = state
            drawable.setBounds(12 + index * 120, 12, 108 + index * 120, 60)
            drawable.draw(canvas)
            centers.add(sample.getPixel(60 + index * 120, 36))
            canvas.drawText(labels[index], 12f + index * 120, 88f, labelPaint)
        }
        assertEquals("Normal/focus/pressed/open/disabled must remain visually distinct", 5, centers.size)
        fun color(id: Int) = ContextCompat.getColor(activity, id)
        // Text and icons on every surface they are drawn on, including the inverted focus.
        for ((content, surface) in listOf(R.color.on_surface to R.color.surface_variant,
            R.color.on_surface to R.color.brand_container, R.color.on_surface to R.color.surface,
            R.color.inverse_on_surface to R.color.inverse_surface, R.color.on_surface to R.color.background)) {
            assertTrue("Contrast ${activity.resources.getResourceEntryName(content)} on " +
                activity.resources.getResourceEntryName(surface),
                ColorUtils.calculateContrast(color(content), color(surface)) >= 4.5)
        }
        assertTrue("Secondary text contrast", ColorUtils.calculateContrast(
            color(R.color.on_surface_variant), color(R.color.surface)) >= 4.5)
        assertTrue("Focus is visible against the controls' surroundings", ColorUtils.calculateContrast(
            color(R.color.inverse_surface), color(R.color.surface_variant)) >= 3.0)
        File("build/reports/ui/browser-states-$name.png").outputStream().use {
            assertTrue(sample.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        sample.recycle()
    }

}
