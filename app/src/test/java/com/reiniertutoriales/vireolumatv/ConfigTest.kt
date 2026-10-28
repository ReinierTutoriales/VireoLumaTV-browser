package com.reiniertutoriales.vireolumatv

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
@RoboConfig(application = Application::class, sdk = [28])
class ConfigTest {
    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("config-test", 0)
        .also { it.edit().clear().commit() }

    @Test fun legacySearchAsHomeSwitchIsMigratedAndRemembered() {
        val prefs = prefs()
        prefs.edit().putBoolean("search_engine_as_home_page", true).commit()
        assertEquals(Config.HomePageMode.SEARCH_ENGINE, Config(prefs).homePageMode)
        assertFalse(prefs.contains("search_engine_as_home_page"))
        assertEquals("The migrated mode must survive the next read",
            Config.HomePageMode.SEARCH_ENGINE, Config(prefs).homePageMode)
    }

    @Test fun storedValuesOutsideTheirRangeFallBackSafely() {
        val prefs = prefs()
        prefs.edit().putInt("home_page_mode", 99)
            .putInt(Config.CURSOR_MAX_SPEED_PERCENT_KEY, 1000).commit()
        val config = Config(prefs)
        assertEquals(Config.HomePageMode.HOME_PAGE, config.homePageMode)
        assertEquals(Config.CURSOR_PHYSICS_PERCENT_MAX, config.cursorMaxSpeedPercent)
        config.cursorAccelerationPercent = 1
        assertEquals(Config.CURSOR_PHYSICS_PERCENT_MIN, config.cursorAccelerationPercent)
    }

    @Test fun settingsKeepTheirStoredKeys() {
        val prefs = prefs()
        Config(prefs).apply {
            incognitoMode = true
            keepScreenOn = true
            userAgentString.value = "UA"
            adBlockListNextRetry = 5L
        }
        assertTrue(prefs.getBoolean("incognito_mode", false))
        assertTrue(prefs.getBoolean("keep_screen_on", false))
        assertEquals("UA", prefs.getString("user_agent", null))
        assertEquals(5L, prefs.getLong("adblock_list_next_retry", 0))
        Config(prefs).userAgentString.value = null
        assertFalse(prefs.contains("user_agent"))
    }
}
