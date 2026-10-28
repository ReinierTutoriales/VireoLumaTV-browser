package com.reiniertutoriales.vireolumatv.webengine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup

/**
 * The page engine of one tab. Its view is created on demand ([getOrCreateView]), lent to the
 * window while the tab is shown ([onAttachToWindow] / [onDetachFromWindow]) and may be destroyed
 * while the tab stays open ([trimMemory]); the tab then restores it from its saved state.
 */
interface WebEngine {
    /** The address shown now, or null without a live view. */
    val url: String?
    /** A custom user agent; null uses the engine's own. */
    var userAgentString: String?

    /** The navigation state to keep while the view is gone; null if there is none. */
    fun saveState(): Any?
    /** True only when navigation history came back; false asks the tab to load its address instead. */
    fun restoreState(savedInstanceState: Any): Boolean
    /** Reads back what [saveState] returned after it was stored as bytes. */
    fun stateFromBytes(bytes: ByteArray): Any?

    fun getView(): View?
    @Throws(Exception::class)
    fun getOrCreateView(activityContext: Context): View
    /** Shows the view in [parent], which must already hold no other page. */
    fun onAttachToWindow(callback: WebEngineWindowProviderCallback, parent: ViewGroup)
    /** Takes the view out of the window; [completely] also destroys it. */
    fun onDetachFromWindow(completely: Boolean, destroyTab: Boolean)
    /** Destroys the view if it is not on screen. */
    fun trimMemory()
    /** Whether [internalRepresentation] (a view handed to the window) belongs to this engine. */
    fun isSameSession(internalRepresentation: Any): Boolean

    fun loadUrl(url: String)
    fun reload()
    fun canGoBack(): Boolean
    fun goBack()
    fun canGoForward(): Boolean
    fun goForward()
    fun zoomIn()
    fun zoomOut()
    fun setNetworkAvailable(connected: Boolean)
    fun onResume()
    fun onPause()

    /** Whether this tab blocks ads, applied to the live view at once. */
    fun onUpdateAdblockSetting(newState: Boolean)
    /** Leaves page fullscreen (a video), restoring the browser's own controls. */
    fun hideFullscreenView()
    /** False hands the D-pad to the page's own focus navigation instead of the pointer. */
    fun setVirtualCursorMode(enabled: Boolean)

    // Media keys, applied to the page's main video.
    fun togglePlayback()
    fun stopPlayback()
    fun rewind()
    fun fastForward()

    /** Draws the page into [bitmap] (reused when given) for the tab preview; null when impossible. */
    suspend fun renderThumbnail(bitmap: Bitmap?): Bitmap?
    fun onFilePicked(resultCode: Int, data: Intent?)
    /** True when [requestCode] was this engine's request and the result was passed on. */
    fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean
}
