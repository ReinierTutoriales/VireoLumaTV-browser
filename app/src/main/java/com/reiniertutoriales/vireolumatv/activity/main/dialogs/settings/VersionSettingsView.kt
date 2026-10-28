package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.BuildConfig
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.IncognitoModeMainActivity
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater
import com.reiniertutoriales.vireolumatv.utils.activity
import com.reiniertutoriales.vireolumatv.webengine.Engines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.reiniertutoriales.vireolumatv.utils.toast

/** About page: identity, the credit the TV Bro license requires, versions, links and updates. */
class VersionSettingsView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    interface Callback {
        fun onNeedToCloseSettings()
    }

    var callback: Callback? = null
    private var updateButton: Button? = null

    init {
        val rows = SettingsRows(LayoutInflater.from(context).inflate(R.layout.view_settings_main, this, true)
            .findViewById<LinearLayout>(R.id.settingsList))
        rows.info(context.getString(R.string.app_name_short), sizeSp = 20f)
        rows.info(context.getString(R.string.web_browser_optimized_for_tvs))
        rows.value(context.getString(R.string.vireo_project_credit), null, R.id.rowProjectSource) { open(SOURCES_URL) }
        // Modified binaries must state in an About window that they use TV Bro's sources, with the link.
        rows.value(context.getString(R.string.based_on_tv_bro_sources, TV_BRO_SOURCES_URL), null, R.id.rowTvBroSource) {
            open(TV_BRO_SOURCES_URL)
        }
        rows.info(context.getString(R.string.version_s, BuildConfig.VERSION_NAME))
        rows.info(context.getString(R.string.build_flavor_s, BuildConfig.FLAVOR, "WebView"))
        rows.info(context.getString(R.string.web_engine_version_s, Engines.version))
        rows.value(context.getString(R.string.view_license), null, R.id.rowLicense) { open(LICENSE_URL) }
        rows.value(context.getString(R.string.privacy_policy), null, R.id.rowPrivacyPolicy) { open(PRIVACY_URL) }
        if (BuildConfig.BUILT_IN_AUTO_UPDATE) {
            updateButton = rows.button(R.string.update, R.id.btnCheckUpdate) { checkForUpdates() }
        }
    }

    /** Opens [url] in a browser tab of the current mode, closing the settings first. */
    private fun open(url: String) {
        callback?.onNeedToCloseSettings()
        val target = if (AppContext.provideConfig().incognitoMode)
            IncognitoModeMainActivity::class.java else MainActivity::class.java
        activity?.startActivity(Intent(activity, target).setData(url.toUri()))
    }

    /** Runs [work] with the update button disabled, so a second press cannot start it twice. */
    private fun whileUpdating(work: suspend () -> Unit) {
        val owner = activity as? LifecycleOwner ?: return
        updateButton?.isEnabled = false
        owner.lifecycleScope.launch {
            try { work() } finally { updateButton?.isEnabled = true }
        }
    }

    private fun checkForUpdates() = whileUpdating {
        runCatching { withContext(Dispatchers.IO) { ReleaseUpdater.fetchNewerRelease() } }
            .onFailure { context.toast(R.string.update_check_failed) }
            .onSuccess { release ->
                if (release == null) context.toast(R.string.up_to_date) else confirmUpdate(release)
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

    private fun downloadAndInstall(release: ReleaseUpdater.Release) = whileUpdating {
        runCatching { withContext(Dispatchers.IO) { ReleaseUpdater.download(context, release) } }
            .onSuccess { ReleaseUpdater.install(context, it) }
            .onFailure { context.toast(R.string.update_check_failed) }
    }

    private companion object {
        const val SOURCES_URL = "https://github.com/ReinierTutoriales/VireoLumaTV-browser"
        const val TV_BRO_SOURCES_URL = "https://github.com/truefedex/tv-bro"
        const val LICENSE_URL = "https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/master/LICENSE.md"
        const val PRIVACY_URL = "https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/master/PRIVACY.md"
    }
}
