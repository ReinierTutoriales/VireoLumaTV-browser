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
import com.reiniertutoriales.vireolumatv.Config as BrowserConfig
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.databinding.ActivityMainBinding
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
    @Test @Config(qualifiers = "w960dp-h540dp-notnight-mdpi")
    fun dayControls() = capture("day")

    @Test @Config(qualifiers = "w960dp-h540dp-night-mdpi")
    fun nightControls() = capture("night")

    @Test @Config(qualifiers = "w960dp-h540dp-notnight-mdpi")
    fun enlargedTextControls() = capture("day-large-text", 1.5f)

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
        val vb = ActivityMainBinding.inflate(activity.layoutInflater)
        try {
            assertTrue("Blocker shield needs a readable initial description",
                !vb.vActionBar.adBlockButton.contentDescription.isNullOrBlank())
            vb.flWebViewContainer.setBackgroundColor(Color.rgb(100, 116, 139))
            vb.ivMiniatures.visibility = View.GONE
            vb.vCursorMenu.visibility = View.INVISIBLE
            vb.progressBar.visibility = View.GONE
            vb.progressBarGeneric.visibility = View.GONE
            vb.vActionBar.setAddressBoxText("https://example.test/documentacion/una-direccion-larga")
            vb.vActionBar.forwardButton.isEnabled = false
            activity.setContentView(vb.root)
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, 960, 540)
            vb.vActionBar.backButton.isFocusableInTouchMode = true
            assertTrue(vb.vActionBar.backButton.requestFocus())
            vb.root.refreshDrawableState()
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            vb.root.draw(Canvas(bitmap))
            val output = File("build/reports/ui/browser-$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("Native drawing must include browser controls", bitmap.getPixel(40, 40) !=
                Color.rgb(100, 116, 139))
            bitmap.recycle()
            val address = vb.root.findViewById<TextView>(R.id.etUrl)
            assertEquals("System font scaling is honored", 20f * fontScale, address.textSize, 0.1f)
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
    private fun verifyGeometry(vb: ActivityMainBinding, fontScale: Float) {
        // One capsule row: every command is a 48dp control in a single focus chain.
        val capsuleIds = listOf(R.id.ibBack, R.id.ibForward, R.id.ibRefresh,
            R.id.ibHome, R.id.ibAdBlock, R.id.ibMenu)
        for (width in listOf(720, 960, 1280)) {
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(width * 9 / 16, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, width, width * 9 / 16)
            var previousRight = -1
            for (id in capsuleIds) {
                val view = vb.root.findViewById<View>(id)
                assertEquals("Uniform control width at $width", 48, view.width)
                assertEquals("Uniform control height at $width", 48, view.height)
                assertTrue("Controls must not overlap at $width", view.left >= previousRight)
                previousRight = view.right
                val parent = view.parent as View
                assertTrue(view.top >= parent.paddingTop)
                assertTrue(view.bottom <= parent.height - parent.paddingBottom)
                assertTrue(view.right <= parent.width - parent.paddingRight)
            }
            // Measure in root coordinates: the capsule sits inside the tab-strip surface.
            val surface = vb.rlActionBar
            val capsuleLeft = surface.left + vb.vActionBar.left
            val capsuleRight = surface.left + vb.vActionBar.right
            assertTrue("Capsule must fit within TV overscan at $width", capsuleLeft >= 16 &&
                capsuleRight <= width - 16)
            assertTrue("Capsule must stay on screen at $width",
                surface.top >= 0 && surface.bottom <= vb.root.height)
            val address = vb.root.findViewById<View>(R.id.etUrl)
            assertTrue("Address remains usable at $width", address.width >= 240)
            if (fontScale == 1f) assertEquals("Default address height", 48, address.height)
            else assertTrue("Accessible text may expand the field", address.height >= 48)
        }
    }

    private fun verifyStates(activity: AppCompatActivity, name: String) {
        val labels = listOf("Normal", "Foco", "Pulsado", "Activo", "Inactivo")
        val enabled = android.R.attr.state_enabled
        val focused = android.R.attr.state_focused
        val pressed = android.R.attr.state_pressed
        val checked = android.R.attr.state_checked
        val states = listOf(intArrayOf(enabled), intArrayOf(enabled, focused),
            intArrayOf(enabled, pressed), intArrayOf(enabled, checked), intArrayOf(-enabled))
        val sample = Bitmap.createBitmap(600, 104, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sample)
        canvas.drawColor(ContextCompat.getColor(activity, R.color.top_bar_background))
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(activity, R.color.day_night_text_color_contrast)
            textSize = 16f
        }
        val centers = mutableSetOf<Int>()
        for ((index, state) in states.withIndex()) {
            val drawable = AppCompatResources.getDrawable(activity, R.drawable.button_bg_selector)!!.mutate()
            drawable.state = state
            drawable.setBounds(12 + index * 120, 12, 108 + index * 120, 60)
            drawable.draw(canvas)
            centers.add(sample.getPixel(60 + index * 120, 36))
            canvas.drawText(labels[index], 12f + index * 120, 88f, labelPaint)
        }
        assertEquals("Normal/focus/pressed/checked/disabled must remain visually distinct", 5, centers.size)
        val text = ContextCompat.getColor(activity, R.color.day_night_text_color_contrast)
        val icon = ContextCompat.getColor(activity, R.color.day_night_icon_color)
        for (background in listOf(R.color.button_background, R.color.button_background_focused,
            R.color.button_background_pressed, R.color.ui_control_checked)) {
            val surface = ContextCompat.getColor(activity, background)
            assertTrue("Text contrast", ColorUtils.calculateContrast(text, surface) >= 4.5)
            assertTrue("Icon contrast", ColorUtils.calculateContrast(icon, surface) >= 4.5)
        }
        assertTrue("Focus outline contrast", ColorUtils.calculateContrast(
            ContextCompat.getColor(activity, R.color.ui_focus_border),
            ContextCompat.getColor(activity, R.color.button_background_focused)) >= 3.0)
        File("build/reports/ui/browser-states-$name.png").outputStream().use {
            assertTrue(sample.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        sample.recycle()
    }

}
