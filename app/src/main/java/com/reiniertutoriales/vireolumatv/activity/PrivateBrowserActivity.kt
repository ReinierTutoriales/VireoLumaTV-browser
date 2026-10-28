package com.reiniertutoriales.vireolumatv.activity

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity

/** The manifest places this window and its WebView in the private process. */
class PrivateBrowserActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isFinishing && !config.incognitoModeHintSuppress &&
            supportFragmentManager.findFragmentByTag(PRIVATE_NOTICE) == null) {
            PrivateSessionNotice().show(supportFragmentManager, PRIVATE_NOTICE)
        }
    }

    override fun onDestroy() {
        if (isFinishing) VireoLumaTVApp.instance.needToExitProcessAfterMainActivityFinish = true
        super.onDestroy()
    }

    private companion object { const val PRIVATE_NOTICE = "private-session-notice" }
}

/** FragmentManager restores the notice across recreation instead of opening a second dialog. */
class PrivateSessionNotice : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext())
        .setTitle(R.string.menu_private)
        .setMessage(R.string.private_explained)
        .setPositiveButton(android.R.string.ok, null)
        .setNeutralButton(R.string.action_dont_show_again) { _, _ -> AppContext.config.incognitoModeHintSuppress = true }
        .create()
}
