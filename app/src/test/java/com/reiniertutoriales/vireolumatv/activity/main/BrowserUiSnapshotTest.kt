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
            vb.flWebViewContainer.setBackgroundColor(Color.rgb(100, 116, 139))
            vb.ivMiniatures.visibility = View.GONE
            vb.vCursorMenu.visibility = View.INVISIBLE
            vb.progressBar.visibility = View.GONE
            vb.progressBarGeneric.visibility = View.GONE
            vb.vActionBar.setAddressBoxText("https://example.test/documentacion/una-direccion-larga")
            vb.ibForward.isEnabled = false
            activity.setContentView(vb.root)
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, 960, 540)
            vb.ibBack.isFocusableInTouchMode = true
            assertTrue(vb.ibBack.requestFocus())
            vb.root.refreshDrawableState()
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            vb.root.draw(Canvas(bitmap))
            val output = File("build/reports/ui/browser-$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("Native drawing must include browser controls", bitmap.getPixel(10, 10) !=
                Color.rgb(100, 116, 139))
            bitmap.recycle()
            val address = vb.root.findViewById<TextView>(R.id.etUrl)
            assertEquals("System font scaling is honored", 18f * fontScale, address.textSize, 0.1f)
            assertTrue("Enlarged URL text fits vertically", address.layout.getLineBottom(0) <=
                address.height - address.compoundPaddingTop - address.compoundPaddingBottom)
            verifyGeometry(vb)
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
    private fun verifyGeometry(vb: ActivityMainBinding) {
        val topIds = listOf(R.id.ibVoiceSearch, R.id.ibHistory, R.id.ibFavorites,
            R.id.ibDownloads, R.id.ibIncognito, R.id.ibSettings, R.id.ibMenu)
        val bottomIds = listOf(R.id.ibCloseTab, R.id.ibBack, R.id.ibForward, R.id.ibRefresh,
            R.id.ibAdBlock, R.id.ibPopupBlock, R.id.ibHome)
        for (width in listOf(720, 960, 1280)) {
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(width * 9 / 16, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, width, width * 9 / 16)
            for (ids in listOf(topIds, bottomIds)) {
                var previousRight = -1
                for (id in ids) {
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
            }
            val address = vb.root.findViewById<View>(R.id.etUrl)
            assertTrue("Address remains usable at $width", address.width >= 240)
            assertEquals(48, address.height)
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
