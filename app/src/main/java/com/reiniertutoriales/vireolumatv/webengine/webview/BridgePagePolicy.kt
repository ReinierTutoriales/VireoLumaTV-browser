package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri
import com.reiniertutoriales.vireolumatv.Config

internal object BridgePagePolicy {
    fun isNormalWebPage(uri: Uri?): Boolean {
        val scheme = uri?.scheme ?: return false
        return (scheme.equals("http", true) || scheme.equals("https", true)) &&
            uri.isHierarchical && !uri.host.isNullOrBlank() &&
            !uri.host.equals(Uri.parse(Config.HOME_PAGE_URL).host, true)
    }

    fun isCertificatePage(uri: Uri?, generatedPage: String?, hasError: Boolean): Boolean =
        uri?.toString() == "file:///android_asset/" &&
            generatedPage?.startsWith("internal://warning?type=certificate&url=") == true && hasError
}
