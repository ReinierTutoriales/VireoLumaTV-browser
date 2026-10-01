package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.Html
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ScrollView
import androidx.webkit.WebViewCompat
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.BuildConfig
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.activity.IncognitoModeMainActivity
import com.phlox.tvwebbrowser.activity.main.AutoUpdateModel
import com.phlox.tvwebbrowser.activity.main.MainActivity
import com.phlox.tvwebbrowser.activity.main.SettingsModel
import com.phlox.tvwebbrowser.databinding.ViewSettingsVersionBinding
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModelsRepository
import com.phlox.tvwebbrowser.utils.activity
import com.phlox.tvwebbrowser.webengine.WebEngineFactory
import androidx.core.net.toUri

@SuppressLint("SetTextI18n")
class VersionSettingsView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    companion object {
        private const val URL_SUPPORT_AUTHOR = "https://donatello.to/truefedex"
        // Required by the original license for modified binaries: credit the original sources
        private const val URL_TV_BRO_SOURCES = "https://github.com/truefedex/tv-bro"
        private const val URL_LICENSE =
            "https://raw.githubusercontent.com/truefedex/tv-bro/refs/heads/master/LICENSE.md"
        private const val URL_PRIVACY_POLICY =
            "https://raw.githubusercontent.com/truefedex/tv-bro/refs/heads/master/PRIVACY.md"
    }
    private var vb = ViewSettingsVersionBinding.inflate(LayoutInflater.from(getContext()), this, true)
    var config = AppContext.provideConfig()
    var settingsModel = ActiveModelsRepository.get(SettingsModel::class, activity!!)
    var autoUpdateModel = ActiveModelsRepository.get(AutoUpdateModel::class, activity!!)
    var callback: Callback? = null

    interface Callback {
        fun onNeedToCloseSettings()
    }

    init {
        vb.tvVersion.text = context.getString(R.string.version_s, BuildConfig.VERSION_NAME)

        val registeredEngines = WebEngineFactory.getProviders().joinToString("/") { it.name }
        vb.tvBuildFlavor.text = context.getString(
            R.string.build_flavor_s,
            BuildConfig.FLAVOR,
            registeredEngines
        )

        val engineVersion = "Engine: " + WebEngineFactory.getWebEngineVersionString()
        vb.tvWebViewVersion.text = engineVersion

        vb.tvLink.text = Html.fromHtml("<p>" + context.getString(R.string.based_on_tv_bro_sources,
            "<u>$URL_TV_BRO_SOURCES</u>") + "</p>", Html.FROM_HTML_MODE_LEGACY)
        vb.tvLink.setOnClickListener {
            loadUrl(URL_TV_BRO_SOURCES)
        }

        vb.tvSupportAuthor.text = context.getString(R.string.support_the_author)
        vb.tvSupportAuthor.setOnClickListener {
            loadUrl(URL_SUPPORT_AUTHOR)
        }

        vb.tvLicense.text = context.getString(R.string.view_license)
        vb.tvLicense.setOnClickListener {
            loadUrl(URL_LICENSE)
        }

        vb.tvPrivacy.text = context.getString(R.string.privacy_policy)
        vb.tvPrivacy.setOnClickListener {
            loadUrl(URL_PRIVACY_POLICY)
        }

        if (BuildConfig.BUILT_IN_AUTO_UPDATE) {
            vb.chkAutoCheckUpdates.isChecked = autoUpdateModel.needAutoCheckUpdates

            vb.chkAutoCheckUpdates.setOnCheckedChangeListener { _, isChecked ->
                autoUpdateModel.saveAutoCheckUpdates(isChecked)

                updateUIVisibility()
            }

            vb.spUpdateChannel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    val selectedChannel =
                        autoUpdateModel.updateChecker.versionCheckResult!!.availableChannels[position]
                    if (selectedChannel == config.updateChannel) return
                    config.updateChannel = selectedChannel
                    autoUpdateModel.checkUpdate(true) {
                        updateUIVisibility()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {

                }
            }

            vb.btnUpdate.setOnClickListener {
                callback?.onNeedToCloseSettings()
                autoUpdateModel.showUpdateDialogIfNeeded(activity as MainActivity, true)
            }

            updateUIVisibility()
        } else {
            vb.chkAutoCheckUpdates.visibility = View.INVISIBLE
        }
    }

    private fun loadUrl(url: String) {
        callback?.onNeedToCloseSettings()
        val activityClass = if (settingsModel.config.incognitoMode)
            IncognitoModeMainActivity::class.java else MainActivity::class.java
        val intent = Intent(activity, activityClass)
        intent.data = url.toUri()
        activity?.startActivity(intent)
    }

    private fun updateUIVisibility() {
        if (autoUpdateModel.updateChecker.versionCheckResult == null) {
            autoUpdateModel.checkUpdate(false) {
                if (autoUpdateModel.updateChecker.versionCheckResult != null) {
                    updateUIVisibility()
                }
            }
            return
        }

        vb.tvUpdateChannel.visibility = if (autoUpdateModel.needAutoCheckUpdates) View.VISIBLE else View.INVISIBLE
        vb.spUpdateChannel.visibility = if (autoUpdateModel.needAutoCheckUpdates) View.VISIBLE else View.INVISIBLE

        if (autoUpdateModel.needAutoCheckUpdates) {
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item,
                autoUpdateModel.updateChecker.versionCheckResult!!.availableChannels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            vb.spUpdateChannel.adapter = adapter
            val selected = autoUpdateModel.updateChecker.versionCheckResult!!.availableChannels.indexOf(config.updateChannel)
            if (selected != -1) {
                val tmp = vb.spUpdateChannel.onItemSelectedListener
                vb.spUpdateChannel.onItemSelectedListener = null
                vb.spUpdateChannel.setSelection(selected)
                vb.spUpdateChannel.onItemSelectedListener = tmp
            }
        }

        val hasUpdate = autoUpdateModel.updateChecker.hasUpdate()
        vb.tvNewVersion.visibility = if (autoUpdateModel.needAutoCheckUpdates && hasUpdate) View.VISIBLE else View.INVISIBLE
        vb.btnUpdate.visibility = if (autoUpdateModel.needAutoCheckUpdates && hasUpdate) View.VISIBLE else View.INVISIBLE
        if (hasUpdate) {
            vb.tvNewVersion.text = context.getString(R.string.new_version_available_s, autoUpdateModel.updateChecker.versionCheckResult!!.latestVersionName)
        }
    }
}