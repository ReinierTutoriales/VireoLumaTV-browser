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
class ConfigThemeTest {
    @Test fun invalidThemeOrdinalsUseSystemWithoutResettingOtherPreferences() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("theme-recovery", 0)
        for (ordinal in listOf(-1, 3, Int.MIN_VALUE, Int.MAX_VALUE)) {
            prefs.edit().clear().putInt(Config.THEME_KEY, ordinal)
                .putString(Config.HOME_PAGE_KEY, "https://custom.example/")
                .putInt(Config.CURSOR_MAX_SPEED_PERCENT_KEY, 75).commit()
            val config = Config(prefs)
            assertEquals(Config.Theme.SYSTEM, config.theme.value)
            assertEquals("https://custom.example/", config.homePage)
            assertEquals(75, config.cursorMaxSpeedPercent)
            config.theme.value = Config.Theme.BLACK
            assertEquals(Config.Theme.BLACK, Config(prefs).theme.value)
        }
    }

    @Test fun validThemesRetainTheirExistingOrdinals() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("valid-themes", 0)
        for (theme in Config.Theme.entries) {
            prefs.edit().clear().putInt(Config.THEME_KEY, theme.ordinal).commit()
            assertEquals(theme, Config(prefs).theme.value)
        }
    }
}
