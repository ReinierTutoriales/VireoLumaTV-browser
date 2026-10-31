package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LegacyLocationGrantsTest {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("legacy-location-test", Context.MODE_PRIVATE)
        .apply { edit().clear().commit() }

    @Test fun storedGrantsAreClearedOncePerProfile() {
        var clears = 0
        repeat(3) { LegacyLocationGrants.clearOnce(prefs, "app") { clears++ } }
        assertEquals(1, clears)
        LegacyLocationGrants.clearOnce(prefs, "app:incognito") { clears++ }
        assertEquals("Each WebView profile has its own store", 2, clears)
    }

    @Test fun anInterruptedClearIsRepeated() {
        var clears = 0
        assertTrue(runCatching { LegacyLocationGrants.clearOnce(prefs, "app") { clears++; error("killed") } }.isFailure)
        LegacyLocationGrants.clearOnce(prefs, "app") { clears++ }
        LegacyLocationGrants.clearOnce(prefs, "app") { clears++ }
        assertEquals(2, clears)
    }
}
