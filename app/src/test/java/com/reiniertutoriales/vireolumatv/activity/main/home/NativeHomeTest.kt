package com.reiniertutoriales.vireolumatv.activity.main.home

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.TabStore
import com.reiniertutoriales.vireolumatv.databinding.BrowserWindowBinding
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.model.Favorite
import kotlinx.coroutines.runBlocking
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RoboConfig
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@RoboConfig(application = VireoLumaTVApp::class, sdk = [28])
class NativeHomeTest {
    @Test fun focusDoesNotActivateSearchOrWriteStorageAndUpReturnsToTheCapsule() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead()
        val input = f.vb.nativeHomeContainer.findViewById<AppCompatEditText>(R.id.home_search)
        assertEquals(View.INVISIBLE, f.web.visibility)
        assertTrue(f.vb.pageContainer.cursorSuppressed)
        assertFalse(input.showSoftInputOnFocus)
        assertTrue(f.home.focusContent())
        assertTrue(input.hasFocus())
        assertEquals(1, f.web.scripts.size)
        assertFalse(f.web.scripts.single().contains("setItem"))
        assertFalse(f.web.scripts.single().contains("removeItem"))
        // The native focus graph reaches Submit and the grid; focusing a control never navigates.
        assertEquals(R.id.home_submit, input.focusSearch(View.FOCUS_RIGHT)?.id)
        assertNull(f.loadedUrl)
        input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(View.VISIBLE, f.vb.controlsOverlay.visibility)
        input.performClick() // The first native page click closes the capsule and reaches the field.
        assertEquals(View.INVISIBLE, f.vb.controlsOverlay.visibility)
        assertTrue(input.hasFocus())
        assertTrue(f.vb.pageContainer.cursorSuppressed)
        assertEquals(View.INVISIBLE, f.web.visibility)
    }

    @Test fun onlyTheExactInternalHomeUsesNativeControlsAndLateStorageCannotResurrectIt() = Fixture().use { f ->
        f.home.update(f.tab)
        val pending = requireNotNull(f.web.callback)
        assertTrue(f.home.isVisible)
        f.tab.url = "https://example.test/"
        f.web.address = f.tab.url
        f.home.update(f.tab)
        pending.onReceiveValue("{\"raw\":\"[\\\"https://stale.test/\\\"]\",\"hidden\":false}")
        assertFalse(f.home.isVisible)
        assertEquals(0, f.vb.nativeHomeContainer.childCount)
        assertEquals(View.VISIBLE, f.web.visibility)
        for (url in listOf(Config.HOME_PAGE_URL + "?external=1", Config.HOME_PAGE_URL + "#fragment",
            Config.HOME_PAGE_URL + "../home/", "https://other.test/")) {
            f.tab.url = url
            f.home.update(f.tab)
            assertFalse(url, f.home.isVisible)
        }
    }

    @Test fun legacyShortcutsRenderNativelyAndPreviewIsBoundedAndReleasedOnHide() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead("{\"raw\":\"[\\\"https://saved.test/\\\"]\",\"hidden\":false}")
        val grid = f.vb.nativeHomeContainer.findViewById<RecyclerView>(R.id.home_tiles)
        assertEquals("YouTube, saved shortcut, Add and Edit", 4, grid.adapter!!.itemCount)
        val tiles = (0 until grid.childCount).map { grid.getChildAt(it) }
        assertTrue(tiles.any { it.tag == "shortcut:https://saved.test/" })
        val preview = requireNotNull(f.home.preview(null))
        assertTrue(preview.width <= 480 && preview.height <= 480)
        assertSame(preview, f.home.preview(preview))
        f.home.hide()
        assertNull(f.home.preview(preview))
        assertFalse(f.vb.pageContainer.cursorSuppressed)
        preview.recycle()
    }

    @Test fun previewingANewTabHidesNativeHomeAndBackRestoresItWithoutChangingTabs() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead()
        f.activity.openCapsule()
        f.vb.tabStrip.current = 1
        runBlocking { f.activity.showPreview(null) }
        assertEquals(View.INVISIBLE, f.vb.nativeHomeContainer.visibility)
        assertSame(f.tab, f.tabs.currentTab.value)
        f.activity.closeCapsule()
        assertEquals(View.VISIBLE, f.vb.nativeHomeContainer.visibility)
        assertTrue(f.vb.nativeHomeContainer.findViewById<View>(R.id.home_search).hasFocus())
        assertSame(f.tab, f.tabs.currentTab.value)
    }

    @Test fun privateHomeKeepsTheSharedFavoritesPromisedByTheExistingPrivateMode() = Fixture().use { f ->
        val favorite = Favorite().apply { title = "Saved favorite"; url = "https://favorite.test/" }
        favorite.id = runBlocking { AppDatabase.db.favoriteDao().insert(favorite) }
        try {
            f.home.update(f.tab)
            f.finishRead()
            val grid = f.vb.nativeHomeContainer.findViewById<RecyclerView>(R.id.home_tiles)
            assertTrue((0 until grid.childCount).any { grid.getChildAt(it).tag == "favorite:https://favorite.test/" })
            f.home.hide()
            assertEquals(0, f.vb.nativeHomeContainer.childCount)
        } finally {
            runBlocking { AppDatabase.db.favoriteDao().delete(favorite) }
        }
    }

    @Test fun largeTextAndRtlLeaveTheNativeControlsMeasurableAndReachable() = Fixture().use { f ->
        val config = f.host.get().resources.configuration
        config.fontScale = 1.5f
        config.setLayoutDirection(java.util.Locale.forLanguageTag("he"))
        @Suppress("DEPRECATION")
        f.host.get().resources.updateConfiguration(config, f.host.get().resources.displayMetrics)
        f.home.update(f.tab)
        f.finishRead()
        val input = f.vb.nativeHomeContainer.findViewById<AppCompatEditText>(R.id.home_search)
        val submit = f.vb.nativeHomeContainer.findViewById<View>(R.id.home_submit)
        assertTrue(input.width > 0 && input.height > 0)
        assertTrue(submit.width > 0 && submit.height > 0)
        assertTrue(input.bottom <= f.vb.nativeHomeContainer.height)
        assertTrue(submit.requestFocus())
        assertNotNull(submit.focusSearch(View.FOCUS_DOWN))
    }

    private class StorageView(context: android.content.Context) : WebView(context) {
        var address = Config.HOME_PAGE_URL
        var callback: ValueCallback<String>? = null
        val scripts = mutableListOf<String>()
        override fun getUrl() = address
        override fun getProgress() = 100
        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            scripts += script
            callback = resultCallback
        }
    }

    private class Fixture : AutoCloseable {
        val host = Robolectric.buildActivity(AppCompatActivity::class.java).apply { get().setTheme(R.style.AppTheme); setup() }
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val vb = BrowserWindowBinding.inflate(host.get().layoutInflater)
        val tabs = ScreenModels.get(TabStore::class, host.get())
        val web = StorageView(host.get())
        var loadedUrl: String? = null
        val engine = Proxy.newProxyInstance(PageEngine::class.java.classLoader, arrayOf(PageEngine::class.java)) { _, method, args ->
            when (method.name) {
                "getView" -> web
                "getUrl" -> web.address
                "getCanGoBack", "getCanGoForward" -> false
                "load" -> { loadedUrl = args!![0] as String; null }
                else -> null
            }
        } as PageEngine
        val tab = BrowserTab(url = Config.HOME_PAGE_URL, selected = true)
        val home: NativeHome
        private val previousPrivate = AppContext.config.privateBrowsing
        init {
            AppContext.config.privateBrowsing = true
            BrowserTab::class.java.getDeclaredField("engine\$delegate").apply { isAccessible = true }.set(tab, lazyOf(engine))
            activity.vb = vb
            activity.tabStore = tabs
            activity.mainThread = Handler(Looper.getMainLooper())
            tabs.openTabs.clear(); tabs.openTabs.add(tab); tabs.currentTab.value = tab
            vb.pageContainer.addView(web)
            vb.controlsOverlay.visibility = View.INVISIBLE
            vb.tabPreview.visibility = View.INVISIBLE
            vb.startupSpinner.visibility = View.GONE
            host.get().setContentView(vb.root)
            activity.configureBrowserControls()
            home = NativeHome(activity, vb.nativeHomeContainer)
            activity.nativeHome = home
            layout()
        }
        fun finishRead(json: String = "{\"raw\":\"[]\",\"hidden\":false}") {
            requireNotNull(web.callback).onReceiveValue(json)
            // ListAdapter computes updates off the UI thread; drain the posted commit before inspecting focus.
            repeat(30) { Thread.sleep(5); shadowOf(Looper.getMainLooper()).idle(); layout() }
        }
        fun layout() {
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, 1280, 720)
        }
        override fun close() {
            home.hide()
            activity.lifecycleScope.cancel()
            tabs.currentTab.value = null
            tabs.openTabs.clear()
            AppContext.config.privateBrowsing = previousPrivate
            web.destroy()
            host.pause().stop().destroy()
        }
    }
}
