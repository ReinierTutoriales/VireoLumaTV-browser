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
import com.reiniertutoriales.vireolumatv.activity.PrivateBrowserActivity
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater
import com.reiniertutoriales.vireolumatv.utils.activity
import com.reiniertutoriales.vireolumatv.webengine.Engines
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.reiniertutoriales.vireolumatv.utils.toast

/** Application identity, license, versions, links and updates. */
class AboutPage @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    interface Callback {
        fun onSettingsDone()
    }

    var callback: Callback? = null
    private var updateButton: Button? = null
    private var pendingUpdate: File? = null

    init {
        val rows = SettingsRows(LayoutInflater.from(context).inflate(R.layout.settings_general, this, true)
            .findViewById<LinearLayout>(R.id.settingsList))
        rows.info(context.getString(R.string.app_name), sizeSp = 20f)
        rows.info(context.getString(R.string.about_tagline))
        rows.value(context.getString(R.string.about_tvbro_credit), null) { open(TVBRO_SOURCE_URL) }
        rows.value(context.getString(R.string.about_source), null, R.id.rowProjectSource) { open(SOURCES_URL) }
        rows.info(context.getString(R.string.about_version, BuildConfig.VERSION_NAME))
        rows.info(context.getString(R.string.about_build, BuildConfig.FLAVOR, "WebView"))
        rows.info(context.getString(R.string.about_engine, Engines.version))
        rows.value(context.getString(R.string.about_license), null, R.id.rowLicense) { open(LICENSE_URL) }
        rows.value(context.getString(R.string.about_privacy), null, R.id.rowPrivacyPolicy) { open(PRIVACY_URL) }
        if (BuildConfig.BUILT_IN_AUTO_UPDATE) {
            updateButton = rows.button(R.string.action_update, R.id.btnCheckUpdate) { checkForUpdates() }
        }
    }

    /** Opens [url] in a browser tab of the current mode, closing the settings first. */
    private fun open(url: String) {
        callback?.onSettingsDone()
        val target = if (AppContext.config.privateBrowsing)
            PrivateBrowserActivity::class.java else MainActivity::class.java
        activity?.startActivity(Intent(activity, target).setData(url.toUri()))
    }

    /** Runs [work] with the update button disabled, so a second press cannot start it twice. */
    private fun whileUpdating(work: suspend () -> Unit) {
        val owner = activity as? LifecycleOwner ?: return
        updateButton?.isEnabled = false
        owner.lifecycleScope.launch {
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                context.toast(R.string.update_failed)
            } finally { updateButton?.isEnabled = true }
        }
    }

    private fun checkForUpdates() = whileUpdating {
        // Returning from unknown-source settings should retry the verified file, not download it again.
        pendingUpdate?.takeIf { it.isFile }?.let {
            ReleaseUpdater.install(context, it)
            return@whileUpdating
        }
        pendingUpdate = null
        val release = withContext(Dispatchers.IO) { ReleaseUpdater.fetchNewerRelease() }
        if (release == null) context.toast(R.string.update_none) else confirmUpdate(release)
    }

    private fun confirmUpdate(release: ReleaseUpdater.Release) {
        AlertDialog.Builder(context)
            .setTitle(R.string.update_available_title)
            .setMessage(context.getString(R.string.update_available_message, release.version))
            .setPositiveButton(R.string.action_update) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndInstall(release: ReleaseUpdater.Release) = whileUpdating {
        val apk = withContext(Dispatchers.IO) { ReleaseUpdater.download(context, release) }
        pendingUpdate = apk
        ReleaseUpdater.install(context, apk)
    }

    private companion object {
        private const val TVBRO_SOURCE_URL = "https://github.com/truefedex/tv-bro"
        const val SOURCES_URL = "https://github.com/ReinierTutoriales/VireoLumaTV-browser"
        const val LICENSE_URL = "https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/master/LICENSE.md"
        const val PRIVACY_URL = "https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/master/PRIVACY.md"
    }
}
