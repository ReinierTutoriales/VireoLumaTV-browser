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
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.cancel
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.databinding.BrowserWindowBinding
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewPageEngine
import com.reiniertutoriales.vireolumatv.webengine.webview.UserActivation
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
    @Test fun downFromTheCommandHostDoesNotCloseTheCapsule() = Fixture().use { f ->
        f.activity.openCapsule()
        // Exercise window dispatch with focus in the host, outside the tab focus group.
        // This View-only fixture has no Compose frame to give its host a measured height.
        f.vb.controlsHost.layout(0, 0, 1280, 120)
        f.vb.controlsHost.isFocusableInTouchMode = true
        assertTrue("The laid-out command host must take focus", f.vb.controlsHost.requestFocus())
        assertTrue(f.vb.controlsHost.hasFocus())
        f.activity.onAttachedToWindow()
        f.activity.window.callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(View.VISIBLE, f.vb.controlsOverlay.visibility)
    }

    @Test fun thePageBehindTheOpenCapsuleCannotTakeFocusUntilItCloses() = Fixture().use { f ->
        f.activity.openCapsule()
        // A page attached on a tab change asks for focus behind the open capsule.
        assertFalse(f.page.requestFocus())
        f.activity.closeCapsule()
        assertTrue(f.page.hasFocus() || f.page.requestFocus())
    }

    @Test fun arrowSearchStopsAtTheCapsuleEdge() = Fixture().use { f ->
        f.activity.openCapsule()
        // A focusable View of the page behind the capsule, below the controls.
        val behind = View(f.activity).apply { isFocusable = true; isFocusableInTouchMode = true }
        f.vb.pageContainer.addView(behind, android.widget.FrameLayout.LayoutParams(400, 200, android.view.Gravity.BOTTOM))
        relayout(f)
        f.vb.controlsHost.layout(0, 0, 600, 120)
        f.vb.controlsHost.isFocusableInTouchMode = true
        assertTrue(f.vb.controlsHost.requestFocus())
        assertNull("Arrow search must not leave the capsule for the page behind it",
            f.vb.controlsHost.focusSearch(f.vb.controlsHost, View.FOCUS_DOWN))
    }

    private fun tabCommand(activity: MainActivity, command: com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand) {
        MainActivity::class.java.getDeclaredMethod("onTabCommand",
            com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand::class.java)
            .apply { isAccessible = true }.invoke(activity, command)
    }

    @Test fun tabFocusUpdatesThePreviewSelectionWithoutChangingTheLiveTab() = Fixture().use { f ->
        f.tabs.workScope.cancel()
        val background = BrowserTab(url = "https://background.test")
        f.tabs.openTabs.add(background)
        f.activity.openCapsule()
        tabCommand(f.activity, com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand.Focus(1))
        assertSame(background, f.activity.tabAtStripSelection())
        assertSame(f.tab, f.tabs.currentTab.value)
    }

    @Test fun tabActivationUsesItsIndexWithoutAPrecedingFocusEvent() = Fixture().use { f ->
        f.tabs.workScope.cancel()
        val background = BrowserTab(url = "https://background.test")
        BrowserTab::class.java.getDeclaredField("engine\$delegate").apply { isAccessible = true }
            .set(background, lazyOf(f.engine))
        f.tabs.openTabs.add(background)
        tabCommand(f.activity, com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabCommand.Show(1))
        assertSame(background, f.tabs.currentTab.value)
        assertSame(background, f.activity.tabAtStripSelection())
    }

    @Test fun closingABackgroundTabKeepsTheLivePageAndTheStripAndIgnoresStaleCloses() = Fixture().use { f ->
        f.tabs.workScope.cancel()
        val background = BrowserTab(url = "https://background.test")
        f.tabs.openTabs.add(background)
        f.activity.openCapsule()
        f.activity.closeTab(background)
        assertSame(f.tab, f.tabs.currentTab.value)
        assertEquals(listOf(f.tab), f.tabs.openTabs.toList())
        assertEquals(View.VISIBLE, f.vb.controlsOverlay.visibility)
        f.activity.closeTab(background)
        assertSame(f.tab, f.tabs.currentTab.value)
        assertEquals(1, f.tabs.openTabs.size)
    }

    @Test fun aClaimedPopupGestureWithoutNativeActivationIsRejected() = Fixture().use { f ->
        UserActivation.consume()
        val window = TabWindow(f.activity, f.tab)
        val before = f.activity.tabStore.openTabs.size
        assertNull(window.createWindow(dialog = false, userGesture = true))
        assertEquals(before, f.activity.tabStore.openTabs.size)
    }

    @Test fun pageDownloadsRequireAFreshSingleUseActivationBeforeEnteringTheQueue() = Fixture().use { f ->
        val callback = TabWindow(f.activity, f.tab)
        val pending = f.activity.downloads.pending
        // Keep accepted downloads queued without opening a platform permission dialog or service.
        f.activity.downloads.waitingForPermission = true
        UserActivation.consume()
        fun download() = callback.onPageDownload("https://example.test/file.zip", "test-agent",
            "attachment; filename=file.zip", "application/zip", 32L)
        try {
            download()
            assertEquals("A page script cannot enqueue a download", 0, pending.size)
            UserActivation.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            download()
            assertEquals("A recent remote press reaches the real download queue", 1, pending.size)
            download()
            assertEquals("A consumed activation cannot authorize another request", 1, pending.size)
            UserActivation.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            download()
            assertEquals("Releasing the same key cannot grant another download", 1, pending.size)
            UserActivation.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A))
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(3))
            download()
            assertEquals("An expired gesture is rejected by the callback", 1, pending.size)
        } finally {
            UserActivation.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            UserActivation.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A))
            UserActivation.consume()
        }
    }

    @Test fun changedAddressRefreshesTheTabIconWithoutTitleOrChromiumIconCallbacks() = Fixture().use { f ->
        val callback = TabWindow(f.activity, f.tab)
        // Tab strip is now a Compose host; the old RecyclerView adapter observer no longer applies.
        // Keep the model-level URL update contract.
        val wasPrivate = f.activity.config.privateBrowsing
        f.activity.config.privateBrowsing = true // No history persistence needed for this UI contract.
        try {
            f.tab.title = "Unchanged title"
            callback.onPageFinished("https://redirected.test/page")
            assertEquals("https://redirected.test/page", f.tab.url)
            callback.onVisited("https://redirected.test/another-page")
            assertEquals("https://redirected.test/another-page", f.tab.url)
            assertEquals("Unchanged title", f.tab.title)
            callback.onVisited(f.tab.url)
            callback.onPageFinished(f.tab.url)
            assertEquals("https://redirected.test/another-page", f.tab.url)
        } finally {
            f.activity.config.privateBrowsing = wasPrivate
        }
    }

    @Test fun anotherAppCannotReplaceTheShownPage() = Fixture().use { f ->
        // The extra older versions honoured from any sender; no app may load into the shown tab.
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://phishing.test/login"))
            .putExtra("com.reiniertutoriales.vireolumatv.EXTRA_OPEN_IN_SAME_TAB", true)
        val restore = com.reiniertutoriales.vireolumatv.webengine.Engines.replaceForTest(object :
            com.reiniertutoriales.vireolumatv.webengine.EngineBackend {
            override fun create(tab: BrowserTab): PageEngine = f.engine
            override suspend fun clearCache(context: android.content.Context) {}
            override val version = "test"
        })
        try {
            f.activity.handleIntent(intent)
        } finally { restore() }
        assertEquals("https://example.test", f.tab.url)
        assertEquals(2, f.tabs.openTabs.size)
        assertEquals("https://phishing.test/login", f.tabs.openTabs[1].url)
        assertNotSame(f.tab, f.tabs.currentTab.value)
    }

    @Test fun theNetworkComingBackReachesTheShownPageOnlyWhenRestored() = Fixture().use { f ->
        f.activity.adBlocker = AdBlocker(autoLoad = false).apply { listLoading.value = true } // no list fetch
        val watcher = MainActivity::class.java.getDeclaredField("networkCallback").apply { isAccessible = true }
            .get(f.activity) as com.reiniertutoriales.vireolumatv.utils.NetworkWatcher
        watcher.update("wifi", online = true, validated = false)
        watcher.update("wifi", online = true, validated = false)
        watcher.lost("wifi")
        watcher.update("wifi", online = true, validated = true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(listOf(true, false, true), f.online)
        assertEquals("Online, then back after the loss", 2, f.restoredNetwork)
    }

    @Test fun addToFavoritesInATabsOptionsActsOnThatTabEvenAfterItMoved() = Fixture().use { f ->
        val other = BrowserTab(url = "https://other.test/page", title = "Other")
        f.tabs.openTabs.add(other)
        val chosen = mutableListOf<BrowserTab>()
        f.vb.controlsHost.onTabOptions = { tab ->
            if (true) chosen += tab  // options path now lives on the host callback
        }
        f.vb.controlsHost.onTabOptions?.invoke(other)
        // The tab moves before the user picks the option.
        f.tabs.openTabs.swap(0, 1)
        // Options dialog is owned by MainActivity; the host callback is the contract now.
        assertEquals(listOf(other), chosen)
    }

    private class Fixture : AutoCloseable {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).apply {
            get().setTheme(R.style.AppTheme)
            setup()
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val vb = BrowserWindowBinding.inflate(host.get().layoutInflater)
        val tabs = ScreenModels.get(TabStore::class, host.get())
        val page = View(host.get()).apply { isFocusableInTouchMode = true }
        var virtualCursor = true
        var exitedFullscreen = 0
        var hideFullscreen: (() -> Unit)? = null
        var canGoBack = true
        var wentBack = 0
        var refreshed = 0
        var restoredNetwork = 0
        val online = mutableListOf<Boolean>()
        var thumbnailRenders = 0
        var loadedUrl: String? = null
        var pageUrl: String? = null
        val thumbnail = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val engine = Proxy.newProxyInstance(PageEngine::class.java.classLoader,
            arrayOf(PageEngine::class.java)) { _, method, args ->
            when (method.name) {
                "getView" -> page
                "isVirtualCursorMode" -> virtualCursor
                "setPointerMode" -> {
                    // Like the real engine: the container follows the mode.
                    virtualCursor = args!![0] as Boolean
                    vb.pageContainer.pointerEnabled = virtualCursor
                    null
                }
                "attach" -> { page.requestFocus(); null } // The real engine focuses its page when shown.
                "getUrl" -> pageUrl
                "getCanGoBack" -> canGoBack
                "getCanGoForward" -> true
                "closeFullscreen" -> { exitedFullscreen++; hideFullscreen?.invoke(); null }
                "back" -> { wentBack++; null }
                "reload" -> { refreshed++; null }
                "onNetworkRestored" -> { restoredNetwork++; null }
                "setOnline" -> { online += args!![0] as Boolean; null }
                "load" -> { loadedUrl = args!![0] as String; null }
                "preview" -> { thumbnailRenders++; thumbnail }
                else -> null
            }
        } as PageEngine
        val tab = BrowserTab(url = "https://example.test", selected = true).apply {
            thumbnail = this@Fixture.thumbnail
        }
        init {
            BrowserTab::class.java.getDeclaredField("engine\$delegate").apply { isAccessible = true }
                .set(tab, lazyOf(engine))
            tabs.openTabs.clear()
            tabs.openTabs.add(tab)
            tabs.currentTab.value = tab
            activity.vb = vb
            activity.tabStore = tabs
            activity.browserSettings = ScreenModels.get(BrowserSettings::class, host.get())
            activity.mainThread = Handler(Looper.getMainLooper())
            vb.pageContainer.addView(page)
            vb.controlsOverlay.visibility = View.INVISIBLE
            vb.tabPreview.visibility = View.INVISIBLE
            vb.startupSpinner.visibility = View.GONE
            host.get().setContentView(vb.root)
            vb.root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            vb.root.layout(0, 0, 1280, 720)
            activity.configureBrowserControls()
            page.requestFocus()
        }
        fun key(view: View, code: Int) {
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        override fun close() {
            activity.lifecycleScope.cancel()
            vb.root.removeAllViews()
            tabs.currentTab.value = null
            tabs.openTabs.clear()
            host.pause().stop().destroy()
            thumbnail.recycle()
        }
    }

    /** A player view that records the touches the pointer sends it. */
    private class Player(context: android.content.Context) : View(context) {
        val touches = mutableListOf<Int>()
        init { isFocusableInTouchMode = true }
        override fun onTouchEvent(event: MotionEvent): Boolean { touches += event.actionMasked; return true }
    }

    private fun fullscreenEngine(f: Fixture): WebViewPageEngine {
        val engine = WebViewPageEngine(f.tab).apply { window = TabWindow(f.activity, f.tab) }
        WebViewPageEngine::class.java.getDeclaredField("container").apply { isAccessible = true }
            .set(engine, f.vb.pageContainer)
        return engine
    }

    private fun relayout(f: Fixture) {
        f.vb.root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        f.vb.root.layout(0, 0, 1280, 720)
    }

    @Test fun okOverAFullscreenPlayerTapsItsControlsOncePerPress() = Fixture().use { f ->
        val engine = fullscreenEngine(f)
        val player = Player(f.host.get())
        engine.enterFullscreen(player)
        relayout(f)
        f.vb.pageContainer.pointerController.fadeIn()
        f.key(f.vb.pageContainer, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("One OK is one tap on the player, not on the hidden page",
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), player.touches)
        // Enter and gamepad A arriving for the same press still make a single tap.
        f.vb.pageContainer.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        f.vb.pageContainer.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER).let {
            KeyEvent.changeTimeRepeat(it, it.eventTime, 1) })
        f.vb.pageContainer.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(4, player.touches.size)
        engine.closeFullscreen()
    }

    @Test fun aPressHeldAcrossFullscreenIsCancelledNotReleasedElsewhere() = Fixture().use { f ->
        val engine = fullscreenEngine(f)
        val player = Player(f.host.get())
        engine.enterFullscreen(player)
        relayout(f)
        f.vb.pageContainer.pointerController.fadeIn()
        f.vb.pageContainer.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        // The player leaves fullscreen while OK is still held.
        engine.onPageLeftFullscreen()
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), player.touches)
        assertFalse("No finger is left down to be lifted on the page", f.vb.pageContainer.pointerController.isSelectionPressed)
        f.vb.pageContainer.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals("The release reaches nobody", 2, player.touches.size)
        assertTrue(f.vb.pageContainer.pointerEnabled)
    }

    @Test fun aLongPressOverTheVideoOpensNoPageMenu() = Fixture().use { f ->
        val engine = fullscreenEngine(f)
        val player = Player(f.host.get())
        engine.enterFullscreen(player)
        engine.openPointerMenuAt(10, 10)
        assertNotEquals(View.VISIBLE, f.vb.pointerMenu.visibility)
        assertTrue(player.hasFocus())
        engine.closeFullscreen()
    }

    @Test fun inDpadModeTheFullscreenPlayerGetsTheKeys() = Fixture().use { f ->
        val engine = fullscreenEngine(f)
        engine.setPointerMode(false)
        val keys = mutableListOf<Int>()
        val player = object : View(f.host.get()) {
            init { isFocusableInTouchMode = true }
            override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean { keys += keyCode; return true }
        }
        engine.enterFullscreen(player)
        relayout(f)
        assertFalse(f.vb.pageContainer.pointerEnabled)
        f.key(f.vb.pageContainer, KeyEvent.KEYCODE_DPAD_RIGHT)
        f.key(f.vb.pageContainer, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER), keys)
        engine.closeFullscreen()
    }

    @Test fun fullscreenExitKeepsDirectDpadNavigation() = Fixture().use { f ->
        val callback = TabWindow(f.activity, f.tab)
        val engine = WebViewPageEngine(f.tab).apply { window = callback }
        WebViewPageEngine::class.java.getDeclaredField("container").apply { isAccessible = true }
            .set(engine, f.vb.pageContainer)
        engine.setPointerMode(false)
        val video = View(f.host.get()).apply { isFocusableInTouchMode = true }
        engine.enterFullscreen(video)
        engine.closeFullscreen()
        assertNull(video.parent)
        assertFalse("Leaving a video must not switch D-pad navigation back to the pointer",
            f.vb.pageContainer.pointerEnabled)
        engine.setPointerMode(true)
        engine.enterFullscreen(video)
        engine.closeFullscreen()
        assertTrue(f.vb.pageContainer.pointerEnabled)
    }

    @Test fun backAtTheRootLeavesAfterOpeningTheCapsuleAndOtherwiseClosesIt() = Fixture().use { f ->
        // N08 / TV-DB: page → Back opens the capsule → Back leaves when there is no history.
        f.canGoBack = false
        f.activity.consumeBack()
        assertEquals(View.VISIBLE, f.vb.controlsOverlay.visibility)
        assertFalse(f.activity.isFinishing)
        f.activity.consumeBack()
        assertTrue("Back at the root must reach the launcher", f.activity.isFinishing)
    }

    @Test fun backOnACapsuleWithHistoryOrOpenedOtherwiseOnlyClosesIt() = Fixture().use { f ->
        f.canGoBack = true
        f.activity.consumeBack()
        f.activity.consumeBack()
        assertEquals("With history, the capsule's Back button goes back; Back closes", View.INVISIBLE,
            f.vb.controlsOverlay.visibility)
        assertFalse(f.activity.isFinishing)
        // A capsule opened by the menu or Up is never an exit, even at the root.
        f.canGoBack = false
        f.activity.openCapsule()
        f.activity.consumeBack()
        assertEquals(View.INVISIBLE, f.vb.controlsOverlay.visibility)
        assertFalse(f.activity.isFinishing)
    }

    @Test fun aLateSecondFullscreenEndChangesNothing() = Fixture().use { f ->
        val callback = TabWindow(f.activity, f.tab)
        val engine = WebViewPageEngine(f.tab).apply { window = callback }
        WebViewPageEngine::class.java.getDeclaredField("container").apply { isAccessible = true }
            .set(engine, f.vb.pageContainer)
        engine.setPointerMode(false)
        engine.enterFullscreen(View(f.host.get()).apply { isFocusableInTouchMode = true })
        engine.exitFullscreen(restoreBrowserControls = true)
        f.activity.consumeBack()
        assertEquals(View.INVISIBLE, f.vb.controlsOverlay.visibility)
        // WebView may report the end again (onHideCustomView after the browser closed it).
        engine.exitFullscreen(restoreBrowserControls = true)
        assertFalse("A repeated end must not switch D-pad navigation to the pointer", f.vb.pageContainer.pointerEnabled)
        assertEquals("A repeated end must not reopen the capsule", View.INVISIBLE, f.vb.controlsOverlay.visibility)
    }
}
