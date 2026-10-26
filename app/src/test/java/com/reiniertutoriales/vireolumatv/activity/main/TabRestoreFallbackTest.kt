package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.webkit.WebView
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.Utils
import com.reiniertutoriales.vireolumatv.webengine.WebEngine
import com.reiniertutoriales.vireolumatv.webengine.WebEngineWindowProviderCallback
import com.reiniertutoriales.vireolumatv.webengine.webview.WebViewWebEngine
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

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class TabRestoreFallbackTest {
    private fun tabWithEngine(engine: WebEngine, privateMode: Boolean = false) =
        WebTabState(url = "https://saved.test/page", incognito = privateMode).apply {
            WebTabState::class.java.getDeclaredField("webEngine\$delegate")
                .apply { isAccessible = true }.set(this, lazyOf(engine))
        }

    @Test fun rejectedMemoryStateLoadsSavedUrlOnceInBothModes() {
        for (privateMode in listOf(false, true)) {
            val app = RuntimeEnvironment.getApplication()
            var liveView: View? = null
            val loads = mutableListOf<String>()
            var restores = 0
            val engine = Proxy.newProxyInstance(WebEngine::class.java.classLoader,
                arrayOf(WebEngine::class.java)) { _, method, args ->
                when (method.name) {
                    "getView" -> liveView
                    "restoreState" -> { restores++; false }
                    "loadUrl" -> { loads.add(args!![0] as String); null }
                    else -> null
                }
            } as WebEngine
            val tab = tabWithEngine(engine, privateMode).apply { savedState = Bundle() }
            val model = TabsModel()
            model.modelScope.cancel() // Persistence is covered by separate Room/atomic-file tests.
            model.tabsStates.add(tab)
            val window = Proxy.newProxyInstance(WebEngineWindowProviderCallback::class.java.classLoader,
                arrayOf(WebEngineWindowProviderCallback::class.java)) { _, _, _ -> null } as WebEngineWindowProviderCallback
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
        val engine = Proxy.newProxyInstance(WebEngine::class.java.classLoader,
            arrayOf(WebEngine::class.java)) { _, method, args ->
            when (method.name) {
                "stateFromBytes" -> Utils.bytesToBundle(args!![0] as ByteArray)
                "restoreState" -> false
                else -> null
            }
        } as WebEngine
        val tab = tabWithEngine(engine).apply { wvStateFileName = "tab-rejected-state-test" }
        val file = File(app.filesDir, "${WebTabState.TAB_WVSTATES_DIR}/${tab.wvStateFileName}")
        file.parentFile!!.mkdirs()
        file.writeBytes(requireNotNull(Utils.bundleToBytes(Bundle())))
        try {
            assertFalse(tab.restoreWebView())
            assertEquals("https://saved.test/page", tab.url)
            assertTrue("Restoration failure must not delete persisted user data", file.exists())
        } finally { file.delete() }
    }

    @Test fun invalidMemoryStateFallsBackInsteadOfThrowing() {
        val engine = WebViewWebEngine(WebTabState())
        val tab = tabWithEngine(engine).apply { savedState = "invalid legacy payload" }
        assertFalse(tab.restoreWebView())
        assertEquals("https://saved.test/page", tab.url)
    }

    @Test fun realEngineRestoresValidHistoryAndRejectsEmptyBundle() {
        val app = RuntimeEnvironment.getApplication()
        val engine = WebViewWebEngine(WebTabState())
        val tab = tabWithEngine(engine)
        try {
            val live = engine.getOrCreateView(app) as WebView
            engine.loadUrl("https://saved.test/first")
            shadowOf(live).pushEntryToHistory("https://saved.test/first")
            engine.loadUrl("https://saved.test/second")
            shadowOf(live).pushEntryToHistory("https://saved.test/second")
            tab.savedState = requireNotNull(engine.saveState())
            engine.onDetachFromWindow(completely = true, destroyTab = false)
            engine.getOrCreateView(app)
            assertTrue(tab.restoreWebView())
            assertEquals("https://saved.test/second", engine.url)
            assertTrue(engine.canGoBack())
            tab.savedState = Bundle()
            assertFalse(tab.restoreWebView())
            assertNull(tab.savedState)
        } finally { engine.onDetachFromWindow(completely = true, destroyTab = true) }
    }

    @Test fun engineWithoutLiveViewDoesNotManufactureSavedState() {
        assertNull(WebViewWebEngine(WebTabState()).saveState())
    }
}
