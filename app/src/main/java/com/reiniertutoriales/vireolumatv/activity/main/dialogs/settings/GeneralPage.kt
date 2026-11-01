package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.AdBlocker
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.BrowserSettings
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.SettingsRows.Companion.reveal
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.utils.AppLanguages
import com.reiniertutoriales.vireolumatv.utils.activity
import com.reiniertutoriales.vireolumatv.webengine.Engines
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** General settings page. Choices apply at once; text fields are applied by [save] on close. */
class GeneralPage @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {
    var browserSettings = ScreenModels.get(BrowserSettings::class, activity!!)
    var adBlocker = ScreenModels.get(AdBlocker::class, activity!!)
    var config = AppContext.config

    private val rows = SettingsRows(
        LayoutInflater.from(context).inflate(R.layout.settings_general, this, true)
            .findViewById<LinearLayout>(R.id.settingsList))
    private lateinit var searchEngineUrl: EditText
    private lateinit var homePageMode: Spinner
    private lateinit var customHomePageUrl: EditText
    private lateinit var userAgent: EditText
    private lateinit var adBlockListUrl: EditText
    private lateinit var adBlockStatus: SettingsRows.Status

    init {
        rows.section(R.string.settings_section_browsing)
        addSearchEngine()
        addHomePage()
        addUserAgent()
        rows.section(R.string.settings_section_protection)
        addAdBlock()
        rows.section(R.string.settings_section_display)
        rows.choice(R.string.settings_language, R.id.spAppLanguage,
            AppLanguages.names(context.getString(R.string.language_system)), AppLanguages.chosen()) { AppLanguages.choose(it) }
        addAppearanceAndPlayback()
        rows.section(R.string.settings_section_remote)
        addInput()
        rows.section(R.string.settings_section_advanced)
        addAdvanced()
    }

    /** What most people never change: where the filter list comes from, debugging and the cache. */
    private fun addAdvanced() {
        adBlockListUrl = rows.field(R.string.settings_filter_list, R.string.field_address, id = R.id.etAdBlockerListUrl)
        adBlockListUrl.setText(config.blockListUrl.value)
        addDebugging()
        rows.button(R.string.settings_clear_cache, R.id.btnClearWebCache) {
            (activity as MainActivity).lifecycleScope.launch {
                Engines.clearCache(context)
                Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addSearchEngine() {
        val urls = Config.SEARCH_ENGINES.map { it.url }
        val custom = Config.SEARCH_ENGINES.lastIndex
        val saved = config.searchAddress.value
        val known = if (saved == "") 0 else urls.indexOf(saved)
        rows.choice(R.string.settings_search_engine, R.id.spEngine, Config.SEARCH_ENGINES.map { it.title }.toTypedArray(),
            if (known == -1) custom else known) { position ->
            val group = searchEngineUrl.parent as View
            if (position == custom) {
                reveal(group)
                searchEngineUrl.setText(config.searchAddress.value)
                searchEngineUrl.requestFocus()
            } else {
                group.visibility = View.GONE
                searchEngineUrl.setText(urls[position])
            }
        }
        searchEngineUrl = rows.field(R.string.field_address, R.string.settings_search_engine_address,
            visible = known == -1)
        searchEngineUrl.setText(if (known == -1) saved else urls[known])
        if (known == -1) searchEngineUrl.requestFocus()
    }

    private fun addHomePage() {
        val modes = Config.HomePageMode.entries
        homePageMode = rows.choice(R.string.settings_home_page, R.id.spHomePage,
            resources.getStringArray(R.array.settings_home_page_modes), browserSettings.homePageMode.ordinal) { position ->
            (customHomePageUrl.parent as View).visibility =
                if (modes[position] == Config.HomePageMode.CUSTOM) View.VISIBLE else View.GONE
        }
        customHomePageUrl = rows.field(R.string.settings_home_page_address, R.string.field_address,
            visible = browserSettings.homePageMode == Config.HomePageMode.CUSTOM)
        customHomePageUrl.setText(browserSettings.homePage)
    }

    private fun addUserAgent() {
        val presets = browserSettings.uaStrings
        val custom = browserSettings.userAgentStringTitles.lastIndex
        val saved = config.userAgentOverride.value
        val known = if (saved == null) 0 else presets.indexOf(saved)
        rows.choice(R.string.settings_user_agent_choice, R.id.spTitles, browserSettings.userAgentStringTitles,
            if (known == -1) custom else known) { position ->
            val group = userAgent.parent as View
            if (position == custom) {
                // Repeated custom selections keep a saved or half-typed value.
                if (group.visibility != View.VISIBLE) {
                    reveal(group)
                    userAgent.setText(config.userAgentOverride.value.orEmpty())
                    userAgent.requestFocus()
                }
            } else {
                group.visibility = View.GONE
                userAgent.setText(presets[position])
            }
        }
        userAgent = rows.field(R.string.settings_user_agent, R.string.settings_user_agent_custom,
            id = R.id.etUAString, groupId = R.id.llUAString, visible = known == -1)
        userAgent.setText(if (known == -1) saved.orEmpty() else presets[known])
        if (known == -1) userAgent.requestFocus()
    }

    private fun addAdBlock() {
        lateinit var details: View
        rows.toggle(R.string.blocker_toggle, R.id.llAdblock, config.adBlockEnabled) { _, enabled ->
            config.adBlockEnabled = enabled
            (activity as MainActivity).refreshAdblockSettings()
            details.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        details = rows.group(config.adBlockEnabled) {
            adBlockStatus = status(R.id.tvAdBlockerListInfo, R.id.btnAdBlockerUpdate,
                R.drawable.ic_refresh, R.string.blocker_update_lists) {
                if (!adBlocker.listLoading.value) {
                    setBlockListUrl()
                    adBlocker.loadBlockList(true)
                }
            }
        }
        showBlockerState()
    }

    private fun addAppearanceAndPlayback() {
        rows.toggle(R.string.settings_darken_pages, R.id.llWebViewAlgorithmicDarkening,
            config.darkenPages) { _, on ->
            config.darkenPages = on
        }
        rows.toggle(R.string.settings_autoplay, R.id.llAllowAutoplayMedia, config.allowAutoplayMedia) { _, on ->
            config.allowAutoplayMedia = on
        }
        // Read by the document-start script from the next page load; codecs follow the hardware decoders.
        val heights = intArrayOf(0, 1080, 720, 480)
        rows.choice(R.string.settings_video_quality, R.id.spVideoMaxQuality, resources.getStringArray(R.array.settings_video_qualities),
            heights.indexOf(config.videoMaxHeight).coerceAtLeast(0)) { config.videoMaxHeight = heights[it] }
        rows.toggle(R.string.settings_keep_screen_on, R.id.llKeepScreenOn, browserSettings.keepScreenOn.value) { _, on ->
            browserSettings.keepScreenOn.value = on
        }
    }

    private fun addDebugging() {
        rows.toggle(R.string.settings_debugging, R.id.llWebEngineDebug, config.pageDebugging) { switch, on ->
            if (!on) {
                config.pageDebugging = false
                // Closes remote debugging of the pages already open, not only of later ones.
                Engines.setPageDebugging(false)
                return@toggle
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.warning_title)
                .setMessage(R.string.settings_debugging_warning)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    config.pageDebugging = true
                    Toast.makeText(context, R.string.settings_restart_needed, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.action_cancel) { _, _ -> switch.isChecked = false }
                .setOnCancelListener { switch.isChecked = false }
                .show()
        }
    }

    private fun addInput() {
        rows.toggle(R.string.settings_stick_moves_pointer, R.id.llNavigateWithJoystickAxes,
            !config.ignoreStickAxes) { _, on -> config.ignoreStickAxes = !on }
        val percent = { value: Int -> context.getString(R.string.settings_percent_of_default, value) }
        rows.slider(R.string.settings_pointer_speed, R.id.sbCursorMaxSpeed, Config.POINTER_PERCENT_MIN,
            Config.POINTER_PERCENT_MAX, config.pointerSpeedPercent, percent) { config.pointerSpeedPercent = it }
        rows.slider(R.string.settings_pointer_acceleration, R.id.sbCursorAcceleration, Config.POINTER_PERCENT_MIN,
            Config.POINTER_PERCENT_MAX, config.pointerAccelPercent, percent) { config.pointerAccelPercent = it }
    }

    private val loadingObserver: (Boolean) -> Unit = { showBlockerState() }
    private val resultObserver: (AdBlocker.UpdateResult) -> Unit = { showBlockerState() }
    private val sourceObserver: (String) -> Unit = { showBlockerState() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        adBlocker.listLoading.observe(loadingObserver)
        adBlocker.updateResult.observe(resultObserver)
        config.blockListUrl.observe(sourceObserver)
    }

    override fun onDetachedFromWindow() {
        adBlocker.listLoading.forget(loadingObserver)
        adBlocker.updateResult.forget(resultObserver)
        config.blockListUrl.forget(sourceObserver)
        super.onDetachedFromWindow()
    }

    private fun setBlockListUrl() {
        val source = adBlockListUrl.text.toString().trim().ifEmpty { Config.DEFAULT_ADBLOCK_LIST_URL }
        if (config.blockListUrl.value != source) {
            config.blockListUpdatedAt = 0L
            config.blockListRetryAt = 0L
            config.blockListUrl.value = source
        }
    }

    private fun showBlockerState() {
        val loading = adBlocker.listLoading.value
        val lastUpdate = if (config.blockListUpdatedAt == 0L) context.getString(R.string.blocker_never)
            else SimpleDateFormat("hh:mm dd MMMM yyyy", Locale.getDefault()).format(Date(config.blockListUpdatedAt))
        val lists = if (config.blockListUrl.value == Config.DEFAULT_ADBLOCK_LIST_URL)
            adBlocker.configuredListNames().joinToString(" · ") else config.blockListUrl.value
        val state = when {
            loading -> R.string.blocker_updating
            else -> when (adBlocker.updateResult.value) {
                AdBlocker.UpdateResult.UPDATED -> R.string.blocker_updated
                AdBlocker.UpdateResult.PARTIAL -> R.string.blocker_partial
                AdBlocker.UpdateResult.CACHED -> R.string.blocker_using_saved
                AdBlocker.UpdateResult.ERROR -> R.string.blocker_update_failed
                else -> null
            }
        }
        val notRefreshed = adBlocker.unavailableLists.value
        adBlockStatus.text.text = buildList {
            add("${context.getString(R.string.blocker_last_update)}: $lastUpdate")
            add(lists)
            state?.let { add(context.getString(it)) }
            if (!loading && notRefreshed.isNotEmpty())
                add(context.getString(R.string.blocker_not_refreshed, notRefreshed.joinToString(" · ")))
        }.joinToString("\n")
        // The focused TV button stays in place while busy; its click ignores duplicate refreshes.
        adBlockStatus.action.isEnabled = true
        adBlockStatus.action.visibility = View.VISIBLE
        adBlockStatus.progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
    }

    fun save() {
        browserSettings.setSearchEngineURL(searchEngineUrl.text.toString())
        browserSettings.setHomePageProperties(Config.HomePageMode.entries[homePageMode.selectedItemPosition],
            customHomePageUrl.text.toString())
        config.userAgentOverride.value = userAgent.text.toString().trim(' ').ifEmpty { null }
        setBlockListUrl()
    }
}
