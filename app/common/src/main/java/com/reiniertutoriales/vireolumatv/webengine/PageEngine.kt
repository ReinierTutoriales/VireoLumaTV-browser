package com.reiniertutoriales.vireolumatv.webengine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup

/**
 * The page of one tab. Its [view] is created on demand, lent to the window while the tab is shown
 * ([attach] / [detach]) and may be released while the tab stays open; the tab then brings it back
 * from its saved history.
 */
interface PageEngine {
    /** The address shown now, or null without a live view. */
    val url: String?
    /** A custom user agent; null uses the engine's own. */
    var userAgent: String?
    val view: View?
    val canGoBack: Boolean
    val canGoForward: Boolean

    /** The view, created now if needed; throws when the device has no usable engine. */
    fun createView(context: Context): View
    /** Shows the view in [parent], which must hold no other page, and talks to [window] until [detach]. */
    fun attach(window: PageWindow, parent: ViewGroup)
    /** Takes the view out of the window; [destroyView] also releases it. */
    fun detach(destroyView: Boolean)
    /** Releases the view if it is not on screen. */
    fun releaseHiddenView()
    /** Whether [view] (one handed to the window) is this engine's. */
    fun ownsView(view: Any): Boolean

    /** The navigation history to keep while the view is gone; null if there is none. */
    fun saveHistory(): Any?
    /** True only when the history came back; false asks the tab to load its address instead. */
    fun restoreHistory(history: Any): Boolean
    /** Reads back what [saveHistory] returned after it was stored as bytes. */
    fun readHistory(bytes: ByteArray): Any?

    fun load(url: String)
    fun reload()
    fun back()
    fun forward()
    /** One zoom step, [closer] or farther. */
    fun zoom(closer: Boolean)
    fun setOnline(online: Boolean)
    fun resume()
    fun pause()

    /** Whether this tab blocks ads, applied to the live view at once. */
    fun setAdBlocking(enabled: Boolean)
    /** Leaves page fullscreen (a video) and restores the browser's controls. */
    fun closeFullscreen()
    /** False hands the D-pad to the page's own focus navigation instead of the pointer. */
    fun setPointerMode(enabled: Boolean)
    /** A media key, applied to the page's main video. */
    fun media(command: MediaCommand)

    /** A picture of the page for the tab preview, drawn into [reuse] when it fits; null when impossible. */
    suspend fun preview(reuse: Bitmap?): Bitmap?
    fun onFilePicked(resultCode: Int, data: Intent?)
    /** True when [requestCode] was this engine's request and the answer was passed on. */
    fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean
}

enum class MediaCommand { PLAY_PAUSE, STOP, REWIND, FAST_FORWARD }
