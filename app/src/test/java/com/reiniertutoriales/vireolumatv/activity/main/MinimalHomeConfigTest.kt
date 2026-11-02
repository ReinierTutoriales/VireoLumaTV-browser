package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import com.reiniertutoriales.vireolumatv.Config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
@RoboConfig(application = Application::class, sdk = [28])
class MinimalHomeConfigTest {
    @Test fun existingInstallSwitchesSearchOnceWithoutResettingOtherSettings() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("minimal-home", 0)
        prefs.edit().clear()
            .putString(Config.SEARCH_URL_KEY, Config.SEARCH_ENGINES[0].url)
            .putBoolean(Config.BLOCKER_ENABLED_KEY, true)
            .putString(Config.BLOCK_LIST_URL_KEY, "https://list.example/filter.txt")
            .putString(Config.HOME_PAGE_KEY, "https://custom.example/")
            .putInt(Config.POINTER_SPEED_KEY, 75).commit()
        val config = Config(prefs)
        assertEquals(Config.DUCKDUCKGO_SEARCH_URL, config.searchAddress.value)
        assertTrue(config.adBlockEnabled)
        assertEquals("https://list.example/filter.txt", config.blockListUrl.value)
        assertEquals("https://custom.example/", config.homePage)
        assertEquals(75, config.pointerSpeedPercent)
        // A later explicit settings change must survive reopening the app.
        config.searchAddress.value = "https://custom.example/?q=[query]"
        assertEquals("https://custom.example/?q=[query]", Config(prefs).searchAddress.value)
    }
}
