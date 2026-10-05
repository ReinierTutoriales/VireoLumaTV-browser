package com.reiniertutoriales.vireolumatv.activity.main

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.databinding.ActivityMainBinding
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import com.reiniertutoriales.vireolumatv.webengine.WebEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/** Exercise MainActivity's real handlers and XML hierarchy without loading a network page. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class BrowserNavigationTest {
    private class Fixture : AutoCloseable {
        val host = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.AppTheme)
            setup()
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val vb = ActivityMainBinding.inflate(host.get().layoutInflater)
        val tabs = ActiveModelsRepository.get(TabsModel::class, host.get())
        val page = View(host.get()).apply { isFocusableInTouchMode = true }
        var virtualCursor = true
        var exitedFullscreen = 0
        var wentBack = 0
        var refreshed = 0
        var loadedUrl: String? = null
        val thumbnail = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val engine = Proxy.newProxyInstance(WebEngine::class.java.classLoader,
            arrayOf(WebEngine::class.java)) { _, method, args ->
            when (method.name) {
                "getView" -> page
                "isVirtualCursorMode" -> virtualCursor
                "setVirtualCursorMode" -> { virtualCursor = args!![0] as Boolean; null }
                "canGoBack", "canGoForward" -> true
                "hideFullscreenView" -> { exitedFullscreen++; null }
                "goBack" -> { wentBack++; null }
                "reload" -> { refreshed++; null }
                "loadUrl" -> { loadedUrl = args!![0] as String; null }
                "renderThumbnail" -> thumbnail
                else -> null
            }
        } as WebEngine
        val tab = WebTabState(url = "https://example.test", selected = true).apply {
            thumbnail = this@Fixture.thumbnail
        }
        init {
            WebTabState::class.java.getDeclaredField("webEngine\$delegate").apply { isAccessible = true }
                .set(tab, lazyOf(engine))
            tabs.tabsStates.clear()
            tabs.tabsStates.add(tab)
            tabs.currentTab.value = tab
            set("vb", vb)
            set("tabsModel", tabs)
            set("settingsModel", ActiveModelsRepository.get(SettingsModel::class, host.get()))
            set("uiHandler", Handler(Looper.getMainLooper()))
            vb.flWebViewContainer.addView(page)
            vb.rlActionBar.visibility = View.INVISIBLE
            vb.llBottomPanel.visibility = View.INVISIBLE
            vb.ivMiniatures.visibility = View.INVISIBLE
            vb.progressBarGeneric.visibility = View.GONE
            host.get().setContentView(vb.root)
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, 1280, 720)
            call("configureBrowserControls")
            page.requestFocus()
        }
        fun set(name: String, value: Any) = MainActivity::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.set(activity, value)
        fun call(name: String) = MainActivity::class.java.getDeclaredMethod(name)
            .apply { isAccessible = true }.invoke(activity)
        fun key(view: View, code: Int) {
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        override fun close() {
            vb.root.removeAllViews()
            tabs.currentTab.value = null
            tabs.tabsStates.clear()
            host.pause().stop().destroy()
            thumbnail.recycle()
        }
    }

    @Test fun backOpensBothRowsAndUpReachesTabsWithoutHidingControls() = Fixture().use { f ->
        f.call("handleBackNavigation")
        assertEquals(View.VISIBLE, f.vb.rlActionBar.visibility)
        assertEquals(View.VISIBLE, f.vb.llBottomPanel.visibility)
        assertTrue(f.vb.ibBack.hasFocus())
        f.vb.ibRefresh.requestFocus()
        assertEquals("Focusing a lower command must not hide search/settings", View.VISIBLE, f.vb.rlActionBar.visibility)
        f.key(f.vb.ibRefresh, KeyEvent.KEYCODE_DPAD_UP)
        assertTrue("Up must reach an actionable tab control", f.vb.vTabs.hasFocus())
        assertEquals(View.VISIBLE, f.vb.llBottomPanel.visibility)
        f.vb.ibRefresh.requestFocus()
        f.key(f.vb.ibRefresh, KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(View.INVISIBLE, f.vb.rlActionBar.visibility)
        assertEquals(View.INVISIBLE, f.vb.llBottomPanel.visibility)
        assertTrue(f.page.hasFocus())
    }

    @Test fun pageCommandsRunOnceAndRestorePageFocus() = Fixture().use { f ->
        f.call("handleBackNavigation")
        f.vb.ibBack.performClick()
        assertEquals(1, f.wentBack)
        assertTrue(f.page.hasFocus())
        f.call("handleBackNavigation")
        f.vb.ibRefresh.performClick()
        assertEquals(1, f.refreshed)
        assertTrue(f.page.hasFocus())
    }

    @Test fun firstBackExitsFullscreenBeforeEnablingPointerOrOpeningChrome() = Fixture().use { f ->
        f.set("isFullscreen", true)
        f.virtualCursor = false
        f.call("handleBackNavigation")
        assertEquals(1, f.exitedFullscreen)
        assertFalse("Fullscreen exit belongs to the engine, not an early cursor-mode branch", f.virtualCursor)
        assertEquals(View.INVISIBLE, f.vb.rlActionBar.visibility)
        f.set("isFullscreen", false)
        f.call("handleBackNavigation")
        assertEquals(View.VISIBLE, f.vb.llBottomPanel.visibility)
        assertTrue(f.virtualCursor)
    }

    @Test fun cancelSearchRestoresButtonsAndKeyboardSearchLoadsGoogle() = Fixture().use { f ->
        f.call("handleBackNavigation")
        val address = f.vb.vActionBar.findViewById<EditText>(R.id.etUrl)
        address.requestFocus()
        assertTrue(f.vb.vActionBar.isEditingAddress)
        f.call("handleBackNavigation")
        assertFalse(f.vb.vActionBar.isEditingAddress)
        assertEquals(View.VISIBLE, f.vb.llBottomPanel.visibility)
        assertEquals(View.VISIBLE, f.vb.vActionBar.findViewById<View>(R.id.ibSettings).visibility)
        address.requestFocus()
        address.setText("android tv navegador")
        address.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        assertNotNull("TV keyboard Search must load a result", f.loadedUrl)
        assertTrue(f.loadedUrl!!.contains("android+tv+navegador"))
        assertEquals(View.INVISIBLE, f.vb.rlActionBar.visibility)
        assertTrue(f.page.hasFocus())
    }

    @Test fun rapidBackAndReopenCannotLeaveInvisibleOrUnfocusedChrome() = Fixture().use { f ->
        repeat(4) {
            f.call("handleBackNavigation")
            f.call("handleBackNavigation")
        }
        assertTrue(f.page.hasFocus())
        f.call("handleBackNavigation")
        assertEquals(View.VISIBLE, f.vb.rlActionBar.visibility)
        assertEquals(View.VISIBLE, f.vb.llBottomPanel.visibility)
        assertTrue(f.vb.ibBack.hasFocus())
        f.vb.rlActionBar.animate().cancel()
        f.vb.llBottomPanel.animate().cancel()
        f.call("handleBackNavigation")
        assertEquals(View.INVISIBLE, f.vb.rlActionBar.visibility)
        assertEquals(View.INVISIBLE, f.vb.llBottomPanel.visibility)
        assertTrue(f.page.hasFocus())
    }
}
