package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.reiniertutoriales.vireolumatv.activity.main.compose.LocalChromeMetrics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.BuildConfig
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.PrivateBrowserActivity
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvListRow
import com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater
import com.reiniertutoriales.vireolumatv.utils.toast
import com.reiniertutoriales.vireolumatv.webengine.Engines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Application identity, license, versions, links and updates. [onDone] closes the settings. */
class AboutPage(private val context: Context, private val scope: CoroutineScope, private val onDone: () -> Unit) {
    /** True while an update check, download or install runs; the button ignores presses meanwhile. */
    var updating by mutableStateOf(false)
        private set
    private var pendingUpdate: File? = null
    private var updateDialog: TvDialog? = null

    @Composable
    fun Content() = Column {
        val metrics = LocalChromeMetrics.current
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
            Image(painterResource(R.drawable.brand_logo), contentDescription = null,
                modifier = Modifier.size(metrics.control * 2))
            Column {
                SettingsInfo(stringResource(R.string.app_name), sizeSp = 20)
                SettingsInfo(stringResource(R.string.about_tagline))
            }
        }
        SettingsValue(stringResource(R.string.about_source), null, "rowProjectSource") { open(SOURCES_URL) }
        SettingsInfo(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
        SettingsInfo(stringResource(R.string.about_build, BuildConfig.FLAVOR, "WebView"))
        SettingsInfo(stringResource(R.string.about_engine, Engines.version))
        SettingsValue(stringResource(R.string.about_license), null, "rowLicense") { open(LICENSE_URL) }
        SettingsValue(stringResource(R.string.about_privacy), null, "rowPrivacyPolicy") { open(PRIVACY_URL) }
        if (BuildConfig.BUILT_IN_AUTO_UPDATE) {
            // Stays enabled while busy so focus does not jump; whileUpdating ignores the extra press.
            TvListRow(stringResource(R.string.action_update),
                supporting = if (updating) stringResource(R.string.update_in_progress) else null,
                modifier = Modifier.testTag("btnCheckUpdate").semantics { liveRegion = LiveRegionMode.Polite },
                onClick = ::checkForUpdates)
        }
    }

    /** Opens [url] in a browser tab of the current mode, closing the settings first. */
    private fun open(url: String) {
        onDone()
        val target = if (AppContext.config.privateBrowsing)
            PrivateBrowserActivity::class.java else MainActivity::class.java
        context.startActivity(Intent(context, target).setData(url.toUri()))
    }

    /** Runs [work] while [updating], so a second press cannot start it twice. */
    internal fun whileUpdating(work: suspend () -> Unit) {
        if (updating) return
        updating = true
        scope.launch {
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                context.toast(R.string.update_failed)
            } finally { updating = false }
        }
    }

    internal fun checkForUpdates() = whileUpdating {
        // Returning from unknown-source settings should retry the verified file, not download it again.
        pendingUpdate?.takeIf { it.isFile }?.let {
            ReleaseUpdater.install(context, it)
            return@whileUpdating
        }
        pendingUpdate = null
        val release = withContext(Dispatchers.IO) { ReleaseUpdater.fetchNewerRelease() }
        if (release == null) context.toast(R.string.update_none) else confirmUpdate(release)
    }

    /** For tests: a file downloaded and verified earlier, as after returning from the installer. */
    internal fun setPendingUpdate(file: File?) { pendingUpdate = file }

    internal fun close() {
        updateDialog?.dismiss()
        updateDialog = null
    }

    private fun confirmUpdate(release: ReleaseUpdater.Release) {
        updateDialog = TvDialog.Builder(context)
            .setTitle(R.string.update_available_title)
            .setMessage(context.getString(R.string.update_available_message, release.version))
            .setPositiveButton(R.string.action_update) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { updateDialog = null }
            .show()
    }

    private fun downloadAndInstall(release: ReleaseUpdater.Release) = whileUpdating {
        val apk = withContext(Dispatchers.IO) { ReleaseUpdater.download(context, release) }
        pendingUpdate = apk
        ReleaseUpdater.install(context, apk)
    }

    private companion object {
        const val SOURCES_URL = "https://github.com/ReinierTutoriales/VireoLumaTV-browser"
        const val LICENSE_URL = "https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/master/LICENSE.md"
        const val PRIVACY_URL = "https://github.com/ReinierTutoriales/VireoLumaTV-browser/blob/master/PRIVACY.md"
    }
}
