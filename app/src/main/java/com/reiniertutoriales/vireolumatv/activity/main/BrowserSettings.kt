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
