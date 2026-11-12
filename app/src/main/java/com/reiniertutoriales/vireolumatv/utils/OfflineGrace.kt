package com.reiniertutoriales.vireolumatv.utils

import android.os.Handler

/**
 * Tells pages they are offline only after [graceMs] without a network; back online at once.
 * A Wi-Fi re-association or a hand-over between default networks then does not fire the HTML
 * `offline` event, which makes players such as YouTube stop; a real loss is still reported.
 * Main thread only.
 */
internal class OfflineGrace(private val handler: Handler, private val graceMs: Long, private val apply: (online: Boolean) -> Unit) {
    private val goOffline = Runnable { apply(false) }

    fun report(online: Boolean) {
        handler.removeCallbacks(goOffline)
        if (online) apply(true) else handler.postDelayed(goOffline, graceMs)
    }
}
