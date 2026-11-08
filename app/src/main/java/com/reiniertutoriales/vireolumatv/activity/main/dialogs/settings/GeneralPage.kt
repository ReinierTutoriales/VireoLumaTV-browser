package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.AdBlocker
import com.reiniertutoriales.vireolumatv.activity.main.BrowserSettings
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvListRow
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.utils.AppLanguages
import com.reiniertutoriales.vireolumatv.utils.FrameStats
import com.reiniertutoriales.vireolumatv.webengine.Engines
import com.reiniertutoriales.vireolumatv.webengine.webview.MediaPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * General settings. Choices and switches apply at once; the text values (search address, custom
 * home page, filter list) are kept here while the screen is open and applied by [save] on close.
 */
class GeneralPage(
    private val context: Context,
    private val browserSettings: BrowserSettings,
    private val adBlocker: AdBlocker,
    private val scope: CoroutineScope,
    private val onBlockerChanged: () -> Unit,
    private val config: Config = AppContext.config,
) {
    private val engineUrls = Config.SEARCH_ENGINES.map { it.url }
    private val customEngine = Config.SEARCH_ENGINES.lastIndex
    private var engine by mutableIntStateOf(
        config.searchAddress.value.let { saved -> if (saved == "") 0 else engineUrls.indexOf(saved) }
            .let { if (it == -1) customEngine else it })
    private var searchUrl by mutableStateOf(
        config.searchAddress.value.takeIf { engine == customEngine } ?: engineUrls[engine])
    private var homeMode by mutableStateOf(browserSettings.homePageMode)
    private var homeUrl by mutableStateOf(browserSettings.homePage)
    private var listUrl by mutableStateOf(config.blockListUrl.value)
    private var adBlock by mutableStateOf(config.adBlockEnabled)
    private var darken by mutableStateOf(config.darkenPages)
    private var autoplay by mutableStateOf(config.allowAutoplayMedia)
    private var videoHeight by mutableIntStateOf(config.videoMaxHeight)
    private var keepScreenOn by mutableStateOf(browserSettings.keepScreenOn.value)
    private var stickMovesPointer by mutableStateOf(!config.ignoreStickAxes)
    private var pointerSpeed by mutableIntStateOf(config.pointerSpeedPercent)
    private var pointerAccel by mutableIntStateOf(config.pointerAccelPercent)
    private var debugging by mutableStateOf(config.pageDebugging)
    /** Bumped by the blocker observers so the status text is read again. */
    private var blockerChanges by mutableIntStateOf(0)

    @Composable
    fun Content() {
        DisposableEffect(adBlocker, config) {
            val loading: (Boolean) -> Unit = { blockerChanges++ }
            val result: (AdBlocker.UpdateResult) -> Unit = { blockerChanges++ }
            val source: (String) -> Unit = { blockerChanges++ }
            adBlocker.listLoading.observe(loading)
            adBlocker.updateResult.observe(result)
            config.blockListUrl.observe(source)
            onDispose {
                adBlocker.listLoading.forget(loading)
                adBlocker.updateResult.forget(result)
                config.blockListUrl.forget(source)
            }
        }
        Column {
            SettingsSection(R.string.settings_section_browsing)
            SettingsChoice(context, R.string.settings_search_engine, "spEngine",
                Config.SEARCH_ENGINES.map { it.title }.toTypedArray(), engine) { position ->
                engine = position
                if (position == customEngine) {
                    searchUrl = config.searchAddress.value
                    editText(context, R.string.field_address, R.string.settings_search_engine_address, searchUrl) { searchUrl = it }
                } else {
                    searchUrl = engineUrls[position]
                }
            }
            if (engine == customEngine) {
                SettingsText(context, R.string.field_address, R.string.settings_search_engine_address,
                    "etSearchEngineUrl", searchUrl) { searchUrl = it }
            }
            val modes = Config.HomePageMode.entries
            SettingsChoice(context, R.string.settings_home_page, "spHomePage",
                context.resources.getStringArray(R.array.settings_home_page_modes), homeMode.ordinal) { homeMode = modes[it] }
            if (homeMode == Config.HomePageMode.CUSTOM) {
                SettingsText(context, R.string.settings_home_page_address, R.string.field_address,
                    "etCustomHomePageUrl", homeUrl) { homeUrl = it }
            }

            SettingsSection(R.string.settings_section_protection)
            SettingsToggle(R.string.blocker_toggle, "llAdblock", adBlock) {
                adBlock = it
                config.adBlockEnabled = it
                onBlockerChanged()
            }
            if (adBlock) {
                blockerChanges.let { SettingsInfo(blockerState(), Modifier.testTag("tvAdBlockerListInfo")) }
                // Stays in place while busy so focus does not move; a second press is ignored.
                TvListRow(stringResource(R.string.blocker_update_lists), symbol = TvSymbol.Refresh,
                    modifier = Modifier.testTag("btnAdBlockerUpdate"), onClick = {
                        if (!adBlocker.listLoading.value) {
                            applyBlockListUrl()
                            adBlocker.loadBlockList(true)
                        }
                    })
            }

            SettingsSection(R.string.settings_section_display)
            SettingsChoice(context, R.string.settings_language, "spAppLanguage",
                AppLanguages.names(context.getString(R.string.language_system)), AppLanguages.chosen()) { AppLanguages.choose(it) }
            SettingsToggle(R.string.settings_darken_pages, "llWebViewAlgorithmicDarkening", darken) {
                darken = it; config.darkenPages = it
            }
            SettingsToggle(R.string.settings_autoplay, "llAllowAutoplayMedia", autoplay) {
                autoplay = it; config.allowAutoplayMedia = it
            }
            // Read by the document-start script from the next page load. Automatic follows what the
            // hardware decoders handle; no limit keeps only the codecs the TV has no decoder for out.
            SettingsChoice(context, R.string.settings_video_quality, "spVideoMaxQuality",
                context.resources.getStringArray(R.array.settings_video_qualities),
                VIDEO_HEIGHTS.indexOf(videoHeight).coerceAtLeast(0)) {
                videoHeight = VIDEO_HEIGHTS[it]; config.videoMaxHeight = videoHeight
            }
            SettingsToggle(R.string.settings_keep_screen_on, "llKeepScreenOn", keepScreenOn) {
                keepScreenOn = it; browserSettings.keepScreenOn.value = it
            }

            SettingsSection(R.string.settings_section_remote)
            SettingsToggle(R.string.settings_stick_moves_pointer, "llNavigateWithJoystickAxes", stickMovesPointer) {
                stickMovesPointer = it; config.ignoreStickAxes = !it
            }
            val percent = { value: Int -> context.getString(R.string.settings_percent_of_default, value) }
            SettingsSlider(R.string.settings_pointer_speed, "sbCursorMaxSpeed", Config.POINTER_PERCENT_MIN,
                Config.POINTER_PERCENT_MAX, pointerSpeed, POINTER_STEP, percent) {
                pointerSpeed = it; config.pointerSpeedPercent = it
            }
            SettingsSlider(R.string.settings_pointer_acceleration, "sbCursorAcceleration", Config.POINTER_PERCENT_MIN,
                Config.POINTER_PERCENT_MAX, pointerAccel, POINTER_STEP, percent) {
                pointerAccel = it; config.pointerAccelPercent = it
            }

            SettingsSection(R.string.settings_section_advanced)
            SettingsText(context, R.string.settings_filter_list, R.string.field_address, "etAdBlockerListUrl", listUrl) {
                listUrl = it
            }
            SettingsToggle(R.string.settings_debugging, "llWebEngineDebug", debugging) { on ->
                if (!on) {
                    debugging = false
                    config.pageDebugging = false
                    // Closes remote debugging of the pages already open, not only of later ones.
                    Engines.setPageDebugging(false)
                    return@SettingsToggle
                }
                debugging = true
                confirmWarning(context, R.string.warning_title, R.string.settings_debugging_warning, onConfirmed = {
                    config.pageDebugging = true
                    Toast.makeText(context, R.string.settings_restart_needed, Toast.LENGTH_SHORT).show()
                }, onCancelled = { debugging = false })
            }
            FrameStats.summary(AppLanguages.localeOf(context))?.let {
                SettingsInfo(context.getString(R.string.settings_slow_frames, it))
            }
            TvListRow(stringResource(R.string.settings_clear_cache), modifier = Modifier.testTag("btnClearWebCache"),
                onClick = {
                    scope.launch {
                        Engines.clearCache(context)
                        Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    private fun applyBlockListUrl() {
        val source = listUrl.trim().ifEmpty { Config.DEFAULT_ADBLOCK_LIST_URL }
        if (config.blockListUrl.value != source) {
            config.blockListUpdatedAt = 0L
            config.blockListRetryAt = 0L
            config.blockListUrl.value = source
        }
    }

    private fun blockerState(): String {
        val loading = adBlocker.listLoading.value
        val lastUpdate = if (config.blockListUpdatedAt == 0L) context.getString(R.string.blocker_never)
            else SimpleDateFormat("hh:mm dd MMMM yyyy", Locale.getDefault()).format(Date(config.blockListUpdatedAt))
        val lists = if (config.blockListUrl.value == Config.DEFAULT_ADBLOCK_LIST_URL)
            adBlocker.configuredListNames().joinToString(" · ") else config.blockListUrl.value
        val state = if (loading) R.string.blocker_updating else when (adBlocker.updateResult.value) {
            AdBlocker.UpdateResult.UPDATED -> R.string.blocker_updated
            AdBlocker.UpdateResult.PARTIAL -> R.string.blocker_partial
            AdBlocker.UpdateResult.CACHED -> R.string.blocker_using_saved
            AdBlocker.UpdateResult.ERROR -> R.string.blocker_update_failed
            else -> null
        }
        val notRefreshed = adBlocker.unavailableLists.value
        return buildList {
            add("${context.getString(R.string.blocker_last_update)}: $lastUpdate")
            add(lists)
            state?.let { add(context.getString(it)) }
            if (!loading && notRefreshed.isNotEmpty())
                add(context.getString(R.string.blocker_not_refreshed, notRefreshed.joinToString(" · ")))
        }.joinToString("\n")
    }

    /** Applies the text values; called when the settings close. */
    fun save() {
        browserSettings.setSearchEngineURL(searchUrl)
        browserSettings.setHomePageProperties(homeMode, homeUrl)
        applyBlockListUrl()
    }

    private companion object {
        val VIDEO_HEIGHTS = intArrayOf(0, MediaPolicy.NO_LIMIT, 1080, 720, 480)
        const val POINTER_STEP = 5
    }
}
