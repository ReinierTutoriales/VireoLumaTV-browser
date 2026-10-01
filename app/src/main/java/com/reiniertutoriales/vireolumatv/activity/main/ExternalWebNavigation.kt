package com.reiniertutoriales.vireolumatv.activity.main

import android.net.Uri

internal object ExternalWebNavigation {
    fun isAllowed(uri: Uri?): Boolean {
        val scheme = uri?.scheme ?: return false
        return (scheme.equals("http", ignoreCase = true) ||
            scheme.equals("https", ignoreCase = true)) &&
            uri.isHierarchical && !uri.host.isNullOrBlank()
    }
}
