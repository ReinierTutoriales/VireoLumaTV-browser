package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import android.net.Uri
import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ExternalWebNavigationTest {
    @Test fun processSwitchPreservesAValidLinkButDoesNotForwardExecutableData() {
        val source = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/page"))
        val target = Intent()
        ExternalWebNavigation.copyAllowedData(source, target)
        assertEquals(source.data, target.data)
        val rejected = Intent()
        ExternalWebNavigation.copyAllowedData(Intent(Intent.ACTION_VIEW, Uri.parse("javascript:alert(1)")), rejected)
        assertEquals(null, rejected.data)
    }

    @Test fun acceptsWebLinksIncludingMixedCaseAndInternationalHosts() {
        listOf("https://example.com/path?q=1", "HTTP://example.com", "https://例子.测试", "http://[::1]/").forEach {
            assertTrue(it, ExternalWebNavigation.isAllowed(Uri.parse(it)))
        }
    }

    @Test fun rejectsLocalExecutableInternalAndMalformedInputs() {
        assertFalse(ExternalWebNavigation.isAllowed(null))
        listOf("", "example.com", "//example.com", "https:", "http:example.com", "https:///", "https://", "javascript:alert(1)", "file:///android_asset/", "content://provider/file", "intent://example.com", "data:text/html,hello", "about:home", "internal://warning").forEach {
            assertFalse(it, ExternalWebNavigation.isAllowed(Uri.parse(it)))
        }
    }
}
