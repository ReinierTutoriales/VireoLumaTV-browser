package com.reiniertutoriales.vireolumatv.activity.main

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.webengine.*
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class SingleLiveTabTest {
    @Test fun switchingDestroysBeforeAllocationAndRestoresHistoryInBothModes() {
        for (privateMode in listOf(false, true)) verify(privateMode)
    }

    private fun verify(privateMode: Boolean) {
        val app = RuntimeEnvironment.getApplication()
        val views = IdentityHashMap<WebTabState, View>()
        val engines = IdentityHashMap<WebTabState, WebEngine>()
        val histories = IdentityHashMap<WebTabState, String>()
        val events = mutableListOf<String>()
        var loads = 0
        val providerCallback = Proxy.newProxyInstance(WebEngineProviderCallback::class.java.classLoader,
            arrayOf(WebEngineProviderCallback::class.java)) { _, method, args ->
            if (method.name != "createWebEngine") null else {
                val tab = args!![0] as WebTabState
                engines.getOrPut(tab) {
                    Proxy.newProxyInstance(WebEngine::class.java.classLoader,
                        arrayOf(WebEngine::class.java)) { _, call, values ->
                        when (call.name) {
                            "getView" -> views[tab]
                            "saveState" -> Bundle().apply {
                                putString("history", histories[tab])
                                events.add("save:${tab.url}")
                            }
                            "restoreState" -> { histories[tab] = (values!![0] as Bundle).getString("history")!!; null }
                            "onDetachFromWindow" -> {
                                if (values!![0] == true) {
                                    views.remove(tab)
                                    events.add("destroy:${tab.url}")
                                }
                                null
                            }
                            "loadUrl" -> { loads++; null }
                            else -> null
                        }
                    } as WebEngine
                }
            }
        } as WebEngineProviderCallback
        val field = WebEngineFactory::class.java.getDeclaredField("initializedProvider").apply { isAccessible = true }
        val previous = field.get(null)
        field.set(null, WebEngineProvider("test", providerCallback))
        val model = TabsModel()
        // Persistence has separate Room/atomic-file tests; keep this test focused on renderer lifetime.
        model.modelScope.cancel()
        try {
            val first = WebTabState(url = "https://one.test", incognito = privateMode)
            val second = WebTabState(url = "https://two.test", incognito = privateMode)
            model.tabsStates.add(first)
            model.tabsStates.add(second)
            val window = Proxy.newProxyInstance(WebEngineWindowProviderCallback::class.java.classLoader,
                arrayOf(WebEngineWindowProviderCallback::class.java)) { _, _, _ -> null } as WebEngineWindowProviderCallback
            val parent = FrameLayout(app)
            val allocate: (WebTabState) -> View? = { tab ->
                assertTrue("Old renderer must be released before allocating another", views.isEmpty())
                events.add("create:${tab.url}")
                View(app).also { views[tab] = it }
            }
            model.changeTab(first, allocate, parent, window)
            histories[first] = "first -> second -> third"
            model.changeTab(second, allocate, parent, window)
            assertEquals(1, views.size)
            assertNotNull(first.savedState)
            assertTrue(events.indexOf("save:${first.url}") < events.indexOf("destroy:${first.url}"))
            assertTrue(events.indexOf("destroy:${first.url}") < events.indexOf("create:${second.url}"))
            histories[second] = "other history"
            histories.remove(first)
            model.changeTab(first, allocate, parent, window)
            assertEquals("first -> second -> third", histories[first])
            assertEquals(1, views.size)
            assertEquals(2, loads) // Restored tabs do not reload their URL and lose history.
            val count = events.size
            model.changeTab(first, allocate, parent, window)
            assertEquals(count, events.size)
            val transportTab = WebTabState(url = "https://transport.test", incognito = privateMode)
            model.tabsStates.add(transportTab)
            model.changeTab(transportTab, allocate, parent, window, loadInitialUrl = false)
            assertEquals(2, loads) // Caller/Chromium owns initial navigation, with no duplicate request.
            assertEquals(1, views.size)
        } finally {
            model.clear()
            field.set(null, previous)
        }
    }
}
