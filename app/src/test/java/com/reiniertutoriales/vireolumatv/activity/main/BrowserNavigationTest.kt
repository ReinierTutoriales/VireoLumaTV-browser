package com.reiniertutoriales.vireolumatv.activity.main

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.ImageView
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.cancel
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.databinding.ActivityMainBinding
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import com.reiniertutoriales.vireolumatv.webengine.WebEngine
import com.reiniertutoriales.vireolumatv.webengine.WebEngineWindowProviderCallback
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewWebEngine
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
        var hideFullscreen: (() -> Unit)? = null
        var wentBack = 0
        var refreshed = 0
        var thumbnailRenders = 0
        var loadedUrl: String? = null
        val thumbnail = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val engine = Proxy.newProxyInstance(WebEngine::class.java.classLoader,
            arrayOf(WebEngine::class.java)) { _, method, args ->
            when (method.name) {
                "getView" -> page
                "isVirtualCursorMode" -> virtualCursor
                "setVirtualCursorMode" -> { virtualCursor = args!![0] as Boolean; null }
                "canGoBack", "canGoForward" -> true
                "hideFullscreenView" -> { exitedFullscreen++; hideFullscreen?.invoke(); null }
                "goBack" -> { wentBack++; null }
                "reload" -> { refreshed++; null }
                "loadUrl" -> { loadedUrl = args!![0] as String; null }
                "renderThumbnail" -> { thumbnailRenders++; thumbnail }
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
            activity.lifecycleScope.cancel()
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

    @Test fun controlsRefreshBackAfterHistoryChangedWithoutPageLoad() = Fixture().use { f ->
        // pushState (YouTube and other SPAs) changes history without onPageStarted/onPageFinished.
        f.vb.ibBack.isEnabled = false
        f.vb.ibForward.isEnabled = false
        f.call("handleBackNavigation")
        assertTrue(f.vb.ibBack.isEnabled)
        assertTrue(f.vb.ibForward.isEnabled)
        assertTrue("Back must be reachable as the first bottom-bar command", f.vb.ibBack.hasFocus())
    }

    @Test fun tabsLeadToSearchAndExitingAddressDoesNotFocusCloseApp() = Fixture().use { f ->
        val bar = f.vb.vActionBar
        val address = bar.findViewById<EditText>(R.id.etUrl)
        val close = bar.findViewById<View>(R.id.ibMenu)
        assertEquals(R.id.flUrl, bar.getChildAt(0).id)
        assertEquals(R.id.ibMenu, bar.getChildAt(bar.childCount - 1).id)
        f.call("handleBackNavigation")
        val tab = f.vb.vTabs.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTabs)
            .getChildAt(0)
        assertNotNull("The real tab must be attached for this focus test", tab)
        assertSame(f.tab, tab.tag)
        assertTrue(tab.requestFocus())
        // Robolectric does not run ViewRoot's default D-pad traversal for a key dispatched
        // directly to a child, so exercise the focused child's actual focus graph.
        val tabUpTarget = tab.focusSearch(View.FOCUS_UP)
        assertSame("Up from the real tab must target Search", address, tabUpTarget)
        assertTrue(tabUpTarget.requestFocus())
        assertTrue("Up from the real tab must focus Search", address.hasFocus())
        assertFalse("Focusing Search must leave the toolbar available", bar.isEditingAddress)
        assertEquals(View.VISIBLE, bar.findViewById<View>(R.id.ibSettings).visibility)
        f.key(address, KeyEvent.KEYCODE_DPAD_RIGHT)
        val voice = bar.findViewById<View>(R.id.ibVoiceSearch)
        val expectedRight = if (voice.parent == bar) voice else bar.findViewById<View>(R.id.ibHistory)
        assertTrue("Right from Search must reach the next toolbar action", expectedRight.hasFocus())
        assertFalse(close.hasFocus())
        assertFalse(bar.isEditingAddress)

        val add = f.vb.vTabs.findViewById<View>(R.id.btnAdd)
        assertTrue(add.requestFocus())
        val addUpTarget = add.focusSearch(View.FOCUS_UP)
        assertSame("Up from Add tab must target Search", address, addUpTarget)
        assertTrue(addUpTarget.requestFocus())
        assertTrue("Up from Add tab must focus Search", address.hasFocus())
        assertFalse(bar.isEditingAddress)
        f.key(address, KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(bar.isEditingAddress)
        f.call("handleBackNavigation")
        assertFalse(bar.isEditingAddress)
        assertEquals(View.VISIBLE, f.vb.llBottomPanel.visibility)
        assertFalse(close.hasFocus())
        assertTrue(bar.findViewById<View>(R.id.ibHistory).hasFocus())
    }

    @Test fun activePageStaysSharpAndFirstTouchReachesTheRealPage() = Fixture().use { f ->
        var clicks = 0
        f.page.setOnClickListener { clicks++ }
        f.call("showMenuOverlay")
        assertEquals(View.VISIBLE, f.vb.flWebViewContainer.visibility)
        assertEquals(View.INVISIBLE, f.vb.ivMiniatures.visibility)
        assertTrue(f.vb.flWebViewContainer.cursorSuppressed)
        assertEquals("Opening controls must not allocate an active-page screenshot", 0, f.thumbnailRenders)
        assertEquals(ImageView.ScaleType.CENTER_INSIDE, f.vb.ivMiniatures.scaleType)
        // A page click also exits native address editing without leaving its keyboard/draft behind.
        f.vb.vActionBar.callback = f.activity
        f.vb.vActionBar.setAddressBoxText(f.tab.url)
        val address = f.vb.vActionBar.findViewById<EditText>(R.id.etUrl)
        address.requestFocus()
        address.performClick()
        address.setText("unfinished address draft")
        assertTrue(f.vb.vActionBar.isEditingAddress)
        val now = android.os.SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now, action, 640f, 360f, 0)
            f.vb.flWebViewContainer.dispatchTouchEvent(event)
            event.recycle()
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("The click that resumes the page must reach its search field", 1, clicks)
        assertEquals(View.INVISIBLE, f.vb.rlActionBar.visibility)
        assertFalse(f.vb.flWebViewContainer.cursorSuppressed)
        assertNull(f.vb.flWebViewContainer.onPageTouch)
        assertFalse(f.vb.vActionBar.isEditingAddress)
        assertEquals(f.tab.url, address.text.toString())
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

    @Test fun fullscreenExitRestoresOneCoherentMenuAndNextBackReturnsToPage() = Fixture().use { f ->
        // Exercise the real engine's fullscreen teardown and the real Activity callback together.
        val callback = Class.forName("com.reiniertutoriales.vireolumatv.activity.main.MainActivity\$WebEngineCallback")
            .getDeclaredConstructor(MainActivity::class.java, WebTabState::class.java)
            .apply { isAccessible = true }.newInstance(f.activity, f.tab) as WebEngineWindowProviderCallback
        val engine = WebViewWebEngine(f.tab).apply { this.callback = callback }
        WebViewWebEngine::class.java.getDeclaredField("viewParent").apply { isAccessible = true }
            .set(engine, f.vb.flWebViewContainer)
        val video = View(f.host.get()).apply { isFocusableInTouchMode = true }
        WebViewWebEngine::class.java.getDeclaredMethod("enterFullscreenView", View::class.java)
            .apply { isAccessible = true }.invoke(engine, video)
        assertFalse(f.vb.flWebViewContainer.cursorEnabled)
        assertTrue(video.hasFocus())
        f.hideFullscreen = { engine.hideFullscreenView() }
        f.virtualCursor = false
        f.call("handleBackNavigation")
        assertEquals(1, f.exitedFullscreen)
        assertNull(video.parent)
        assertTrue(f.vb.flWebViewContainer.cursorEnabled)
        assertEquals(View.VISIBLE, f.vb.rlActionBar.visibility)
        assertEquals(View.VISIBLE, f.vb.llBottomPanel.visibility)
        assertTrue(f.vb.ibBack.hasFocus())
        f.call("handleBackNavigation")
        assertEquals("Back must close the existing menu, not reopen bars in an unknown state",
            View.INVISIBLE, f.vb.rlActionBar.visibility)
        assertEquals(View.INVISIBLE, f.vb.llBottomPanel.visibility)
        assertTrue(f.page.hasFocus())
    }

    @Test fun cancelSearchRestoresButtonsAndKeyboardSearchLoadsConfiguredEngine() = Fixture().use { f ->
        f.call("handleBackNavigation")
        val address = f.vb.vActionBar.findViewById<EditText>(R.id.etUrl)
        address.requestFocus()
        address.performClick()
        assertTrue(f.vb.vActionBar.isEditingAddress)
        f.call("handleBackNavigation")
        assertFalse(f.vb.vActionBar.isEditingAddress)
        assertEquals(View.VISIBLE, f.vb.llBottomPanel.visibility)
        assertEquals(View.VISIBLE, f.vb.vActionBar.findViewById<View>(R.id.ibSettings).visibility)
        address.requestFocus()
        address.performClick()
        address.setText("android tv navegador")
        address.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        assertNotNull("TV keyboard Search must load a result", f.loadedUrl)
        assertTrue(f.loadedUrl!!.contains("android+tv+navegador"))
        assertEquals(View.INVISIBLE, f.vb.rlActionBar.visibility)
        assertTrue(f.page.hasFocus())
    }

    @Test fun pageUpdatesCannotOverwriteTypingAndPhysicalEnterSubmitsOnce() = Fixture().use { f ->
        f.call("handleBackNavigation")
        val address = f.vb.vActionBar.findViewById<EditText>(R.id.etUrl)
        f.vb.vActionBar.setAddressBoxText("https://before.test")
        address.requestFocus()
        address.performClick()
        address.setText("my search")
        f.vb.vActionBar.setAddressBoxText("https://redirected.test")
        assertEquals("my search", address.text.toString())
        f.call("handleBackNavigation")
        assertEquals("https://redirected.test", address.text.toString())
        address.requestFocus()
        address.performClick()
        address.setText("https://typed.test")
        f.key(address, KeyEvent.KEYCODE_ENTER)
        assertEquals("https://typed.test", f.loadedUrl)
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
