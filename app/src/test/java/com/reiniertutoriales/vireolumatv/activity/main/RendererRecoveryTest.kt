package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import android.os.Bundle
import com.reiniertutoriales.vireolumatv.model.WebTabState
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
        val engine = Proxy.newProxyInstance(WebEngine::class.java.classLoader,
            arrayOf(WebEngine::class.java)) { _, method, _ ->
            if (method.name == "restoreState") restores++
            null
        } as WebEngine
        val callback = Proxy.newProxyInstance(WebEngineProviderCallback::class.java.classLoader,
            arrayOf(WebEngineProviderCallback::class.java)) { _, method, _ ->
            if (method.name == "createWebEngine") engine else null
        } as WebEngineProviderCallback
        val field = WebEngineFactory::class.java.getDeclaredField("initializedProvider").apply { isAccessible = true }
        val previous = field.get(null)
        field.set(null, WebEngineProvider("test", callback))
        try {
            val tab = WebTabState(url = "https://latest.test").apply { savedState = Bundle() }
            assertTrue(tab.restoreWebView())
            assertEquals(1, restores)
            tab.rendererLost = true
            assertFalse(tab.restoreWebView())
            assertEquals(1, restores)
            assertEquals("https://latest.test", tab.url)
            tab.rendererLost = false
            assertTrue(tab.restoreWebView())
            assertEquals(2, restores)
        } finally { field.set(null, previous) }
    }
}
