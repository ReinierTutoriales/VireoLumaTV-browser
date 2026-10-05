package com.reiniertutoriales.vireolumatv.webengine.webview

object Scripts {
    const val LONG_PRESS_SCRIPT = """
(function() {
    var element = window.VIREOLUMATV_activeElement;
    if (!element || !element.isConnected) return null;
    var link = typeof element.closest === 'function' ? element.closest('a[href]') : null;
    if (link) return link.href;
    return element.src || null;
})()
"""
}
