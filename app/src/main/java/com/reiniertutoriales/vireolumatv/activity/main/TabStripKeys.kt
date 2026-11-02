package com.reiniertutoriales.vireolumatv.activity.main

import android.view.KeyEvent

/**
 * Down from the tab strip returns to the page while the overlay is open. Key presses reach the
 * window callback before the focused view, so this decision lives in one place for both paths.
 */
internal fun downLeavesTabStrip(
    overlayVisible: Boolean,
    tabsFocused: Boolean,
    keyCode: Int,
    action: Int,
    repeatCount: Int,
): Boolean = overlayVisible && tabsFocused && keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
    action == KeyEvent.ACTION_DOWN && repeatCount == 0
