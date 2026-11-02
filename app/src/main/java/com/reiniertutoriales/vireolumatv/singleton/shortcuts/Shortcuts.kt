package com.reiniertutoriales.vireolumatv.singleton.shortcuts

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.annotation.UiThread
import androidx.core.content.edit
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.webengine.MediaCommand
import java.util.EnumMap

/**
 * Remote key bindings for [RemoteShortcut]s: stored in the "shortcuts" preferences and matched against
 * key events. A short binding fires on release; a long-press binding fires once while the key is held,
 * and that press then fires nothing else.
 */
class Shortcuts internal constructor(private val prefs: SharedPreferences) {
    private val bindings = EnumMap<RemoteShortcut, KeyBinding>(RemoteShortcut::class.java)
    private val mainThread = Handler(Looper.getMainLooper())

    // The press being followed: its key code and modifiers, and whether its long press already fired.
    private var pressedCode: Int? = null
    private var pressedModifiers = 0
    private var longPressFired = false

    /** Whether a key event belongs to a shortcut press, and the shortcut it fires, if any. */
    data class KeyResult(val consumed: Boolean, val fire: RemoteShortcut? = null)

    init {
        for (shortcut in RemoteShortcut.entries) {
            val code = prefs.getInt(shortcut.prefsKey, shortcut.defaultKey)
            if (code != 0) bindings[shortcut] = KeyBinding(code, prefs.getInt(shortcut.prefsKey + MODIFIERS, 0),
                prefs.getBoolean(shortcut.prefsKey + LONG_PRESS, false))
        }
    }

    fun binding(shortcut: RemoteShortcut): KeyBinding? = bindings[shortcut]

    /** Stores [binding]; null clears it, and the cleared state survives restarts (no default comes back). */
    fun set(shortcut: RemoteShortcut, binding: KeyBinding?) {
        if (binding == null) bindings.remove(shortcut) else bindings[shortcut] = binding
        prefs.edit {
            putInt(shortcut.prefsKey, binding?.code ?: 0)
            putInt(shortcut.prefsKey + MODIFIERS, binding?.modifiers ?: 0)
            putBoolean(shortcut.prefsKey + LONG_PRESS, binding?.longPress ?: false)
        }
    }

    private fun matching(code: Int, modifiers: Int) =
        bindings.entries.filter { it.value.code == code && it.value.modifiers == modifiers }

    fun onKeyEvent(event: KeyEvent): KeyResult {
        val code = KeyBinding.codeOf(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    if (matching(code, event.modifiers).isEmpty()) return KeyResult(false)
                    pressedCode = code
                    pressedModifiers = event.modifiers
                    longPressFired = false
                    event.startTracking()
                    return KeyResult(true)
                }
                if (code != pressedCode) return KeyResult(false)
                if (event.isLongPress && !longPressFired) {
                    val long = matching(code, pressedModifiers).firstOrNull { it.value.longPress }?.key
                    if (long != null) {
                        longPressFired = true
                        return KeyResult(true, long)
                    }
                }
                return KeyResult(true)
            }
            KeyEvent.ACTION_UP -> {
                if (code != pressedCode) return KeyResult(false)
                pressedCode = null
                if (longPressFired) return KeyResult(true)
                return KeyResult(true, matching(code, pressedModifiers).firstOrNull { !it.value.longPress }?.key)
            }
        }
        return KeyResult(false)
    }

    /** Key events from the activity window; a fired shortcut runs on the next UI turn. */
    fun handle(event: KeyEvent, mainActivity: MainActivity, tab: BrowserTab?): Boolean {
        val result = onKeyEvent(event)
        result.fire?.let { shortcut -> mainThread.post { process(shortcut, mainActivity, tab) } }
        return result.consumed
    }

    @UiThread
    private fun process(shortcut: RemoteShortcut, mainActivity: MainActivity, tab: BrowserTab?) {
        val engine = tab?.engine
        when (shortcut) {
            RemoteShortcut.NAVIGATE_BACK -> mainActivity.navigateBack()
            RemoteShortcut.NAVIGATE_HOME -> mainActivity.navigate(Config.HOME_URL_ALIAS)
            RemoteShortcut.REFRESH_PAGE -> mainActivity.refresh()
            RemoteShortcut.PLAY_PAUSE -> engine?.media(MediaCommand.PLAY_PAUSE)
            RemoteShortcut.MEDIA_STOP -> engine?.media(MediaCommand.STOP)
            RemoteShortcut.MEDIA_REWIND -> engine?.media(MediaCommand.REWIND)
            RemoteShortcut.MEDIA_FAST_FORWARD -> engine?.media(MediaCommand.FAST_FORWARD)
        }
    }

    companion object {
        const val SHORTCUTS_FILE = "shortcuts"
        private const val MODIFIERS = "_mod"
        private const val LONG_PRESS = "_lp"

        @Volatile private var instance: Shortcuts? = null

        fun getInstance(): Shortcuts = instance ?: synchronized(this) {
            instance ?: Shortcuts(VireoLumaTVApp.instance.getSharedPreferences(SHORTCUTS_FILE, Context.MODE_PRIVATE))
                .also { instance = it }
        }
    }
}
