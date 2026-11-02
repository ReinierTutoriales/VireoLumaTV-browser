package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModel
import com.reiniertutoriales.vireolumatv.utils.observable.LiveValue

/** The settings screens' view of [Config], plus the choices they offer. */
class BrowserSettings : ScreenModel() {
    val config = AppContext.config

    var homePage by config::homePage
    var homePageMode by config::homePageMode

    /** "Keep screen on", observable so the browser window follows a change at once. */
    val keepScreenOn = object : LiveValue<Boolean>(config.keepScreenOn) {
        override var value: Boolean
            get() = config.keepScreenOn
            set(value) {
                config.keepScreenOn = value
                sendAll()
            }
    }

    /** User agent presets; an empty string means the WebView's own, and the last entry is custom. */
    private val userAgentPresets = listOf(
        "Default (recommended)" to "",
        "Chrome (Desktop)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36",
        "Chrome (Mobile)" to "Mozilla/5.0 (Linux; Android 16; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
        "Firefox (Desktop)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
        "Firefox (Mobile)" to "Mozilla/5.0 (Android 16; Mobile; rv:147.0) Gecko/125.0 Firefox/147.0",
        "Edge (Desktop)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0",
        "Custom" to "",
    )
    val userAgentStringTitles: Array<String> = userAgentPresets.map { it.first }.toTypedArray()
    val uaStrings: List<String> = userAgentPresets.map { it.second }

    fun setSearchEngineURL(url: String) {
        config.searchAddress.value = url
        if (homePageMode == Config.HomePageMode.SEARCH_ENGINE) homePage = searchHome(url)
    }

    fun setHomePageProperties(mode: Config.HomePageMode, customHomePageUrl: String?) {
        homePageMode = mode
        homePage = when (mode) {
            Config.HomePageMode.SEARCH_ENGINE -> searchHome(config.searchAddress.value)
            Config.HomePageMode.CUSTOM -> customHomePageUrl ?: Config.HOME_URL_ALIAS
            Config.HomePageMode.HOME_PAGE, Config.HomePageMode.BLANK -> Config.HOME_URL_ALIAS
        }
    }

    /** The search site's start page: its scheme and host, without path or query. */
    private fun searchHome(searchUrl: String): String = SITE.find(searchUrl)?.value ?: Config.HOME_URL_ALIAS

    private companion object {
        val SITE = Regex("^https?://[^#?/]+")
    }
}
