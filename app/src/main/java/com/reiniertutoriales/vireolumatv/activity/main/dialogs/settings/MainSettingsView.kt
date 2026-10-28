package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.app.AlertDialog
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
import com.reiniertutoriales.vireolumatv.activity.main.AdblockModel
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.SettingsModel
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.SettingsRows.Companion.reveal
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import com.reiniertutoriales.vireolumatv.utils.activity
import com.reiniertutoriales.vireolumatv.webengine.Engines
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** General settings page. Choices apply at once; text fields are applied by [save] on close. */
class MainSettingsView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {
    var settingsModel = ActiveModelsRepository.get(SettingsModel::class, activity!!)
    var adblockModel = ActiveModelsRepository.get(AdblockModel::class, activity!!)
    var config = AppContext.provideConfig()

    private val rows = SettingsRows(
        LayoutInflater.from(context).inflate(R.layout.view_settings_main, this, true)
            .findViewById<LinearLayout>(R.id.settingsList))
    private lateinit var searchEngineUrl: EditText
    private lateinit var homePageMode: Spinner
    private lateinit var customHomePageUrl: EditText
    private lateinit var userAgent: EditText
    private lateinit var adBlockListUrl: EditText
    private lateinit var adBlockStatus: SettingsRows.Status

    init {
        addSearchEngine()
        addHomePage()
        addUserAgent()
        addAdBlock()
        addAppearanceAndPlayback()
        addInput()
        rows.button(R.string.clear_web_cache, R.id.btnClearWebCache) {
            (activity as MainActivity).lifecycleScope.launch {
                Engines.clearCache(context)
                Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addSearchEngine() {
        val urls = Config.SEARCH_ENGINES.map { it.url }
        val custom = Config.SEARCH_ENGINES.lastIndex
        val saved = config.searchEngineURL.value
        val known = if (saved == "") 0 else urls.indexOf(saved)
        rows.choice(R.string.choose_default_search_engine, R.id.spEngine, Config.SEARCH_ENGINES.map { it.title }.toTypedArray(),
            if (known == -1) custom else known) { position ->
            val group = searchEngineUrl.parent as View
            if (position == custom) {
                reveal(group)
                searchEngineUrl.setText(config.searchEngineURL.value)
                searchEngineUrl.requestFocus()
            } else {
                group.visibility = View.GONE
                searchEngineUrl.setText(urls[position])
            }
        }
        searchEngineUrl = rows.field(R.string.url, R.string.search_engine_url_with_query_placeholder_query,
            visible = known == -1)
        searchEngineUrl.setText(if (known == -1) saved else urls[known])
        if (known == -1) searchEngineUrl.requestFocus()
    }

    private fun addHomePage() {
        val modes = Config.HomePageMode.entries
        homePageMode = rows.choice(R.string.home_page, R.id.spHomePage,
            resources.getStringArray(R.array.home_page_modes), settingsModel.homePageMode.ordinal) { position ->
            (customHomePageUrl.parent as View).visibility =
                if (modes[position] == Config.HomePageMode.CUSTOM) View.VISIBLE else View.GONE
        }
        customHomePageUrl = rows.field(R.string.custom_home_page_url, R.string.url,
            visible = settingsModel.homePageMode == Config.HomePageMode.CUSTOM)
        customHomePageUrl.setText(settingsModel.homePage)
    }

    private fun addUserAgent() {
        if (config.userAgentString.value?.contains("TV Bro/1.0 ") == true) {
            config.userAgentString.value = null // Legacy value: the default user agent applies now.
        }
        val presets = settingsModel.uaStrings
        val custom = settingsModel.userAgentStringTitles.lastIndex
        val saved = config.userAgentString.value
        val known = if (saved == null) 0 else presets.indexOf(saved)
        rows.choice(R.string.choose_user_agent_string, R.id.spTitles, settingsModel.userAgentStringTitles,
            if (known == -1) custom else known) { position ->
            val group = userAgent.parent as View
            if (position == custom) {
                // Repeated custom selections keep a saved or half-typed value.
                if (group.visibility != View.VISIBLE) {
                    reveal(group)
                    userAgent.setText(config.userAgentString.value.orEmpty())
                    userAgent.requestFocus()
                }
            } else {
                group.visibility = View.GONE
                userAgent.setText(presets[position])
            }
        }
        userAgent = rows.field(R.string.user_agent_string, R.string.edit_user_agent_string,
            id = R.id.etUAString, groupId = R.id.llUAString, visible = known == -1)
        userAgent.setText(if (known == -1) saved.orEmpty() else presets[known])
        if (known == -1) userAgent.requestFocus()
    }

    private fun addAdBlock() {
        lateinit var details: View
        rows.toggle(R.string.toggle_ads_blocking, R.id.llAdblock, config.adBlockEnabled) { _, enabled ->
            config.adBlockEnabled = enabled
            (activity as MainActivity).refreshAdblockSettings()
            details.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        details = rows.group(config.adBlockEnabled) {
            adBlockListUrl = field(null, R.string.url, id = R.id.etAdBlockerListUrl)
            adBlockStatus = status(R.id.tvAdBlockerListInfo, R.id.btnAdBlockerUpdate,
                R.drawable.ic_refresh_grey_900_24dp, R.string.update_ad_blocker_list) {
                if (!adblockModel.clientLoading.value) {
                    saveAdBlockListUrl()
                    adblockModel.loadAdBlockList(true)
                }
            }
        }
        adBlockListUrl.setText(config.adBlockListURL.value)
        updateAdBlockInfo()
    }

    private fun addAppearanceAndPlayback() {
        rows.toggle(R.string.webview_use_algorithmic_darkening_with_dark_ui_mode, R.id.llWebViewAlgorithmicDarkening,
            config.webviewUseAlgorithmicDarkeningWithDarkUiMode) { _, on ->
            config.webviewUseAlgorithmicDarkeningWithDarkUiMode = on
        }
        rows.toggle(R.string.allow_autoplay_media, R.id.llAllowAutoplayMedia, config.allowAutoplayMedia) { _, on ->
            config.allowAutoplayMedia = on
        }
        // Read by the document-start script from the next page load; codecs follow the hardware decoders.
        val heights = intArrayOf(0, 1080, 720, 480)
        rows.choice(R.string.video_max_quality, R.id.spVideoMaxQuality, resources.getStringArray(R.array.video_max_qualities),
            heights.indexOf(config.videoMaxHeight).coerceAtLeast(0)) { config.videoMaxHeight = heights[it] }
        rows.toggle(R.string.web_engine_debug, R.id.llWebEngineDebug, config.webEngineDebug) { switch, on ->
            if (!on) {
                config.webEngineDebug = false
                return@toggle
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.warning)
                .setMessage(R.string.web_engine_debug_warning_message)
                .setPositiveButton(R.string.ok) { _, _ ->
                    config.webEngineDebug = true
                    Toast.makeText(context, R.string.need_restart, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> switch.isChecked = false }
                .setOnCancelListener { switch.isChecked = false }
                .show()
        }
    }

    private fun addInput() {
        rows.toggle(R.string.toggle_keep_screen_on, R.id.llKeepScreenOn, settingsModel.keepScreenOn.value) { _, on ->
            settingsModel.keepScreenOn.value = on
        }
        rows.toggle(R.string.navigate_with_joystick_axes, R.id.llNavigateWithJoystickAxes,
            !config.disableMotionAxesDpadNavigation) { _, on -> config.disableMotionAxesDpadNavigation = !on }
        val percent = { value: Int -> context.getString(R.string.cursor_physics_percent, value) }
        rows.slider(R.string.cursor_max_speed, R.id.sbCursorMaxSpeed, Config.CURSOR_PHYSICS_PERCENT_MIN,
            Config.CURSOR_PHYSICS_PERCENT_MAX, config.cursorMaxSpeedPercent, percent) { config.cursorMaxSpeedPercent = it }
        rows.slider(R.string.cursor_acceleration, R.id.sbCursorAcceleration, Config.CURSOR_PHYSICS_PERCENT_MIN,
            Config.CURSOR_PHYSICS_PERCENT_MAX, config.cursorAccelerationPercent, percent) { config.cursorAccelerationPercent = it }
    }

    private val loadingObserver: (Boolean) -> Unit = { updateAdBlockInfo() }
    private val resultObserver: (AdblockModel.UpdateResult) -> Unit = { updateAdBlockInfo() }
    private val sourceObserver: (String) -> Unit = { updateAdBlockInfo() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        adblockModel.clientLoading.subscribe(loadingObserver)
        adblockModel.updateResult.subscribe(resultObserver)
        config.adBlockListURL.subscribe(sourceObserver)
    }

    override fun onDetachedFromWindow() {
        adblockModel.clientLoading.unsubscribe(loadingObserver)
        adblockModel.updateResult.unsubscribe(resultObserver)
        config.adBlockListURL.unsubscribe(sourceObserver)
        super.onDetachedFromWindow()
    }

    private fun saveAdBlockListUrl() {
        val source = adBlockListUrl.text.toString().trim().ifEmpty { Config.DEFAULT_ADBLOCK_LIST_URL }
        if (config.adBlockListURL.value != source) {
            config.adBlockListLastUpdate = 0L
            config.adBlockListNextRetry = 0L
            config.adBlockListURL.value = source
        }
    }

    private fun updateAdBlockInfo() {
        val loading = adblockModel.clientLoading.value
        val lastUpdate = if (config.adBlockListLastUpdate == 0L) context.getString(R.string.never)
            else SimpleDateFormat("hh:mm dd MMMM yyyy", Locale.getDefault()).format(Date(config.adBlockListLastUpdate))
        val lists = if (config.adBlockListURL.value == Config.DEFAULT_ADBLOCK_LIST_URL)
            adblockModel.configuredListNames().joinToString(" · ") else config.adBlockListURL.value
        val state = when {
            loading -> R.string.adblock_updating
            else -> when (adblockModel.updateResult.value) {
                AdblockModel.UpdateResult.UPDATED -> R.string.adblock_updated
                AdblockModel.UpdateResult.PARTIAL -> R.string.adblock_partial_update
                AdblockModel.UpdateResult.CACHED -> R.string.adblock_using_saved
                AdblockModel.UpdateResult.ERROR -> R.string.adblock_update_error
                else -> null
            }
        }
        val notRefreshed = adblockModel.unavailableLists.value
        adBlockStatus.text.text = buildList {
            add("${context.getString(R.string.last_update)}: $lastUpdate")
            add(lists)
            state?.let { add(context.getString(it)) }
            if (!loading && notRefreshed.isNotEmpty())
                add(context.getString(R.string.adblock_lists_not_refreshed, notRefreshed.joinToString(" · ")))
        }.joinToString("\n")
        // The focused TV button stays in place while busy; its click ignores duplicate refreshes.
        adBlockStatus.action.isEnabled = true
        adBlockStatus.action.visibility = View.VISIBLE
        adBlockStatus.progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
    }

    fun save() {
        settingsModel.setSearchEngineURL(searchEngineUrl.text.toString())
        settingsModel.setHomePageProperties(Config.HomePageMode.entries[homePageMode.selectedItemPosition],
            customHomePageUrl.text.toString())
        config.userAgentString.value = userAgent.text.toString().trim(' ').ifEmpty { null }
        saveAdBlockListUrl()
    }
}
