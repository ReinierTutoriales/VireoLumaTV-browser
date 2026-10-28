package com.reiniertutoriales.vireolumatv.activity

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity

/**
 * The browser window in private mode. It runs in a process of its own, whose WebView data
 * directory is separate; finishing it ends that process, so nothing of the session stays in memory.
 */
class IncognitoModeMainActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isFinishing && !config.incognitoModeHintSuppress) explainPrivateMode()
    }

    override fun onDestroy() {
        if (isFinishing) VireoLumaTVApp.instance.needToExitProcessAfterMainActivityFinish = true
        super.onDestroy()
    }

    /** What private mode keeps and forgets, until the user asks not to be told again. */
    private fun explainPrivateMode() {
        AlertDialog.Builder(this)
            .setTitle(R.string.incognito_mode)
            .setIcon(R.drawable.ic_incognito)
            .setMessage(R.string.incognito_mode_hint)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.don_t_show_again) { _, _ -> config.incognitoModeHintSuppress = true }
            .show()
    }
}
