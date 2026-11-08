package com.reiniertutoriales.vireolumatv.activity.main.home

import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.TabStore
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.databinding.BrowserWindowBinding
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.lang.reflect.Proxy
import org.robolectric.annotation.Config as RoboConfig
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@RoboConfig(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class NativeHomeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun remote() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun focusedTag(): String =
        compose.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag]

    private fun press(key: Key) = compose.onNode(isFocused()).performKeyInput { pressKey(key) }

    @Test fun focusDoesNotActivateSearchOrWriteStorageAndUpReturnsToTheCapsule() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead()
        assertEquals(View.INVISIBLE, f.web.visibility)
        assertTrue(f.vb.pageContainer.cursorSuppressed)
        assertTrue(f.home.focusContent())
        compose.waitForIdle()
        assertEquals("home_page_search", focusedTag())
        assertEquals(1, f.web.scripts.size)
        assertFalse(f.web.scripts.single().contains("setItem"))
        assertFalse(f.web.scripts.single().contains("removeItem"))
        // Focus reaches Submit and back; focusing a control never navigates.
        press(Key.DirectionRight)
        assertEquals("home_submit", focusedTag())
        press(Key.DirectionLeft)
        assertEquals("home_page_search", focusedTag())
        assertNull(f.loadedUrl)
        // OK starts typing. Right inside the text moves the cursor; at its end it moves to Submit.
        press(Key.DirectionCenter)
        compose.onNodeWithTag("home_page_search").performTextInput("tv")
        compose.onNodeWithTag("home_page_search").performTextInputSelection(TextRange(1))
        press(Key.DirectionRight)
        assertEquals("Right inside the text stays in the field", "home_page_search", focusedTag())
        compose.onNodeWithTag("home_page_search").performTextInputSelection(TextRange(2))
        press(Key.DirectionRight)
        assertEquals("home_submit", focusedTag())
        assertNull(f.loadedUrl)
        // Up from the field opens the capsule; a click on the field closes it and starts typing.
        assertTrue(f.home.focusContent())
        compose.waitForIdle()
        press(Key.DirectionUp)
        assertEquals(View.VISIBLE, f.vb.controlsOverlay.visibility)
        compose.onNodeWithTag("home_page_search").performClick()
        compose.waitForIdle()
        assertEquals(View.INVISIBLE, f.vb.controlsOverlay.visibility)
        compose.onNodeWithTag("home_page_search").assertIsFocused()
        assertTrue(f.vb.pageContainer.cursorSuppressed)
        assertEquals(View.INVISIBLE, f.web.visibility)
    }

    @Test fun emptySearchShowsWhyAndNeverNavigates() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead()
        compose.onNodeWithTag("home_submit").performClick()
        compose.onNodeWithTag("home_search_error").assertIsDisplayed()
        assertNull(f.loadedUrl)
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

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun legacyShortcutsRenderNativelyAndPreviewIsBoundedAndReleasedOnHide() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead("{\"raw\":\"[\\\"https://saved.test/\\\"]\",\"hidden\":false}")
        assertEquals("YouTube, saved shortcut, Add and Edit",
            listOf("youtube", "shortcut:https://saved.test/", "add", "edit"), f.home.tileKeys)
        compose.onNodeWithTag("home_tile_shortcut:https://saved.test/").assertIsDisplayed()
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
            assertEquals(listOf("youtube", "favorite:https://favorite.test/", "shortcut:https://a.test/", "add", "edit"),
                f.home.tileKeys)
            compose.onNodeWithTag("home_tile_edit").performClick()
            f.drain()

            // A favorite is no longer a dead end while editing: it can be moved and removed here.
            compose.onNodeWithTag("home_tile_favorite:https://favorite.test/").performClick()
            val menu = ShadowDialog.getLatestDialog() as TvDialog
            assertEquals(listOf(R.string.home_move_left, R.string.home_move_right, R.string.home_remove)
                .map { compose.activity.getString(it) }, menu.items.map { it.toString() })

            // Moving the shortcut left saves the whole arrangement, which the next read applies.
            compose.runOnUiThread { menu.dismiss() }
            compose.onNodeWithTag("home_tile_shortcut:https://a.test/").performClick()
            val actions = ShadowDialog.getLatestDialog() as TvDialog
            assertEquals(listOf(R.string.home_move_left, R.string.home_edit_address, R.string.home_remove)
                .map { compose.activity.getString(it) }, actions.items.map { it.toString() })
            compose.runOnUiThread { actions.choose(0) }
            val saved = listOf("youtube", "shortcut:https://a.test/", "favorite:https://favorite.test/")
            assertTrue(f.web.scripts.last().contains(org.json.JSONObject.quote(HomeShortcuts.ORDER_KEY)))
            assertTrue(f.web.scripts.last().contains(org.json.JSONObject.quote(org.json.JSONArray(saved).toString())))
            f.finishRead(org.json.JSONObject().put("raw", "[\"https://a.test/\"]").put("hidden", false)
                .put("order", org.json.JSONArray(saved).toString()).toString())
            assertEquals(saved + listOf("add", "edit"), f.home.tileKeys)
        } finally {
            runBlocking { AppDatabase.db.favoriteDao().delete(favorite) }
        }
    }

    @Test fun anInvalidOrDuplicateAddressKeepsTheEditorOpenWithItsReason() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead("{\"raw\":\"[\\\"https://a.test/\\\"]\",\"hidden\":false}")
        compose.onNodeWithTag("home_tile_add").performClick()
        val editor = ShadowDialog.getLatestDialog() as TvDialog
        compose.runOnUiThread {
            editor.setFieldValue(0, "a.test")
            editor.press(android.content.DialogInterface.BUTTON_POSITIVE)
        }
        assertTrue(editor.isShowing)
        assertEquals(compose.activity.getString(R.string.home_duplicate), editor.error)
        assertEquals("Nothing is written for a refused address", 1, f.web.scripts.size)
        compose.runOnUiThread {
            editor.setFieldValue(0, "b.test")
            editor.press(android.content.DialogInterface.BUTTON_POSITIVE)
        }
        assertFalse(editor.isShowing)
        assertTrue(f.web.scripts.last().contains(org.json.JSONObject.quote(
            org.json.JSONArray(listOf("https://a.test/", "https://b.test/")).toString())))
    }

    @Test fun previewingANewTabHidesNativeHomeAndBackRestoresItWithoutChangingTabs() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead()
        f.activity.openCapsule()
        f.vb.controlsHost.current = 1
        runBlocking { f.activity.showPreview(null) }
        assertEquals(View.INVISIBLE, f.vb.nativeHomeContainer.visibility)
        assertSame(f.tab, f.tabs.currentTab.value)
        f.activity.closeCapsule()
        compose.waitForIdle()
        assertEquals(View.VISIBLE, f.vb.nativeHomeContainer.visibility)
        val holder = compose.activity.window.decorView.findFocus()
        assertTrue("Home holds the window focus, not $holder", holder != null && f.vb.nativeHomeContainer.hasFocus())
        compose.onNodeWithTag("home_page_search").assertIsFocused()
        assertSame(f.tab, f.tabs.currentTab.value)
    }

    @RoboConfig(qualifiers = "w640dp-h320dp-mdpi")
    @Test fun onlyControlsAreFocusStopsAndTheNextGridRowRemainsReachable() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead("{\"raw\":\"[\\\"https://saved.test/\\\"]\",\"hidden\":false}")
        // The background closes the capsule on a click but is never a keyboard focus target.
        val stops = compose.onAllNodes(isFocusable()).fetchSemanticsNodes()
            .map { it.config.getOrElse(SemanticsProperties.TestTag) { "" } }
            .filter { it == "home_page_search" || it == "home_submit" || it.startsWith("home_tile_") }
        // The lazy grid composes only the rows in view, so later tiles may be absent here.
        assertEquals(listOf("home_page_search", "home_submit"), stops.take(2))
        assertTrue(stops.drop(2).all { it.removePrefix("home_tile_") in f.home.tileKeys })
        // Three columns on this short window: Edit is alone on the second row, below YouTube.
        compose.onNodeWithTag("home_tile_youtube").requestFocus()
        press(Key.DirectionDown)
        assertEquals("home_tile_edit", focusedTag())
    }

    @Test fun privateHomeKeepsTheSharedFavoritesPromisedByTheExistingPrivateMode() = Fixture().use { f ->
        val favorite = Favorite().apply { title = "Saved favorite"; url = "https://favorite.test/" }
        favorite.id = runBlocking { AppDatabase.db.favoriteDao().insert(favorite) }
        val internal = Favorite().apply { title = "Internal page"; url = Config.HOME_PAGE_URL }
        internal.id = runBlocking { AppDatabase.db.favoriteDao().insert(internal) }
        try {
            f.home.update(f.tab)
            f.finishRead()
            assertTrue("favorite:https://favorite.test/" in f.home.tileKeys)
            assertEquals("Internal destinations are excluded, just as in the previous Home", 4, f.home.tileKeys.size)
            f.home.hide()
            assertEquals(0, f.vb.nativeHomeContainer.childCount)
        } finally {
            runBlocking {
                AppDatabase.db.favoriteDao().delete(favorite)
                AppDatabase.db.favoriteDao().delete(internal)
            }
        }
    }

    @RoboConfig(qualifiers = "ldrtl-w960dp-h540dp-mdpi", fontScale = 1.5f)
    @Test fun largeTextAndRtlLeaveTheNativeControlsMeasurableAndReachable() = Fixture().use { f ->
        f.home.update(f.tab)
        f.finishRead()
        compose.onNodeWithTag("home_page_search").assertIsDisplayed()
        compose.onNodeWithTag("home_submit").assertIsDisplayed().requestFocus()
        press(Key.DirectionDown)
        assertTrue(focusedTag().startsWith("home_tile_"))
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

    private inner class Fixture : AutoCloseable {
        val host = compose.activity
        val activity: MainActivity = Robolectric.buildActivity(MainActivity::class.java).get()
        val vb = BrowserWindowBinding.inflate(LayoutInflater.from(ContextThemeWrapper(host, R.style.AppTheme)))
        val tabs = ScreenModels.get(TabStore::class, host)
        val web = StorageView(host)
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
            host.setContentView(vb.root)
            activity.configureBrowserControls()
            home = NativeHome(activity, vb.nativeHomeContainer)
            activity.nativeHome = home
            compose.waitForIdle()
        }

        fun finishRead(json: String = "{\"raw\":\"[]\",\"hidden\":false}") {
            requireNotNull(web.callback).onReceiveValue(json)
            drain()
        }

        /** Room delivers favorites off the UI thread; let the posted updates and composition settle. */
        fun drain() = repeat(30) { Thread.sleep(5); shadowOf(Looper.getMainLooper()).idle(); compose.waitForIdle() }

        override fun close() {
            home.hide()
            activity.lifecycleScope.cancel()
            tabs.currentTab.value = null
            tabs.openTabs.clear()
            AppContext.config.privateBrowsing = previousPrivate
            web.destroy()
        }
    }
}
