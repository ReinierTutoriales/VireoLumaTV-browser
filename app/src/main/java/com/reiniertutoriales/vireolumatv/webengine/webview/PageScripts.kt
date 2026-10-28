package com.reiniertutoriales.vireolumatv.webengine.webview

object PageScripts {
    const val HOLD_SCRIPT = """
(function() {
    var element = window.VIREOLUMATV_activeElement;
    if (!element || !element.isConnected) return null;
    var link = typeof element.closest === 'function' ? element.closest('a[href]') : null;
    if (link) return link.href;
    return element.src || null;
})()
"""
}
