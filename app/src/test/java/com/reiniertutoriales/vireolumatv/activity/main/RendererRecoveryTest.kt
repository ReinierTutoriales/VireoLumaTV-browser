package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import android.os.Bundle
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.webengine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class RendererRecoveryTest {
    @Test fun ordinaryRecreationRestoresHistoryButRendererLossSkipsStaleState() {
        var restores = 0
        val engine = Proxy.newProxyInstance(PageEngine::class.java.classLoader,
            arrayOf(PageEngine::class.java)) { _, method, _ ->
            if (method.name == "restoreHistory") { restores++; true } else null
        } as PageEngine
        val callback = Proxy.newProxyInstance(EngineBackend::class.java.classLoader,
            arrayOf(EngineBackend::class.java)) { _, method, _ ->
            if (method.name == "create") engine else null
        } as EngineBackend
        val restore = Engines.replaceForTest(callback)
        try {
            val tab = BrowserTab(url = "https://latest.test").apply { savedState = Bundle() }
            assertTrue(tab.restoreWebView())
            assertEquals(1, restores)
            tab.rendererLost = true
            assertFalse(tab.restoreWebView())
            assertEquals(1, restores)
            assertEquals("https://latest.test", tab.url)
            tab.rendererLost = false
            assertTrue(tab.restoreWebView())
            assertEquals(2, restores)
        } finally { restore() }
    }
}
