package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.ui.graphics.Color

/**
 * Surfaces and borders of the native controls. Focus (D-pad) is the strongest state; hover
 * (mouse or pointer remote) is subtler, as Google TV asks for pointer support.
 */
internal object TvControlColors {
    val Idle = Color.Black
    val Hovered = Color(0xFF151515)
    val Focused = Color(0xFF202020)
    val Pressed = Color(0xFF343434)
    /** The current item of a list (selected tab, chosen option) while not focused. */
    val Selected = Color(0xFF171717)
    val BorderIdle = Color.DarkGray
    val BorderHovered = Color.Gray
    val BorderFocused = Color.White

    fun surface(focused: Boolean, hovered: Boolean, pressed: Boolean = false) = when {
        pressed -> Pressed
        focused -> Focused
        hovered -> Hovered
        else -> Idle
    }

    fun border(focused: Boolean, hovered: Boolean) = when {
        focused -> BorderFocused
        hovered -> BorderHovered
        else -> BorderIdle
    }
}
