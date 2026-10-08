package com.reiniertutoriales.vireolumatv.model

import android.net.Uri
import java.util.concurrent.atomic.AtomicInteger

/**
 * Screens the navigations of a window opened by a page (window.open / target=_blank) before it
 * is shown. [isAd] runs on WebView worker threads; [onBlocked]/[onAllowed] are invoked at most
 * once in total and must post to the UI thread themselves.
 */
class PopupGuard(
    private val isAd: (Uri) -> Boolean,
    private val onBlocked: () -> Unit,
    private val onAllowed: () -> Unit
) {
    private val state = AtomicInteger(PENDING)
    @Volatile var active = true
        private set

    val isBlocked: Boolean get() = state.get() == BLOCKED

    /** @return true if this main-frame navigation must be cancelled. */
    fun onNavigation(url: Uri): Boolean {
        // A blocked window is being closed: nothing may load in it any more.
        if (state.get() == BLOCKED) return true
        if (!active) return false
        if (url.scheme != "http" && url.scheme != "https") return false
        if (isAd(url)) {
            val previous = state.getAndSet(BLOCKED)
            active = false
            if (previous != BLOCKED) onBlocked()
            return true
        }
        allow()
        return false
    }

    /** Show the window on its first safe navigation. */
    fun allow() {
        if (state.compareAndSet(PENDING, ALLOWED)) onAllowed()
    }

    /**
     * The caller's timeout: a window that never navigated (about:blank written by script, or a
     * destination still undecided) is a popunder pattern, so it is closed rather than shown.
     */
    fun expire() {
        if (state.compareAndSet(PENDING, BLOCKED)) {
            active = false
            onBlocked()
        }
    }

    /** A page finished in the shown window: stop screening (ordinary browsing from here on). */
    fun onPageFinished(url: String?) {
        if (state.get() == ALLOWED && url != null &&
            (url.startsWith("http://") || url.startsWith("https://"))) active = false
    }

    private companion object {
        const val PENDING = 0
        const val ALLOWED = 1
        const val BLOCKED = 2
    }
}
