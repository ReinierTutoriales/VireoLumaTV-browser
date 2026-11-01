package com.reiniertutoriales.vireolumatv.webengine.webview

import android.webkit.WebViewClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkFailureTest {
    private val failure = NetworkFailure()
    private val page = "https://news.test/today"

    @Test fun aPageThatFailedForLackOfNetworkLoadsAgainOnce() {
        failure.requested()
        failure.failed(page, WebViewClient.ERROR_HOST_LOOKUP)
        assertTrue(failure.takeReload(page))
        assertFalse("One reload per failure", failure.takeReload(page))
    }

    @Test fun everyNetworkErrorCountsAndOtherErrorsDoNot() {
        for (code in listOf(WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT,
                WebViewClient.ERROR_TIMEOUT, WebViewClient.ERROR_IO)) {
            failure.failed(page, code)
            assertTrue("code $code", failure.takeReload(page))
        }
        for (code in listOf(WebViewClient.ERROR_UNKNOWN, WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
                WebViewClient.ERROR_UNSAFE_RESOURCE, WebViewClient.ERROR_BAD_URL, WebViewClient.ERROR_AUTHENTICATION)) {
            failure.failed(page, code)
            assertFalse("code $code", failure.takeReload(page))
        }
    }

    @Test fun aNewLoadOrAnotherPageOnScreenForgetsTheFailure() {
        failure.failed(page, WebViewClient.ERROR_CONNECT)
        failure.requested()
        assertFalse(failure.takeReload(page))
        failure.failed(page, WebViewClient.ERROR_CONNECT)
        assertFalse("The user went back to another page", failure.takeReload("https://news.test/"))
        assertFalse(failure.takeReload(page))
    }
}
