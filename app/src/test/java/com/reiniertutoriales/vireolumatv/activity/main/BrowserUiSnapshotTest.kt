package com.reiniertutoriales.vireolumatv.activity.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
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

    private fun capture(name: String) {
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
        } finally {
            vb.root.removeAllViews()
            tabs.currentTab.value = null
            tabs.tabsStates.clear()
            host.pause().stop().destroy()
            AppCompatDelegate.setDefaultNightMode(previousMode)
        }
    }
}
