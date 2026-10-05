package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.AdblockModel
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.SettingsModel
import com.reiniertutoriales.vireolumatv.databinding.ViewSettingsMainBinding
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import com.reiniertutoriales.vireolumatv.utils.activity
import com.reiniertutoriales.vireolumatv.webengine.WebEngineFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainSettingsView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {
    private var vb = ViewSettingsMainBinding.inflate(LayoutInflater.from(getContext()), this, true)
    var settingsModel = ActiveModelsRepository.get(SettingsModel::class, activity!!)
    var adblockModel = ActiveModelsRepository.get(AdblockModel::class, activity!!)
    var config = AppContext.provideConfig()

    init {
        initHomePageAndSearchEngineConfigUI()

        initUAStringConfigUI(context)

        initAdBlockConfigUI()

        initThemeSettingsUI()

        initWebViewAlgorithmicDarkeningWithDarkUiModeUI()

        initAllowAutoplayMediaUI()

        initWebEngineDebugUI()

        initKeepScreenOnUI()

        initJoystickAxesNavigationUI()

        initVirtualCursorPhysicsSettingsUI()

        vb.btnClearWebCache.setOnClickListener {
            (activity as MainActivity).lifecycleScope.launch {
                WebEngineFactory.clearCache(context)
                Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initThemeSettingsUI() {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, context.resources.getStringArray(R.array.themes))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        vb.spTheme.adapter = adapter

        vb.spTheme.setSelection(config.theme.value.ordinal, false)

        vb.spTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (config.theme.value.ordinal == position) return
                config.theme.value = Config.Theme.values()[position]
                Toast.makeText(context, context.getString(R.string.need_restart), Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun initWebViewAlgorithmicDarkeningWithDarkUiModeUI() {
        vb.scWebViewAlgorithmicDarkeningWithDarkUiMode.isChecked =
            config.webviewUseAlgorithmicDarkeningWithDarkUiMode
        vb.scWebViewAlgorithmicDarkeningWithDarkUiMode.setOnCheckedChangeListener { _, isChecked ->
            config.webviewUseAlgorithmicDarkeningWithDarkUiMode = isChecked
        }
    }

    private fun initAllowAutoplayMediaUI() {
        vb.scAllowAutoplayMedia.isChecked = config.allowAutoplayMedia
        vb.scAllowAutoplayMedia.setOnCheckedChangeListener { _, isChecked ->
            config.allowAutoplayMedia = isChecked
        }
    }

    private fun initWebEngineDebugUI() {
        vb.scWebEngineDebug.isChecked = config.webEngineDebug
        vb.scWebEngineDebug.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                config.webEngineDebug = false
                return@setOnCheckedChangeListener
            }

            AlertDialog.Builder(context)
                .setTitle(R.string.warning)
                .setMessage(R.string.web_engine_debug_warning_message)
                .setPositiveButton(R.string.ok) { _, _ ->
                    config.webEngineDebug = true
                    Toast.makeText(context, context.getString(R.string.need_restart), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    vb.scWebEngineDebug.isChecked = false
                }
                .setOnCancelListener {
                    vb.scWebEngineDebug.isChecked = false
                }
                .show()
        }
    }

    private fun initKeepScreenOnUI() {
        vb.scKeepScreenOn.isChecked = settingsModel.keepScreenOn.value

        vb.scKeepScreenOn.setOnCheckedChangeListener { buttonView, isChecked ->
            settingsModel.keepScreenOn.value = isChecked
        }
    }

    private fun initJoystickAxesNavigationUI() {
        vb.scNavigateWithJoystickAxes.isChecked = !config.disableMotionAxesDpadNavigation
        vb.scNavigateWithJoystickAxes.setOnCheckedChangeListener { _, isChecked ->
            config.disableMotionAxesDpadNavigation = !isChecked
        }
    }

    private fun initVirtualCursorPhysicsSettingsUI() {
        val minP = Config.CURSOR_PHYSICS_PERCENT_MIN
        val maxP = Config.CURSOR_PHYSICS_PERCENT_MAX
        val range = maxP - minP
        vb.sbCursorMaxSpeed.max = range
        vb.sbCursorAcceleration.max = range
        fun refreshValueLabels() {
            vb.tvCursorMaxSpeedValue.text = context.getString(R.string.cursor_physics_percent, config.cursorMaxSpeedPercent)
            vb.tvCursorAccelerationValue.text = context.getString(R.string.cursor_physics_percent, config.cursorAccelerationPercent)
        }
        vb.sbCursorMaxSpeed.progress = config.cursorMaxSpeedPercent - minP
        vb.sbCursorAcceleration.progress = config.cursorAccelerationPercent - minP
        refreshValueLabels()
        vb.sbCursorMaxSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorMaxSpeedPercent = minP + progress
                refreshValueLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        vb.sbCursorAcceleration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorAccelerationPercent = minP + progress
                refreshValueLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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

    private fun initAdBlockConfigUI() {
        vb.scAdblock.isChecked = config.adBlockEnabled
        vb.etAdBlockerListUrl.setText(config.adBlockListURL.value)
        vb.llAdblock.setOnClickListener {
            vb.scAdblock.isChecked = !vb.scAdblock.isChecked
            config.adBlockEnabled = vb.scAdblock.isChecked
            (activity as MainActivity).refreshAdblockSettings()
            vb.llAdBlockerDetails.visibility = if (vb.scAdblock.isChecked) VISIBLE else GONE
        }
        vb.llAdBlockerDetails.visibility = if (config.adBlockEnabled) VISIBLE else GONE

        vb.btnAdBlockerUpdate.setOnClickListener {
            if (adblockModel.clientLoading.value) return@setOnClickListener
            saveAdBlockListUrl()
            adblockModel.loadAdBlockList(true)
        }

        updateAdBlockInfo()
    }

    private fun saveAdBlockListUrl() {
        val value = vb.etAdBlockerListUrl.text.toString().trim()
        val source = value.ifEmpty { Config.DEFAULT_ADBLOCK_LIST_URL }
        if (config.adBlockListURL.value != source) {
            config.adBlockListLastUpdate = 0L
            config.adBlockListNextRetry = 0L
            config.adBlockListURL.value = source
        }
    }

    private fun updateAdBlockInfo() {
        val dateFormat = SimpleDateFormat("hh:mm dd MMMM yyyy", Locale.getDefault())
        val lastUpdate = if (config.adBlockListLastUpdate == 0L)
            context.getString(R.string.never) else
            dateFormat.format(Date(config.adBlockListLastUpdate))
        val lists = if (config.adBlockListURL.value == Config.DEFAULT_ADBLOCK_LIST_URL)
            "EasyList · EasyPrivacy · EasyList Spanish" else config.adBlockListURL.value
        val infoText = "${context.getString(R.string.last_update)}: $lastUpdate\n$lists"
        val loadingAdBlockList = adblockModel.clientLoading.value
        val status = when {
            loadingAdBlockList -> R.string.adblock_updating
            adblockModel.updateResult.value == AdblockModel.UpdateResult.UPDATED -> R.string.adblock_updated
            adblockModel.updateResult.value == AdblockModel.UpdateResult.PARTIAL -> R.string.adblock_partial_update
            adblockModel.updateResult.value == AdblockModel.UpdateResult.CACHED -> R.string.adblock_using_saved
            adblockModel.updateResult.value == AdblockModel.UpdateResult.ERROR -> R.string.adblock_update_error
            else -> null
        }
        vb.tvAdBlockerListInfo.text = infoText + (status?.let { "\n" + context.getString(it) } ?: "")
        // Keep the focused TV button in place; the click handler rejects duplicate refreshes.
        vb.btnAdBlockerUpdate.isEnabled = true
        vb.btnAdBlockerUpdate.visibility = View.VISIBLE
        vb.pbAdBlockerListLoading.visibility = if (loadingAdBlockList) View.VISIBLE else View.INVISIBLE
    }

    private fun initUAStringConfigUI(context: Context) {
        if (config.userAgentString.value?.contains("TV Bro/1.0 ") == true) {//legacy ua string - now default one should be used
            config.userAgentString.value = null
        }
        val selected = if (config.userAgentString.value == null) {
            0
        } else {
            settingsModel.uaStrings.indexOf(config.userAgentString.value ?: "")
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, settingsModel.userAgentStringTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spTitles.adapter = adapter

        if (selected != -1) {
            vb.spTitles.setSelection(selected, false)
            vb.etUAString.setText(settingsModel.uaStrings[selected])
        } else {
            vb.spTitles.setSelection(settingsModel.userAgentStringTitles.size - 1, false)
            vb.llUAString.visibility = View.VISIBLE
            vb.etUAString.setText(config.userAgentString.value ?: "")
            vb.etUAString.requestFocus()
        }
        vb.spTitles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position == settingsModel.userAgentStringTitles.size - 1 && vb.llUAString.visibility == View.GONE) {
                    vb.llUAString.visibility = View.VISIBLE
                    vb.llUAString.startAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in))
                    vb.etUAString.requestFocus()
                }
                vb.etUAString.setText(settingsModel.uaStrings[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>) {

            }
        }
    }

    private fun initHomePageAndSearchEngineConfigUI() {
        var selected = 0
        if ("" != config.searchEngineURL.value) {
            selected = Config.SearchEnginesURLs.indexOf(config.searchEngineURL.value)
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, Config.SearchEnginesTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        vb.spEngine.adapter = adapter

        if (selected != -1) {
            vb.spEngine.setSelection(selected)
            vb.etUrl.setText(Config.SearchEnginesURLs[selected])
        } else {
            vb.spEngine.setSelection(Config.SearchEnginesTitles.size - 1)
            vb.llURL.visibility = View.VISIBLE
            vb.etUrl.setText(config.searchEngineURL.value)
            vb.etUrl.requestFocus()
        }
        vb.spEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position == (Config.SearchEnginesTitles.size - 1)) {
                    if (vb.llURL.visibility == View.GONE) {
                        vb.llURL.visibility = View.VISIBLE
                        vb.llURL.startAnimation(
                            AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
                        )
                    }
                    vb.etUrl.setText(config.searchEngineURL.value)
                    vb.etUrl.requestFocus()
                    return
                } else {
                    vb.llURL.visibility = View.GONE
                    vb.etUrl.setText(Config.SearchEnginesURLs[position])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val homePageSpinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, context.resources.getStringArray(R.array.home_page_modes))
        homePageSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spHomePage.adapter = homePageSpinnerAdapter
        vb.spHomePage.setSelection(settingsModel.homePageMode.ordinal)

        vb.spHomePage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val homePageMode = Config.HomePageMode.entries[position]
                vb.llCustomHomePage.visibility = if (homePageMode == Config.HomePageMode.CUSTOM) View.VISIBLE else View.GONE
                vb.llHomePageLinksMode.visibility = if (homePageMode == Config.HomePageMode.HOME_PAGE) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val homePageLinksSpinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, context.resources.getStringArray(R.array.home_page_links_modes))
        homePageLinksSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spHomePageLinks.adapter = homePageLinksSpinnerAdapter
        vb.spHomePageLinks.setSelection(settingsModel.homePageLinksMode.ordinal)

        vb.etCustomHomePageUrl.setText(settingsModel.homePage)
    }

    fun save() {
        val customSearchEngineUrl = vb.etUrl.text.toString()
        settingsModel.setSearchEngineURL(customSearchEngineUrl)

        val homePageMode = Config.HomePageMode.entries[vb.spHomePage.selectedItemPosition]
        val customHomePageURL = vb.etCustomHomePageUrl.text.toString()
        val homePageLinksMode = Config.HomePageLinksMode.entries[vb.spHomePageLinks.selectedItemPosition]
        settingsModel.setHomePageProperties(homePageMode, customHomePageURL, homePageLinksMode)

        val userAgent = vb.etUAString.text.toString().trim(' ')
        config.userAgentString.value = userAgent.ifEmpty { null }
        saveAdBlockListUrl()
    }
}
