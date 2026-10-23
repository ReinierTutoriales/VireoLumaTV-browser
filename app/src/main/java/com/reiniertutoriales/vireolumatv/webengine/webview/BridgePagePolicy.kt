package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.Uri
import com.reiniertutoriales.vireolumatv.Config
import androidx.core.net.toUri

internal object BridgePagePolicy {
    fun isPackagedHomePage(uri: Uri?): Boolean =
        uri?.toString()?.startsWith(Config.HOME_PAGE_URL) == true

    fun isNormalWebPage(uri: Uri?): Boolean {
        val scheme = uri?.scheme ?: return false
        return (scheme.equals("http", true) || scheme.equals("https", true)) &&
            uri.isHierarchical && !uri.host.isNullOrBlank() &&
            !uri.host.equals(Config.HOME_PAGE_URL.toUri().host, true)
    }

    fun isCertificatePage(uri: Uri?, generatedPage: String?, hasError: Boolean): Boolean =
        uri?.toString() == "file:///android_asset/" &&
            generatedPage?.startsWith("internal://warning?type=certificate&url=") == true && hasError
}
