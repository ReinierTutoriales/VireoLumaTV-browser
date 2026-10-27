package com.reiniertutoriales.vireolumatv.utils

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.sign

/**
 * Turns remote and gamepad input into pointer commands for [Target].
 *
 * - D-pad and OK keys go to [Target.onKey] unchanged.
 * - The hat (the D-pad of most gamepads, AXIS_HAT_X/Y) becomes the same D-pad keys. A controller
 *   may report one press on both channels; the target still sees one DOWN and one UP.
 * - The left stick (AXIS_X/Y) becomes a proportional speed through [Target.onStick], outside the
 *   device's own dead zone. [stickEnabled] turns only the stick off; the hat is the D-pad.
 * - Motion from navigation sources is always reported as handled, so Android does not also
 *   synthesize D-pad keys from it. Pointer devices (mouse, touchpad) are never taken over.
 * - While [isTyping] returns true nothing is handled, so the keyboard owns the D-pad.
 */
class RemoteInput(
    private val target: Target,
    private val stickEnabled: () -> Boolean = { true },
    private val isTyping: () -> Boolean = { false },
) {
    interface Target {
        fun onKey(event: KeyEvent): Boolean
        fun onStick(x: Float, y: Float)
    }

    // The D-pad direction the target currently holds, and which channels report it held.
    private var heldDirection = 0
    private var heldByKey = false
    private var heldByHat = false
    private var stickX = 0f
    private var stickY = 0f
    private var primaryDown = false
    private var lastKey: Triple<Long, Int, Int>? = null

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code !in NavigationReservedShortcutKeyCodes.dpadNavigationKeys || isTyping()) return false
        // The same KeyEvent can be dispatched twice (window callback and view tree); act once.
        val signature = Triple(event.eventTime, event.action, code)
        if (signature == lastKey) return true
        lastKey = signature
        if (code == heldDirection) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                heldByKey = true
                if (event.repeatCount == 0 && heldByHat) return true // The hat already pressed it.
            } else if (event.action == KeyEvent.ACTION_UP) {
                clearHeld()
            }
        } else if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && code in DIRECTION_KEYS) {
            heldDirection = code
            heldByKey = true
            heldByHat = false
        }
        return target.onKey(event)
    }

    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isNavigationSource(event) || isTyping()) return false
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return true
        onHat(event, directionOf(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y)))
        if (stickEnabled()) {
            onStick(centered(event, MotionEvent.AXIS_X), centered(event, MotionEvent.AXIS_Y))
        } else {
            onStick(0f, 0f)
        }
        onPrimaryButton(event, event.buttonState and MotionEvent.BUTTON_PRIMARY != 0)
        return true
    }

    /** Drops held state without notifying the target, e.g. when the target itself was reset. */
    fun resetState() {
        clearHeld()
        stickX = 0f
        stickY = 0f
        primaryDown = false
        lastKey = null
    }

    private fun clearHeld() {
        heldDirection = 0
        heldByKey = false
        heldByHat = false
    }

    private fun onHat(event: MotionEvent, direction: Int) {
        if (heldByHat && direction != heldDirection) {
            heldByHat = false
            // A key still reporting the press will send its own UP.
            if (!heldByKey) {
                emit(event, KeyEvent.ACTION_UP, heldDirection)
                heldDirection = 0
            }
        }
        if (direction == 0) return
        if (direction == heldDirection) {
            heldByHat = true
        } else if (heldDirection == 0) {
            heldDirection = direction
            heldByHat = true
            emit(event, KeyEvent.ACTION_DOWN, direction)
        }
    }

    private fun onStick(x: Float, y: Float) {
        if (x == stickX && y == stickY) return
        stickX = x
        stickY = y
        target.onStick(x, y)
    }

    private fun onPrimaryButton(event: MotionEvent, down: Boolean) {
        if (down == primaryDown) return
        primaryDown = down
        emit(event, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
    }

    private fun emit(event: MotionEvent, action: Int, code: Int) {
        target.onKey(KeyEvent(event.downTime, event.eventTime, action, code, 0, 0, event.deviceId, 0, 0, event.source))
    }

    /** Axis value rescaled to 0..1 beyond the device's flat zone (at least [MIN_DEAD_ZONE]). */
    private fun centered(event: MotionEvent, axis: Int): Float {
        val value = event.getAxisValue(axis)
        val flat = event.device?.getMotionRange(axis, event.source)?.flat ?: 0f
        val deadZone = maxOf(flat, MIN_DEAD_ZONE)
        val magnitude = abs(value)
        if (magnitude <= deadZone) return 0f
        return value.sign * ((magnitude - deadZone) / (1f - deadZone)).coerceAtMost(1f)
    }

    companion object {
        /** Drift guard for sticks that report no or a tiny flat zone; an assumption to tune on devices. */
        private const val MIN_DEAD_ZONE = 0.1f
        private const val HAT_PRESSED = 0.5f

        private val DIRECTION_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP_LEFT, KeyEvent.KEYCODE_DPAD_UP_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
        )

        /** Gamepads, joysticks and D-pads, matched on every bit of the source (not one shared bit). */
        fun isNavigationSource(event: MotionEvent): Boolean =
            event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                event.isFromSource(InputDevice.SOURCE_DPAD)

        private fun directionOf(x: Float, y: Float): Int {
            val dx = if (x >= HAT_PRESSED) 1 else if (x <= -HAT_PRESSED) -1 else 0
            val dy = if (y >= HAT_PRESSED) 1 else if (y <= -HAT_PRESSED) -1 else 0
            return when (dx to dy) {
                -1 to 0 -> KeyEvent.KEYCODE_DPAD_LEFT
                1 to 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
                0 to -1 -> KeyEvent.KEYCODE_DPAD_UP
                0 to 1 -> KeyEvent.KEYCODE_DPAD_DOWN
                -1 to -1 -> KeyEvent.KEYCODE_DPAD_UP_LEFT
                1 to -1 -> KeyEvent.KEYCODE_DPAD_UP_RIGHT
                -1 to 1 -> KeyEvent.KEYCODE_DPAD_DOWN_LEFT
                1 to 1 -> KeyEvent.KEYCODE_DPAD_DOWN_RIGHT
                else -> 0
            }
        }
    }
}
