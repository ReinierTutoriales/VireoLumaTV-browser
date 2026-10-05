package com.reiniertutoriales.vireolumatv.webengine.webview

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Records real user activations (remote OK/Enter, gamepad A, physical touch or mouse click) as
 * seen by the Activity window, before any page code runs. Web pages cannot synthesize these
 * events, so a recent activation is the authorization signal for privileged bridge actions.
 *
 * An activation is single-use: [consume] clears it, so one press allows at most one action.
 */
internal class UserActivationTracker(private val clock: () -> Long) {
    @Volatile private var lastActivationMs = NONE

    fun mark(now: Long = clock()) {
        lastActivationMs = now
    }

    @Synchronized
    fun consume(windowMs: Long = WINDOW_MS, now: Long = clock()): Boolean {
        val last = lastActivationMs
        lastActivationMs = NONE
        if (last == NONE) return false
        val age = now - last
        return age in 0..windowMs
    }

    companion object {
        const val WINDOW_MS = 2_000L
        private const val NONE = Long.MIN_VALUE
    }
}

internal object UserActivation {
    private val tracker = UserActivationTracker { SystemClock.uptimeMillis() }

    fun onKeyEvent(event: KeyEvent) {
        if (!isActivationKey(event.keyCode)) return
        val isPress = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        val isRelease = event.action == KeyEvent.ACTION_UP && !event.isCanceled
        if (isPress || isRelease) tracker.mark()
    }

    fun onTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_UP) tracker.mark()
    }

    fun consume(): Boolean = tracker.consume()

    private fun isActivationKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_SELECT -> true
        else -> false
    }
}
