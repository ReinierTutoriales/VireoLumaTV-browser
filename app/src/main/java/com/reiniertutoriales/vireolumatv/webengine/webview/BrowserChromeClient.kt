package com.reiniertutoriales.vireolumatv.webengine.webview

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * The page's requests to the browser window: script dialogs (cancelled while dialogs are
 * blocked), fullscreen video, progress and title, permissions, files, icons and new windows.
 */
internal class BrowserChromeClient(private val view: WebViewEx) : WebChromeClient() {
    private val window get() = view.window
    private val engine get() = view.engine
    private var fullscreen: CustomViewCallback? = null
    private var filePicked: ValueCallback<Array<Uri>>? = null

    /** Answers a script dialog itself when dialogs are blocked; false lets WebView show it. */
    private fun blockDialog(result: JsResult): Boolean {
        if (window?.isDialogsBlockingEnabled() != true) return false
        window?.onBlockedDialog(false)
        result.cancel()
        return true
    }

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult) =
        blockDialog(result) || super.onJsAlert(view, url, message, result)

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult) =
        blockDialog(result) || super.onJsConfirm(view, url, message, result)

    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult) =
        blockDialog(result) || super.onJsPrompt(view, url, message, defaultValue, result)

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (fullscreen != null) {
            Log.i(WebViewEx.TAG, "Fullscreen refused: one is already shown")
            callback.onCustomViewHidden()
            return
        }
        fullscreen = callback
        engine.enterFullscreen(view)
    }

    override fun onHideCustomView() {
        engine.exitFullscreen(restoreBrowserControls = true)
        fullscreen?.onCustomViewHidden()
        fullscreen = null
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) { window?.onProgressChanged(newProgress) }

    override fun onReceivedTitle(view: WebView, title: String) { window?.onReceivedTitle(title) }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap) { window?.onReceivedIcon(icon) }

    override fun onPermissionRequest(request: PermissionRequest) = this.view.permissions.onRequest(request)

    override fun onPermissionRequestCanceled(request: PermissionRequest) = view.permissions.onCanceled(request)

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
        view.permissions.onLocationPrompt(origin, callback)

    override fun onGeolocationPermissionsHidePrompt() = view.permissions.onLocationPromptHidden()

    /** Without the debugging setting only rate-limited warnings and errors reach the log. */
    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        val level = message.messageLevel()
        val important = level == ConsoleMessage.MessageLevel.ERROR || level == ConsoleMessage.MessageLevel.WARNING
        if (!view.config.webEngineDebug && (!important || !view.consoleLogBudget.allow())) return true
        val text = "[${message.lineNumber()}] " + message.message().take(MAX_CONSOLE_CHARS)
        when (level) {
            ConsoleMessage.MessageLevel.ERROR -> Log.e(WebViewEx.WEB_VIEW_TAG, text)
            ConsoleMessage.MessageLevel.WARNING -> Log.w(WebViewEx.WEB_VIEW_TAG, text)
            else -> Log.i(WebViewEx.WEB_VIEW_TAG, text)
        }
        return true
    }

    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
        filePicked = filePathCallback
        val shown = window?.onShowFileChooser(fileChooserParams.createIntent()) == true
        if (!shown) filePicked = null
        return shown
    }

    /** The file picker's result for the page that asked, once. */
    fun onFilePicked(resultCode: Int, data: android.content.Intent?) {
        val waiting = filePicked ?: return
        filePicked = null
        waiting.onReceiveValue(FileChooserParams.parseResult(resultCode, data))
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        val created = engine.onCreateWindow(isDialog, isUserGesture) ?: return false
        (resultMsg.obj as WebView.WebViewTransport).webView = created
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView) { this.window?.closeWindow(window) }

    private companion object {
        const val MAX_CONSOLE_CHARS = 2048
    }
}
