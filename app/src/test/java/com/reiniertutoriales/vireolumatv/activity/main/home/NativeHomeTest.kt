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
        // Right moves focus to Submit from the end of the text, and moves the cursor inside it.
        input.setText("tv")
        input.setSelection(1)
        input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue("Right inside the text stays in the field", input.hasFocus())
        input.setSelection(2)
        input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        input.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(R.id.home_submit, f.vb.nativeHomeContainer.findFocus()?.id)
        assertNull(f.loadedUrl)
        input.setText("")
        assertTrue(f.home.focusContent())
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

    @Test fun editingOffersMoveAndRemoveForShortcutsAndFavoritesAndSavesTheOrder() = Fixture().use { f ->
        val favorite = Favorite().apply { title = "Saved favorite"; url = "https://favorite.test/" }
        favorite.id = runBlocking { AppDatabase.db.favoriteDao().insert(favorite) }
        try {
            f.home.update(f.tab)
            f.finishRead("{\"raw\":\"[\\\"https://a.test/\\\"]\",\"hidden\":false}")
            val grid = f.vb.nativeHomeContainer.findViewById<RecyclerView>(R.id.home_tiles)
            fun tile(key: String) = (0 until grid.childCount).map { grid.getChildAt(it) }.first { it.tag == key }
            fun keys() = (0 until grid.childCount).map { grid.getChildAt(it).tag as String }
            assertEquals(listOf("youtube", "favorite:https://favorite.test/", "shortcut:https://a.test/", "add", "edit"), keys())
            tile("edit").performClick()
            f.drain()

            // A favorite is no longer a dead end while editing: it can be moved and removed here.
            tile("favorite:https://favorite.test/").performClick()
            val menu = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            val labels = (0 until menu.listView.adapter.count).map { menu.listView.adapter.getItem(it).toString() }
            assertEquals(listOf(R.string.home_move_left, R.string.home_move_right, R.string.home_remove)
                .map { f.host.get().getString(it) }, labels)

            // Moving the shortcut left saves the whole arrangement, which the next read applies.
            menu.dismiss()
            tile("shortcut:https://a.test/").performClick()
            val actions = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            val shortcutLabels = (0 until actions.listView.adapter.count).map { actions.listView.adapter.getItem(it).toString() }
            assertEquals(listOf(R.string.home_move_left, R.string.home_edit_address, R.string.home_remove)
                .map { f.host.get().getString(it) }, shortcutLabels)
            actions.listView.performItemClick(null, 0, 0)
            val saved = """["youtube","shortcut:https://a.test/","favorite:https://favorite.test/"]"""
            assertTrue(f.web.scripts.last().contains(org.json.JSONObject.quote(HomeShortcuts.ORDER_KEY)))
            val written = org.json.JSONArray(listOf("youtube", "shortcut:https://a.test/", "favorite:https://favorite.test/")).toString()
            assertTrue(f.web.scripts.last().contains(org.json.JSONObject.quote(written)))
            f.finishRead(org.json.JSONObject().put("raw", "[\"https://a.test/\"]").put("hidden", false)
                .put("order", saved).toString())
            assertEquals(listOf("youtube", "shortcut:https://a.test/", "favorite:https://favorite.test/", "add", "edit"), keys())
        } finally {
            runBlocking { AppDatabase.db.favoriteDao().delete(favorite) }
        }
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

    @Test fun backgroundClicksCannotCaptureDpadFocusAndAnotherGridRowRemainsReachable() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead("{\"raw\":\"[\\\"https://saved.test/\\\"]\",\"hidden\":false}")
        repeat(2) { f.layout(640, 320); shadowOf(Looper.getMainLooper()).idle() }
        val content = f.vb.nativeHomeContainer.getChildAt(0)
        content.performClick()
        assertFalse("The background must never become a keyboard focus target", content.isFocusable)
        f.vb.root.clearFocus()
        val target = android.view.FocusFinder.getInstance().findNextFocus(f.vb.root, null, View.FOCUS_DOWN)
        assertNotNull(target)
        assertNotSame(content, target)
        val grid = f.vb.nativeHomeContainer.findViewById<RecyclerView>(R.id.home_tiles)
        val youtube = (0 until grid.childCount).map { grid.getChildAt(it) }.first { it.tag == "youtube" }
        assertTrue(youtube.requestFocus())
        val edit = youtube.focusSearch(View.FOCUS_DOWN)
        assertNotNull("Edit on the next row must stay reachable on a short window", edit)
        assertEquals("edit", edit.tag)
        assertTrue(edit.requestFocus())
        assertTrue(grid.hasFocus())
    }

    @Test fun privateHomeKeepsTheSharedFavoritesPromisedByTheExistingPrivateMode() = Fixture().use { f ->
        val favorite = Favorite().apply { title = "Saved favorite"; url = "https://favorite.test/" }
        favorite.id = runBlocking { AppDatabase.db.favoriteDao().insert(favorite) }
        val internal = Favorite().apply { title = "Internal page"; url = Config.HOME_PAGE_URL }
        internal.id = runBlocking { AppDatabase.db.favoriteDao().insert(internal) }
        try {
            f.home.update(f.tab)
            f.finishRead()
            val grid = f.vb.nativeHomeContainer.findViewById<RecyclerView>(R.id.home_tiles)
            assertTrue((0 until grid.childCount).any { grid.getChildAt(it).tag == "favorite:https://favorite.test/" })
            assertEquals("Internal destinations are excluded, just as in the previous Home", 4, grid.adapter!!.itemCount)
            f.home.hide()
            assertEquals(0, f.vb.nativeHomeContainer.childCount)
        } finally {
            runBlocking {
                AppDatabase.db.favoriteDao().delete(favorite)
                AppDatabase.db.favoriteDao().delete(internal)
            }
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
            drain()
        }
        /** ListAdapter computes updates off the UI thread; drain the posted commit before inspecting focus. */
        fun drain() = repeat(30) { Thread.sleep(5); shadowOf(Looper.getMainLooper()).idle(); layout() }
        fun layout(width: Int = 1280, height: Int = 720) {
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, width, height)
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
