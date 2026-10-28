package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Application
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.singleton.Favicons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(application = Application::class, sdk = [28])
class HomePageNetworkTest {
    @Test fun missingHomepageIconDoesNotStartLookupOnInterceptionThread() {
        val app = RuntimeEnvironment.getApplication()
        AppContext.init(app, Config(app.getSharedPreferences("home-network-test", 0)).apply { privateBrowsing = false })
        Favicons.clear()
        val previous = Favicons.store
        Favicons.store = object : Favicons.Store {
            override fun find(host: String): SiteSettings? =
                throw AssertionError("The interceptor must never start a favicon lookup")
        }
        val view = WebView(app)
        val request = Proxy.newProxyInstance(WebResourceRequest::class.java.classLoader,
            arrayOf(WebResourceRequest::class.java)) { _, method, _ ->
            if (method.name == "getUrl") Uri.parse("favicon://missing.example") else null
        } as WebResourceRequest
        try { assertEquals(404, StartPageAssets.shouldInterceptRequest(request)!!.statusCode) }
        finally { view.destroy(); Favicons.store = previous; Favicons.clear() }
    }

    @Test fun regularPageRequestsAreNotIntercepted() {
        assertNull(StartPageAssets.shouldInterceptRequest(requestFor("https://example.test/index.html")))
    }

    @Test fun faviconRequestWithoutHostIsNotIntercepted() {
        assertNull(StartPageAssets.shouldInterceptRequest(requestFor("favicon:")))
    }

    private fun requestFor(url: String) = Proxy.newProxyInstance(WebResourceRequest::class.java.classLoader,
        arrayOf(WebResourceRequest::class.java)) { _, method, _ ->
        if (method.name == "getUrl") Uri.parse(url) else null
    } as WebResourceRequest
}
