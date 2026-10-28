package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
@RoboConfig(application = VireoLumaTVApp::class, sdk = [28])
class SettingsModelTest {
    @Test fun searchHomeFollowsTheSearchEngineSite() {
        val model = BrowserSettings()
        model.config.searchAddress.value = "https://search.example/find?q=[query]"
        model.setHomePageProperties(Config.HomePageMode.SEARCH_ENGINE, null)
        assertEquals("https://search.example", model.homePage)
        model.setSearchEngineURL("http://other.example/?s=[query]")
        assertEquals("http://other.example", model.homePage)
    }

    @Test fun customAndBuiltInHomePages() {
        val model = BrowserSettings()
        model.setHomePageProperties(Config.HomePageMode.CUSTOM, "https://mine.example/")
        assertEquals("https://mine.example/", model.homePage)
        model.setSearchEngineURL("https://search.example/?q=[query]")
        assertEquals("A custom home page is not replaced by the search site", "https://mine.example/", model.homePage)
        model.setHomePageProperties(Config.HomePageMode.BLANK, null)
        assertEquals(Config.HOME_URL_ALIAS, model.homePage)
    }

    @Test fun keepScreenOnIsStoredAndAnnounced() {
        val model = BrowserSettings()
        val seen = mutableListOf<Boolean>()
        model.keepScreenOn.observe({ seen += it }, sendCurrent = false)
        model.keepScreenOn.value = true
        assertTrue(model.config.keepScreenOn)
        model.keepScreenOn.value = false
        assertEquals(listOf(true, false), seen)
    }
}
