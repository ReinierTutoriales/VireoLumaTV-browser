package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.Html
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.BuildConfig
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.IncognitoModeMainActivity
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.SettingsModel
import com.reiniertutoriales.vireolumatv.databinding.ViewSettingsVersionBinding
import com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater
import com.reiniertutoriales.vireolumatv.utils.Utils
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import com.reiniertutoriales.vireolumatv.utils.activity
import com.reiniertutoriales.vireolumatv.webengine.WebEngineFactory
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetTextI18n")
class VersionSettingsView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    companion object {
        private const val URL_VIREO_SOURCES = "https://github.com/ReinierTutoriales/VireoLumaTV-browser"
        // Required by the original license for modified binaries: credit the original sources
        private const val URL_TV_BRO_SOURCES = "https://github.com/truefedex/tv-bro"
        private const val URL_LICENSE =
            "https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/master/LICENSE.md"
        private const val URL_PRIVACY_POLICY =
            "https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/master/PRIVACY.md"
    }
    private var vb = ViewSettingsVersionBinding.inflate(LayoutInflater.from(getContext()), this, true)
    var config = AppContext.provideConfig()
    var settingsModel = ActiveModelsRepository.get(SettingsModel::class, activity!!)
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

        vb.tvProjectCredit.text = context.getString(R.string.vireo_project_credit)
        vb.tvProjectCredit.setOnClickListener {
            loadUrl(URL_VIREO_SOURCES)
        }

        vb.tvLink.text = Html.fromHtml("<p>" + context.getString(R.string.based_on_tv_bro_sources,
            "<u>$URL_TV_BRO_SOURCES</u>") + "</p>", Html.FROM_HTML_MODE_LEGACY)
        vb.tvLink.setOnClickListener {
            loadUrl(URL_TV_BRO_SOURCES)
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
            vb.btnUpdate.setOnClickListener { checkForUpdates() }
        } else {
            vb.btnUpdate.visibility = View.GONE
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

    private fun checkForUpdates() {
        val owner = activity as? LifecycleOwner ?: return
        vb.btnUpdate.isEnabled = false
        owner.lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { ReleaseUpdater.fetchNewerRelease() } }
            vb.btnUpdate.isEnabled = true
            result.onFailure {
                Utils.showToast(context, R.string.update_check_failed)
            }.onSuccess { release ->
                if (release == null) Utils.showToast(context, R.string.up_to_date) else confirmUpdate(release)
            }
        }
    }

    private fun confirmUpdate(release: ReleaseUpdater.Release) {
        AlertDialog.Builder(context)
            .setTitle(R.string.new_version_dialog_title)
            .setMessage(context.getString(R.string.new_version_available_s, release.version))
            .setPositiveButton(R.string.update) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndInstall(release: ReleaseUpdater.Release) {
        val owner = activity as? LifecycleOwner ?: return
        vb.btnUpdate.isEnabled = false
        owner.lifecycleScope.launch {
            val apk = runCatching { withContext(Dispatchers.IO) { ReleaseUpdater.download(context, release) } }
            vb.btnUpdate.isEnabled = true
            apk.onSuccess { ReleaseUpdater.install(context, it) }
                .onFailure { Utils.showToast(context, R.string.update_check_failed) }
        }
    }
}
