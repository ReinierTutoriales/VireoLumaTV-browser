package com.reiniertutoriales.vireolumatv.webengine

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate

/** The browser window as one tab's page sees it while the tab is shown. */
interface PageWindow {
    val activity: Activity

    // Navigation and page state

    /** Opens [url] in a tab after this one; [load] starts loading at once. */
    fun openInNewTab(url: String, load: Boolean): PageEngine?
    /** True keeps the page from loading [url] itself: it is not a web address. */
    fun interceptNavigation(url: String): Boolean
    fun onPageStarted(url: String?)
    fun onPageFinished(url: String?)
    /** A new history entry (not a reload). */
    fun onVisited(url: String)
    fun onProgress(percent: Int)
    fun onTitle(title: String)
    fun onIcon(icon: Bitmap)
    fun onScale(scale: Float)
    fun onCertificateError()
    /** The page's renderer died; the engine has already dropped its view. */
    fun onRendererGone()

    // What the page asks for

    /** Asks Android for [permissions]; the code returned identifies the answer. */
    fun requestPermissions(permissions: Array<String>): Int
    fun pickFile(intent: Intent): Boolean
    /** A download the page itself started; the window decides whether a recent press allows it. */
    fun onPageDownload(url: String, userAgent: String?, contentDisposition: String, mimeType: String?, length: Long)
    /** A download already named and allowed, such as a blob the page handed over. */
    fun download(download: Download)

    // Blocking

    val blocksAds: Boolean
    val blocksDialogs: Boolean
    fun isAd(url: Uri, requestType: String?, page: Uri): Boolean
    /** `data:` URL of the filter lists' `$redirect` resource for a blocked request, if any. */
    fun adRedirect(url: Uri, requestType: String?, page: Uri): String?
    /** A page-initiated navigation of this tab to a popunder network; true cancels it. */
    fun isTabUnderAd(url: Uri, page: Uri?): Boolean
    fun onAdsBlocked(count: Int)
    fun onDialogBlocked(newTab: Boolean)
    /** The view of a new window the page opens, or null when it is refused. */
    fun createWindow(dialog: Boolean, userGesture: Boolean): View?
    /** The page closed the window shown by [view]. */
    fun closeWindow(view: Any)

    // Fullscreen

    fun onEnterFullscreen()
    /** Fullscreen is over and page input restored; [restoreControls] brings back the browser's controls. */
    fun onExitFullscreen(restoreControls: Boolean)

    // Pointer

    fun openPointerMenu(cursor: CursorDrawerDelegate, target: PagePoint)
    fun openLinkActions(target: PagePoint)
}

/** A point on the page: where the pointer is, the page's address and the link under it, if any. */
class PagePoint(val pageUrl: String?, val link: String?, val x: Int, val y: Int)
