package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Application
import android.webkit.CookieManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CookieJarTest {
    /** Answers removeAllCookies later, as WebView does, and records the order of the calls. */
    private class Deferred(private val real: CookieManager) : CookieManager() {
        val calls = mutableListOf<String>()
        var pending: android.webkit.ValueCallback<Boolean>? = null
        override fun removeAllCookies(callback: android.webkit.ValueCallback<Boolean>?) {
            calls += "remove"; real.removeAllCookies(null); pending = callback
        }
        override fun flush() { calls += "flush" }
        override fun setAcceptCookie(accept: Boolean) = real.setAcceptCookie(accept)
        override fun acceptCookie() = real.acceptCookie()
        override fun setAcceptThirdPartyCookies(webview: android.webkit.WebView?, accept: Boolean) = Unit
        override fun acceptThirdPartyCookies(webview: android.webkit.WebView?) = false
        override fun setCookie(url: String?, value: String?) = real.setCookie(url, value)
        override fun setCookie(url: String?, value: String?, callback: android.webkit.ValueCallback<Boolean>?) = real.setCookie(url, value, callback)
        override fun getCookie(url: String?): String? = real.getCookie(url)
        @Deprecated("") override fun removeSessionCookie() = Unit
        override fun removeSessionCookies(callback: android.webkit.ValueCallback<Boolean>?) = Unit
        @Deprecated("") override fun removeAllCookie() = Unit
        override fun hasCookies() = real.hasCookies()
        @Deprecated("") override fun removeExpiredCookie() = Unit
    }

    @Test fun theStoreIsFlushedOnlyAfterTheRemovalIsConfirmed() = runBlocking {
        val real = CookieManager.getInstance()
        real.setCookie("https://site.test", "session=1")
        val cookies = Deferred(real)
        val clearing = launch(kotlinx.coroutines.Dispatchers.Unconfined) { CookieJar.clear(cookies) }
        assertEquals("Nothing is flushed while the removal is pending", listOf("remove"), cookies.calls)
        cookies.pending!!.onReceiveValue(true)
        clearing.join()
        assertEquals(listOf("remove", "flush"), cookies.calls)
        assertNull(real.getCookie("https://site.test"))
    }
}
