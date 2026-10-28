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
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
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
        val tabs = ActiveModelsRepository.get(TabsModel::class, activity)
        tabs.modelScope.cancel()
        tabs.tabsStates.clear()
        tabs.tabsStates.add(WebTabState(url = BrowserConfig.HOME_PAGE_URL, title = "Inicio", position = 0))
        tabs.tabsStates.add(WebTabState(url = BrowserConfig.HOME_PAGE_URL,
            title = "Documentación: título largo para comprobar el recorte de las pestañas", position = 1))
        tabs.currentTab.value = tabs.tabsStates[0]
        val vb = BrowserWindowBinding.inflate(activity.layoutInflater)
        try {
            assertTrue("Blocker shield needs a readable initial description",
                !vb.controlsRow.adBlockButton.contentDescription.isNullOrBlank())
            vb.pageContainer.setBackgroundColor(Color.rgb(100, 116, 139))
            vb.tabPreview.visibility = View.GONE
            vb.pointerMenu.visibility = View.INVISIBLE
            vb.loadProgress.visibility = View.GONE
            vb.startupSpinner.visibility = View.GONE
            vb.controlsRow.setAddressBoxText("https://example.test/documentacion/una-direccion-larga")
            vb.controlsRow.forwardButton.isEnabled = false
            activity.setContentView(vb.root)
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, 960, 540)
            vb.controlsRow.backButton.isFocusableInTouchMode = true
            assertTrue(vb.controlsRow.backButton.requestFocus())
            vb.root.refreshDrawableState()
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            vb.root.draw(Canvas(bitmap))
            val output = File("build/reports/ui/browser-$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("Native drawing must include browser controls", bitmap.getPixel(40, 40) !=
                Color.rgb(100, 116, 139))
            bitmap.recycle()
            val address = vb.root.findViewById<TextView>(R.id.address_field)
            assertEquals("System font scaling is honored", 18f * fontScale, address.textSize, 0.1f)
            assertTrue("Enlarged URL text fits vertically: line=${address.layout.getLineBottom(0)}, height=${address.height}, padding=${address.compoundPaddingTop + address.compoundPaddingBottom}", address.layout.getLineBottom(0) <=
                address.height - address.compoundPaddingTop - address.compoundPaddingBottom)
            verifyGeometry(vb, fontScale)
            verifyStates(activity, name)
        } finally {
            vb.root.removeAllViews()
            tabs.currentTab.value = null
            tabs.tabsStates.clear()
            host.pause().stop().destroy()
            AppCompatDelegate.setDefaultNightMode(previousMode)
            RuntimeEnvironment.setFontScale(previousScale)
        }
    }
    private fun verifyGeometry(vb: BrowserWindowBinding, fontScale: Float) {
        // One row of round 48dp commands in a single focus chain.
        val capsuleIds = listOf(R.id.back_button, R.id.forward_button, R.id.reload_button,
            R.id.home_button, R.id.blocker_button, R.id.menu_button)
        for (width in listOf(720, 960, 1280)) {
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(width * 9 / 16, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, width, width * 9 / 16)
            var previousRight = -1
            for (id in capsuleIds) {
                val view = vb.root.findViewById<View>(id)
                assertEquals("Uniform control width at $width", 48, view.width)
                assertEquals("Uniform control height at $width", 48, view.height)
                // A focused control grows 1.1x: neighbours must keep a gap for it.
                assertTrue("Controls keep room to grow at $width", view.left >= previousRight + 4)
                previousRight = view.right
            }
            // Everything focusable stays inside the 48 dp / 27 dp overscan-safe area.
            val surface = vb.controlsOverlay
            val rowLeft = surface.left + vb.controlsRow.left
            val rowRight = surface.left + vb.controlsRow.right
            assertTrue("Controls inside the safe area at $width", rowLeft >= 48 && rowRight <= width - 48)
            assertTrue("Controls start below the safe top at $width", surface.top + vb.controlsRow.top >= 27)
            val address = vb.root.findViewById<View>(R.id.address_field)
            assertTrue("Address remains usable at $width", address.width >= 240)
            if (fontScale == 1f) assertEquals("Default address height", 48, address.height)
            else assertTrue("Accessible text may expand the field", address.height >= 48)
            // Tab chips: 40dp tall, 188dp wide so four fit inside the safe area; "+" is a 48dp control.
            val tabs = vb.tabStrip.findViewById<RecyclerView>(R.id.tab_list)
            val tab = tabs.getChildAt(0)
            assertEquals("Tab height at $width", 40, tab.height)
            assertEquals("Tab width at $width", 188, tab.width)
            assertEquals("Add-tab button height at $width", 48, vb.tabStrip.findViewById<View>(R.id.add_button).height)
            if (width >= 960) assertTrue("Four tabs fit beside the add button at $width: ${tabs.width}",
                tabs.width - tabs.paddingLeft - tabs.paddingRight >= 4 * (188 + 8))
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
