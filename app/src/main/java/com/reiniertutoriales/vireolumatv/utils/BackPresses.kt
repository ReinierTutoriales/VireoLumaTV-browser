package com.reiniertutoriales.vireolumatv.utils

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Converts remote and controller Back gestures into one navigation action per press. */
class BackPresses(
    private val onBackKey: () -> Unit,
    private val longPressTimeoutMs: Int = 500,
    var holdBGoesBack: Boolean = false,
) {
    init {
        require(longPressTimeoutMs > 0)
    }

    private enum class Channel { SYSTEM, KEY, MOTION }

    private data class Press(
        val code: Int,
        val startedAt: Long,
        val longOnly: Boolean,
        val ignored: Boolean,
        var fired: Boolean = false,
    )

    private var keyPress: Press? = null
    private var motionPress: Press? = null
    private val recentStarts = LongArray(Channel.entries.size) { -1L }

    private fun isController(source: Int): Boolean =
        (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

    private fun isDuplicate(channel: Channel, time: Long): Boolean =
        Channel.entries.any { other ->
            other != channel && recentStarts[other.ordinal].let { previous ->
                previous >= 0 && time - previous in 0L..CROSS_CHANNEL_WINDOW_MS
            }
        }

    private fun begin(channel: Channel, code: Int, time: Long, longOnly: Boolean): Press {
        val ignored = isDuplicate(channel, time)
        if (!ignored) recentStarts[channel.ordinal] = time
        return Press(code, time, longOnly, ignored)
    }

    fun sendSystemBack(): Boolean {
        val time = SystemClock.uptimeMillis()
        if (isDuplicate(Channel.SYSTEM, time)) return false
        recentStarts[Channel.SYSTEM.ordinal] = time
        emit()
        return true
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in BACK_KEYS) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0 || keyPress?.code != event.keyCode) {
                    keyPress = begin(
                        Channel.KEY, event.keyCode, event.eventTime,
                        holdBGoesBack && isController(event.source),
                    )
                }
                val press = keyPress ?: return true
                if (press.longOnly && !press.ignored && !press.fired &&
                    (event.isLongPress || event.repeatCount > 0 ||
                        event.flags and KeyEvent.FLAG_LONG_PRESS != 0)) {
                    press.fired = true
                    emit()
                }
            }
            KeyEvent.ACTION_UP -> {
                val press = keyPress
                keyPress = null
                if (press?.code == event.keyCode && !press.ignored && !press.longOnly &&
                    !event.isCanceled) emit()
            }
        }
        return true
    }

    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            val wasHeld = motionPress != null
            motionPress = null
            return wasHeld
        }
        val buttons = event.buttonState
        val controller = isController(event.source)
        val held = buttons and MotionEvent.BUTTON_BACK != 0 ||
            controller && buttons and MotionEvent.BUTTON_SECONDARY != 0
        if (!held && motionPress == null) return false

        val press = motionPress
        if (held && press == null) {
            motionPress = begin(
                Channel.MOTION, MotionEvent.BUTTON_BACK, event.eventTime,
                holdBGoesBack && controller,
            )
        } else if (held && press != null && press.longOnly && !press.ignored && !press.fired &&
            event.eventTime - press.startedAt >= longPressTimeoutMs) {
            press.fired = true
            emit()
        } else if (!held && press != null) {
            motionPress = null
            if (!press.ignored && !press.longOnly) emit()
        }
        return true
    }

    fun resetState() {
        keyPress = null
        motionPress = null
        recentStarts.fill(-1L)
    }

    private fun emit() {
        try {
            onBackKey()
        } catch (error: RuntimeException) {
            Log.e("BackPresses", "Back handler failed", error)
        }
    }

    companion object {
        // Only correlate deliveries from the same physical gesture; consecutive Back presses work.
        private const val CROSS_CHANNEL_WINDOW_MS = 150L

        /** Keys that mean Back: the remote's, a gamepad's B and a keyboard's Escape. */
        val BACK_KEYS: Set<Int> = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE)
    }
}
