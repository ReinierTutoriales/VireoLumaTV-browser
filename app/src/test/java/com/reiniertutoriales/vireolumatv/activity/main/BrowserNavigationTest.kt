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

    private class Fixture : AutoCloseable {
        val host = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
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
        var wentBack = 0
        var refreshed = 0
        var thumbnailRenders = 0
        var loadedUrl: String? = null
        val thumbnail = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val engine = Proxy.newProxyInstance(PageEngine::class.java.classLoader,
            arrayOf(PageEngine::class.java)) { _, method, args ->
            when (method.name) {
                "getView" -> page
                "isVirtualCursorMode" -> virtualCursor
                "setPointerMode" -> { virtualCursor = args!![0] as Boolean; null }
                "getCanGoBack", "getCanGoForward" -> true
                "closeFullscreen" -> { exitedFullscreen++; hideFullscreen?.invoke(); null }
                "back" -> { wentBack++; null }
                "reload" -> { refreshed++; null }
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

    @Test fun backOpensCapsuleAndDownReachesTabsAndTabsDownReturnsToPage() = Fixture().use { f ->
        val bar = f.vb.controlsRow
        f.activity.consumeBack()
        assertEquals(View.VISIBLE, f.vb.controlsOverlay.visibility)
        assertTrue(bar.backButton.hasFocus())
        bar.refreshButton.requestFocus()
        assertEquals("Focusing a command must not hide the capsule", View.VISIBLE, f.vb.controlsOverlay.visibility)
        assertSame("Down from a command must reach the tab strip", f.vb.tabStrip, bar.refreshButton.focusSearch(View.FOCUS_DOWN))
        assertSame("Up from the tab strip must return to the address field",
            bar.findViewById<View>(R.id.address_field), f.vb.tabStrip.focusSearch(View.FOCUS_UP))
        assertTrue("Tab strip must accept focus", f.vb.tabStrip.requestFocus())
        assertTrue("Focus must be inside the tab strip before Down", f.vb.tabStrip.hasFocus() || f.vb.tabStrip.findFocus() != null)
        assertTrue("Down from the tab strip must leave it for the page", downLeavesTabStrip(
            overlayVisible = true, tabsFocused = f.vb.tabStrip.hasFocus(),
            keyCode = KeyEvent.KEYCODE_DPAD_DOWN, action = KeyEvent.ACTION_DOWN, repeatCount = 0))
        f.activity.closeCapsule()
        assertEquals(View.INVISIBLE, f.vb.controlsOverlay.visibility)
        assertTrue(f.page.hasFocus())
    }

    @Test fun controlsRefreshBackAfterHistoryChangedWithoutPageLoad() = Fixture().use { f ->
        // pushState (YouTube and other SPAs) changes history without onPageStarted/onPageFinished.
        f.vb.controlsRow.backButton.isEnabled = false
        f.vb.controlsRow.forwardButton.isEnabled = false
        f.activity.consumeBack()
        assertTrue(f.vb.controlsRow.backButton.isEnabled)
        assertTrue(f.vb.controlsRow.forwardButton.isEnabled)
        assertTrue("Back must be reachable as the first bottom-bar command", f.vb.controlsRow.backButton.hasFocus())
    }

    @Test fun tabsLeadToSearchAndExitingAddressDoesNotFocusCloseApp() = Fixture().use { f ->
        val bar = f.vb.controlsRow
        val address = bar.findViewById<EditText>(R.id.address_field)
        val menu = bar.findViewById<View>(R.id.menu_button)
        assertEquals(R.id.address_box, bar.getChildAt(0).id)
        assertEquals(R.id.menu_button, bar.getChildAt(bar.childCount - 1).id)
        f.activity.consumeBack()
        val tab = f.vb.tabStrip.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.tab_list)
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
        assertFalse("Focusing Search must leave the capsule available", bar.isEditingAddress)
        f.key(address, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue("Right from Search must reach the first capsule command", bar.backButton.hasFocus())
        assertFalse(menu.hasFocus())
        assertFalse(bar.isEditingAddress)

        val add = f.vb.tabStrip.findViewById<View>(R.id.add_button)
        assertTrue(add.requestFocus())
        val addUpTarget = add.focusSearch(View.FOCUS_UP)
        assertSame("Up from Add tab must target Search", address, addUpTarget)
        assertTrue(addUpTarget.requestFocus())
        assertTrue("Up from Add tab must focus Search", address.hasFocus())
        assertFalse(bar.isEditingAddress)
        f.key(address, KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(bar.isEditingAddress)
        f.activity.consumeBack()
        assertFalse(bar.isEditingAddress)
        assertFalse(menu.hasFocus())
        assertTrue(bar.backButton.hasFocus())
    }

    @Test fun activePageStaysSharpAndFirstTouchReachesTheRealPage() = Fixture().use { f ->
        var clicks = 0
        f.page.setOnClickListener { clicks++ }
        f.activity.openCapsule()
        assertEquals(View.VISIBLE, f.vb.pageContainer.visibility)
        assertEquals(View.INVISIBLE, f.vb.tabPreview.visibility)
        assertTrue(f.vb.pageContainer.cursorSuppressed)
        assertEquals("Opening controls must not allocate an active-page screenshot", 0, f.thumbnailRenders)
        assertEquals(ImageView.ScaleType.CENTER_INSIDE, f.vb.tabPreview.scaleType)
        // A page click also exits native address editing without leaving its keyboard/draft behind.
        f.vb.controlsRow.callback = f.activity
        f.vb.controlsRow.showAddress(f.tab.url)
        val address = f.vb.controlsRow.findViewById<EditText>(R.id.address_field)
        address.requestFocus()
        address.performClick()
        address.setText("unfinished address draft")
        assertTrue(f.vb.controlsRow.isEditingAddress)
        val now = android.os.SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now, action, 640f, 360f, 0)
            f.vb.pageContainer.dispatchTouchEvent(event)
            event.recycle()
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("The click that resumes the page must reach its search field", 1, clicks)
        assertEquals(View.INVISIBLE, f.vb.controlsOverlay.visibility)
        assertFalse(f.vb.pageContainer.cursorSuppressed)
        assertNull(f.vb.pageContainer.onPageTouch)
        assertFalse(f.vb.controlsRow.isEditingAddress)
        assertEquals(f.tab.url, address.text.toString())
        assertTrue(f.page.hasFocus())
    }

    @Test fun pageCommandsRunOnceAndRestorePageFocus() = Fixture().use { f ->
        f.activity.consumeBack()
        f.vb.controlsRow.backButton.performClick()
        assertEquals(1, f.wentBack)
        assertTrue(f.page.hasFocus())
        f.activity.consumeBack()
        f.vb.controlsRow.refreshButton.performClick()
        assertEquals(1, f.refreshed)
        assertTrue(f.page.hasFocus())
    }

    @Test fun blockerShieldTogglesTheSelectedTabAndReportsPause() = Fixture().use { f ->
        f.activity.adBlocker = AdBlocker(autoLoad = false)
        f.tab.adblock = true
        f.vb.tabStrip.current = 0
        f.activity.consumeBack()
        val shield = f.vb.controlsRow.adBlockButton
        val unavailable = f.host.get().getString(R.string.blocker_state_none)
        assertEquals(f.host.get().getString(R.string.blocker_summary, unavailable, 0),
            shield.contentDescription)
        // The shield is one focus stop: Right reaches the menu, Down reaches the tab strip.
        assertSame(f.vb.controlsRow.findViewById<View>(R.id.menu_button), shield.focusSearch(View.FOCUS_RIGHT))
        assertSame(f.vb.tabStrip, shield.focusSearch(View.FOCUS_DOWN))
        assertTrue(shield.requestFocus())
        shield.performClick()
        assertEquals(false, f.tab.adblock)
        assertEquals(f.host.get().getString(R.string.blocker_summary,
            f.host.get().getString(R.string.blocker_state_paused), 0), shield.contentDescription)
        assertEquals(1, f.refreshed)
        assertTrue("Long press must open the blocker details", shield.performLongClick())
        val details = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        // A dialog message would keep the list out of the layout.
        assertNotNull("The blocker actions must be in the dialog", details.listView.parent)
        assertEquals("Both blocker actions must stay reachable in the details", 2, details.listView.adapter.count)
    }

    @Test fun fullscreenExitRestoresOneCoherentMenuAndNextBackReturnsToPage() = Fixture().use { f ->
        // Exercise the real engine's fullscreen teardown and the real Activity callback together.
        val callback = TabWindow(f.activity, f.tab)
        val engine = WebViewPageEngine(f.tab).apply { window = callback }
        WebViewPageEngine::class.java.getDeclaredField("container").apply { isAccessible = true }
            .set(engine, f.vb.pageContainer)
        val video = View(f.host.get()).apply { isFocusableInTouchMode = true }
        engine.enterFullscreen(video)
        assertFalse(f.vb.pageContainer.pointerEnabled)
        assertTrue(video.hasFocus())
        f.hideFullscreen = { engine.closeFullscreen() }
        f.virtualCursor = false
        f.activity.consumeBack()
        assertEquals(1, f.exitedFullscreen)
        assertNull(video.parent)
        assertTrue(f.vb.pageContainer.pointerEnabled)
        assertEquals(View.VISIBLE, f.vb.controlsOverlay.visibility)
        assertTrue(f.vb.controlsRow.backButton.hasFocus())
        f.activity.consumeBack()
        assertEquals("Back must close the existing menu, not reopen bars in an unknown state",
            View.INVISIBLE, f.vb.controlsOverlay.visibility)
        assertTrue(f.page.hasFocus())
    }

    @Test fun cancelSearchRestoresButtonsAndKeyboardSearchLoadsConfiguredEngine() = Fixture().use { f ->
        f.activity.consumeBack()
        val address = f.vb.controlsRow.findViewById<EditText>(R.id.address_field)
        address.requestFocus()
        address.performClick()
        assertTrue(f.vb.controlsRow.isEditingAddress)
        f.activity.consumeBack()
        assertFalse(f.vb.controlsRow.isEditingAddress)
        assertEquals(View.VISIBLE, f.vb.controlsRow.findViewById<View>(R.id.menu_button).visibility)
        address.requestFocus()
        address.performClick()
        address.setText("android tv navegador")
        address.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        assertNotNull("TV keyboard Search must load a result", f.loadedUrl)
        assertTrue(f.loadedUrl!!.contains("android+tv+navegador"))
        assertEquals(View.INVISIBLE, f.vb.controlsOverlay.visibility)
        assertTrue(f.page.hasFocus())
    }

    @Test fun pageUpdatesCannotOverwriteTypingAndPhysicalEnterSubmitsOnce() = Fixture().use { f ->
        f.activity.consumeBack()
        val address = f.vb.controlsRow.findViewById<EditText>(R.id.address_field)
        f.vb.controlsRow.showAddress("https://before.test")
        address.requestFocus()
        address.performClick()
        address.setText("my search")
        f.vb.controlsRow.showAddress("https://redirected.test")
        assertEquals("my search", address.text.toString())
        f.activity.consumeBack()
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
            f.activity.consumeBack()
            f.activity.consumeBack()
        }
        assertTrue(f.page.hasFocus())
        f.activity.consumeBack()
        assertEquals(View.VISIBLE, f.vb.controlsOverlay.visibility)
        assertTrue(f.vb.controlsRow.backButton.hasFocus())
        f.vb.controlsOverlay.animate().cancel()
        f.activity.consumeBack()
        assertEquals(View.INVISIBLE, f.vb.controlsOverlay.visibility)
        assertTrue(f.page.hasFocus())
    }
}
