package com.reiniertutoriales.vireolumatv.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog

/** The manifest places this window and its WebView in the private process. */
class PrivateBrowserActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isFinishing) PrivateSessionNotice.attach(this)
    }

    override fun onDestroy() {
        if (isFinishing) VireoLumaTVApp.instance.exitProcessOnFinish = true
        super.onDestroy()
    }
}

/**
 * Explains private mode until the user chooses not to see it again. A notice open when the screen
 * is recreated comes back once, through the saved state; one already closed does not.
 */
object PrivateSessionNotice {
    private const val KEY = "private-session-notice"
    private const val SHOWING = "showing"

    /** Call from onCreate, after super.onCreate. Returns the notice when one is shown. */
    fun attach(activity: ComponentActivity): TvDialog? {
        val registry = activity.savedStateRegistry
        val restored = registry.consumeRestoredStateForKey(KEY)
        var dialog: TvDialog? = null
        registry.registerSavedStateProvider(KEY) { Bundle().apply { putBoolean(SHOWING, dialog?.isShowing == true) } }
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) dialog?.dismiss()
        })
        if (AppContext.config.privateHintSeen || (restored != null && !restored.getBoolean(SHOWING))) return null
        dialog = TvDialog.Builder(activity)
            .setTitle(R.string.menu_private)
            .setMessage(R.string.private_explained)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.action_dont_show_again) { _, _ -> AppContext.config.privateHintSeen = true }
            .show()
        return dialog
    }
}
