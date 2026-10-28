package com.reiniertutoriales.vireolumatv

import android.content.SharedPreferences
import androidx.core.content.edit
import com.reiniertutoriales.vireolumatv.utils.observable.ObservableValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Browser settings stored in [prefs]. The key strings are what existing installs saved; renaming
 * one would silently reset that setting, so they stay as they are.
 */
class Config(val prefs: SharedPreferences) {

    enum class HomePageMode { HOME_PAGE, SEARCH_ENGINE, CUSTOM, BLANK }

    enum class HomePageLinksMode { BOOKMARKS, LATEST_HISTORY, MOST_VISITED }

    /** Written synchronously: the private-mode process reads it right after the switch. */
    var incognitoMode by flag(INCOGNITO_MODE_KEY, false, commit = true)
    var incognitoModeHintSuppress by flag("incognito_mode_hint_suppress", false)
    var directNavigationModeHintSuppress by flag("direct_navigation_mode_hint_suppress", false)
    var keepScreenOn by flag("keep_screen_on", false)
    /** When true, stick and hat motion is not turned into D-pad keys. */
    var disableMotionAxesDpadNavigation by flag("disable_motion_axes_dpad_navigation", false)
    var allowAutoplayMedia by flag("allow_autoplay_media", false)
    var webviewUseAlgorithmicDarkeningWithDarkUiMode by flag("webview_use_algorithmic_darkening_with_dark_ui_mode", true)
    var webEngineDebug by flag("web_engine_debug", false)

    /** Pointer top speed, as a percent of the default derived from the screen width. */
    var cursorMaxSpeedPercent by percent(CURSOR_MAX_SPEED_PERCENT_KEY)
    /** Pointer acceleration, as a percent of the default ramp per frame. */
    var cursorAccelerationPercent by percent("cursor_acceleration_percent")

    /** Maximum video height in pixels; 0 lets the site decide. */
    var videoMaxHeight by number("video_max_height", 0)
    var appVersionCodeMark by number("app_version_code_mark", 0)

    var homePage: String
        get() = prefs.getString(HOME_PAGE_KEY, HOME_URL_ALIAS) ?: HOME_URL_ALIAS
        set(value) = prefs.edit { putString(HOME_PAGE_KEY, value) }

    var homePageMode: HomePageMode
        get() {
            // Versions that had a "search engine as home page" switch are moved over once.
            if (prefs.getBoolean(LEGACY_SEARCH_AS_HOME_KEY, false)) {
                prefs.edit {
                    remove(LEGACY_SEARCH_AS_HOME_KEY)
                    putInt(HOME_PAGE_MODE_KEY, HomePageMode.SEARCH_ENGINE.ordinal)
                }
                return HomePageMode.SEARCH_ENGINE
            }
            return HomePageMode.entries.getOrElse(prefs.getInt(HOME_PAGE_MODE_KEY, 0)) { HomePageMode.HOME_PAGE }
        }
        set(value) = prefs.edit { putInt(HOME_PAGE_MODE_KEY, value.ordinal) }

    var homePageLinksMode: HomePageLinksMode
        get() = HomePageLinksMode.entries.getOrElse(prefs.getInt(HOME_PAGE_LINKS_MODE_KEY, 0)) { HomePageLinksMode.BOOKMARKS }
        set(value) = prefs.edit { putInt(HOME_PAGE_LINKS_MODE_KEY, value.ordinal) }

    val searchEngineURL = StringPreference(SEARCH_ENGINE_URL_PREF_KEY, DUCKDUCKGO_SEARCH_URL)

    /** Null means the WebView's own user agent. */
    val userAgentString = OptionalStringPreference("user_agent")

    /** Read on every request, so it is kept in memory as well. */
    var adBlockEnabled: Boolean = prefs.getBoolean(ADBLOCK_ENABLED_PREF_KEY, true)
        set(value) {
            field = value
            prefs.edit { putBoolean(ADBLOCK_ENABLED_PREF_KEY, value) }
        }
    val adBlockListURL = StringPreference(ADBLOCK_LIST_URL_KEY, DEFAULT_ADBLOCK_LIST_URL)
    var adBlockListLastUpdate by time("adblock_last_update")
    var adBlockListNextRetry by time("adblock_list_next_retry")

    init {
        // DuckDuckGo became the default search once; later choices are kept.
        if (!prefs.getBoolean(DDG_DEFAULT_APPLIED_KEY, false)) {
            searchEngineURL.value = DUCKDUCKGO_SEARCH_URL
            prefs.edit { putBoolean(DDG_DEFAULT_APPLIED_KEY, true) }
        }
    }

    /** A text setting that notifies its observers when it changes. */
    inner class StringPreference(private val key: String, default: String) : ObservableValue<String>(default) {
        override var value: String = prefs.getString(key, default) ?: default
            set(value) {
                prefs.edit { putString(key, value) }
                field = value
                notifyObservers()
            }
    }

    /** Like [StringPreference]; null removes the stored value. */
    inner class OptionalStringPreference(private val key: String) : ObservableValue<String?>(null) {
        override var value: String? = prefs.getString(key, null)
            set(value) {
                prefs.edit { if (value == null) remove(key) else putString(key, value) }
                field = value
                notifyObservers()
            }
    }

    private fun flag(key: String, default: Boolean, commit: Boolean = false) = setting(
        { prefs.getBoolean(key, default) }, { value: Boolean -> prefs.edit(commit) { putBoolean(key, value) } })

    private fun number(key: String, default: Int) = setting(
        { prefs.getInt(key, default) }, { value: Int -> prefs.edit { putInt(key, value) } })

    private fun time(key: String) = setting(
        { prefs.getLong(key, 0L) }, { value: Long -> prefs.edit { putLong(key, value) } })

    private fun percent(key: String) = setting(
        { prefs.getInt(key, 100).coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX) },
        { value: Int -> prefs.edit { putInt(key, value.coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX)) } })

    private fun <T> setting(read: () -> T, write: (T) -> Unit) = object : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = read()
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = write(value)
    }

    /** One choice in the search settings; the custom entry has an empty URL template. */
    data class SearchEngine(val title: String, val url: String)

    companion object {
        const val SEARCH_ENGINE_URL_PREF_KEY = "search_engine_url"
        const val HOME_PAGE_KEY = "home_page"
        const val INCOGNITO_MODE_KEY = "incognito_mode"
        const val CURSOR_MAX_SPEED_PERCENT_KEY = "cursor_max_speed_percent"
        const val ADBLOCK_ENABLED_PREF_KEY = "adblock_enabled"
        const val ADBLOCK_LIST_URL_KEY = "adblock_list_url"
        private const val HOME_PAGE_MODE_KEY = "home_page_mode"
        private const val HOME_PAGE_LINKS_MODE_KEY = "home_page_suggestions_mode"
        private const val LEGACY_SEARCH_AS_HOME_KEY = "search_engine_as_home_page"
        private const val DDG_DEFAULT_APPLIED_KEY = "minimal_home_search_migrated"

        const val CURSOR_PHYSICS_PERCENT_MIN = 25
        const val CURSOR_PHYSICS_PERCENT_MAX = 200

        const val HOME_URL_ALIAS = "about:home"
        const val HOME_PAGE_URL = "https://vireolumatv.invalid/appcontent/home/"
        const val DEFAULT_ADBLOCK_LIST_URL = "https://easylist.to/easylist/easylist.txt"
        const val DUCKDUCKGO_SEARCH_URL = "https://duckduckgo.com/?q=[query]"

        /** "[query]" in a URL is replaced by the typed text. The last entry is the custom one. */
        val SEARCH_ENGINES = listOf(
            SearchEngine("Google", "https://www.google.com/search?q=[query]"),
            SearchEngine("Bing", "https://www.bing.com/search?q=[query]"),
            SearchEngine("Yahoo!", "https://search.yahoo.com/search?p=[query]"),
            SearchEngine("DuckDuckGo", DUCKDUCKGO_SEARCH_URL),
            SearchEngine("Yandex", "https://yandex.com/search/?text=[query]"),
            SearchEngine("Startpage", "https://www.startpage.com/sp/search?query=[query]"),
            SearchEngine("Custom", ""),
        )
    }
}
