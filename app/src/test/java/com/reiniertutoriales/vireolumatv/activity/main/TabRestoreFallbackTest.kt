package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.webkit.WebView
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.webengine.PageEngine
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewPageEngine
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy
import com.reiniertutoriales.vireolumatv.utils.BundleBytes

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class TabRestoreFallbackTest {
    private fun tabWithEngine(engine: PageEngine, privateMode: Boolean = false) =
        BrowserTab(url = "https://saved.test/page", incognito = privateMode).apply {
            BrowserTab::class.java.getDeclaredField("engine\$delegate")
                .apply { isAccessible = true }.set(this, lazyOf(engine))
        }

    @Test fun rejectedMemoryStateLoadsSavedUrlOnceInBothModes() {
        for (privateMode in listOf(false, true)) {
            val app = RuntimeEnvironment.getApplication()
            var liveView: View? = null
            val loads = mutableListOf<String>()
            var restores = 0
            val engine = Proxy.newProxyInstance(PageEngine::class.java.classLoader,
                arrayOf(PageEngine::class.java)) { _, method, args ->
                when (method.name) {
                    "getView" -> liveView
                    "restoreHistory" -> { restores++; false }
                    "load" -> { loads.add(args!![0] as String); null }
                    else -> null
                }
            } as PageEngine
            val tab = tabWithEngine(engine, privateMode).apply { savedState = Bundle() }
            val model = TabStore()
            model.modelScope.cancel() // Persistence is covered by separate Room/atomic-file tests.
            model.tabsStates.add(tab)
            val window = Proxy.newProxyInstance(PageWindow::class.java.classLoader,
                arrayOf(PageWindow::class.java)) { _, _, _ -> null } as PageWindow
            try {
                model.changeTab(tab, { View(app).also { liveView = it } }, FrameLayout(app), window)
                assertEquals(1, restores)
                assertEquals(listOf("https://saved.test/page"), loads)
                assertEquals("https://saved.test/page", tab.url)
                model.changeTab(tab, { error("Already live") }, FrameLayout(app), window)
                assertEquals(1, restores)
                assertEquals(1, loads.size)
            } finally { model.clear() }
        }
    }

    @Test fun rejectedDiskStateDoesNotReportSuccessfulRestoration() {
        val app = RuntimeEnvironment.getApplication()
        val engine = Proxy.newProxyInstance(PageEngine::class.java.classLoader,
            arrayOf(PageEngine::class.java)) { _, method, args ->
            when (method.name) {
                "readHistory" -> BundleBytes.decode(args!![0] as ByteArray)
                "restoreHistory" -> false
                else -> null
            }
        } as PageEngine
        val tab = tabWithEngine(engine).apply { wvStateFileName = "tab-rejected-state-test" }
        val file = File(app.filesDir, "${BrowserTab.TAB_WVSTATES_DIR}/${tab.wvStateFileName}")
        file.parentFile!!.mkdirs()
        file.writeBytes(requireNotNull(BundleBytes.encode(Bundle())))
        try {
            assertFalse(tab.restoreWebView())
            assertEquals("https://saved.test/page", tab.url)
            assertTrue("Restoration failure must not delete persisted user data", file.exists())
        } finally { file.delete() }
    }

    @Test fun invalidMemoryStateFallsBackInsteadOfThrowing() {
        val engine = WebViewPageEngine(BrowserTab())
        val tab = tabWithEngine(engine).apply { savedState = "invalid legacy payload" }
        assertFalse(tab.restoreWebView())
        assertEquals("https://saved.test/page", tab.url)
    }

    @Test fun realEngineRestoresValidHistoryAndRejectsEmptyBundle() {
        val app = RuntimeEnvironment.getApplication()
        val engine = WebViewPageEngine(BrowserTab())
        val tab = tabWithEngine(engine)
        try {
            val live = engine.createView(app) as WebView
            engine.load("https://saved.test/first")
            shadowOf(live).pushEntryToHistory("https://saved.test/first")
            engine.load("https://saved.test/second")
            shadowOf(live).pushEntryToHistory("https://saved.test/second")
            tab.savedState = requireNotNull(engine.saveHistory())
            engine.detach(destroyView = true)
            engine.createView(app)
            assertTrue(tab.restoreWebView())
            assertEquals("https://saved.test/second", engine.url)
            assertTrue(engine.canGoBack)
            tab.savedState = Bundle()
            assertFalse(tab.restoreWebView())
            assertNull(tab.savedState)
        } finally { engine.detach(destroyView = true) }
    }

    @Test fun engineWithoutLiveViewDoesNotManufactureSavedState() {
        assertNull(WebViewPageEngine(BrowserTab()).saveHistory())
    }
}
