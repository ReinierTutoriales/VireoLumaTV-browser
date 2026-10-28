package com.reiniertutoriales.vireolumatv.singleton.shortcuts

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import androidx.annotation.StringRes
import com.reiniertutoriales.vireolumatv.R

/** Browser actions a remote key can be bound to. [prefsKey] names the stored binding; keep it stable. */
@SuppressLint("InlinedApi") // Key codes are plain ints; a key an older system lacks never arrives.
enum class Shortcut(@StringRes val titleResId: Int, val prefsKey: String, val defaultKey: Int) {
    NAVIGATE_BACK(R.string.navigate_back, "shortcut_nav_back", 0),
    NAVIGATE_HOME(R.string.navigate_home, "shortcut_nav_home", 0),
    REFRESH_PAGE(R.string.refresh_page, "shortcut_refresh_page", KeyEvent.KEYCODE_REFRESH),
    PLAY_PAUSE(R.string.play_pause, "shortcut_play_pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
    MEDIA_STOP(R.string.media_stop, "shortcut_media_stop", KeyEvent.KEYCODE_MEDIA_STOP),
    MEDIA_REWIND(R.string.media_rewind, "shortcut_media_rewind", KeyEvent.KEYCODE_MEDIA_REWIND),
    MEDIA_FAST_FORWARD(R.string.media_fast_forward, "shortcut_media_fast_forward", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
}

/** The key press that triggers a shortcut. [code] comes from [codeOf], so recording and matching agree. */
data class KeyBinding(val code: Int, val modifiers: Int = 0, val longPress: Boolean = false) {

    /** Readable form such as "Pulsación larga CTRL+Media play pause". */
    fun describe(context: Context): String = buildString {
        if (longPress) append(context.getString(R.string.long_press)).append(' ')
        if (modifiers and KeyEvent.META_ALT_ON != 0) append("ALT+")
        if (modifiers and KeyEvent.META_CTRL_ON != 0) append("CTRL+")
        if (modifiers and KeyEvent.META_SHIFT_ON != 0) append("SHIFT+")
        append(keyName(context, code))
    }

    companion object {
        /** Keys the system has no key code for are told apart by their scan code. */
        fun codeOf(event: KeyEvent): Int =
            if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN) event.keyCode else event.scanCode

        private fun keyName(context: Context, code: Int): String {
            val symbolic = KeyEvent.keyCodeToString(code)
            if (!symbolic.startsWith(KEYCODE_PREFIX)) return context.getString(R.string.key_code_d, code)
            return symbolic.removePrefix(KEYCODE_PREFIX).lowercase().replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }

        private const val KEYCODE_PREFIX = "KEYCODE_"
    }
}
