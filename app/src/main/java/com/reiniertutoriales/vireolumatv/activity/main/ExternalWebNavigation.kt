package com.reiniertutoriales.vireolumatv.activity.main

import android.net.Uri
import android.content.Intent

internal object ExternalWebNavigation {
    fun copyAllowedData(source: Intent, target: Intent) {
        if (isAllowed(source.data)) target.data = source.data
    }

    fun isAllowed(uri: Uri?): Boolean {
        val scheme = uri?.scheme ?: return false
        return (scheme.equals("http", ignoreCase = true) ||
            scheme.equals("https", ignoreCase = true)) &&
            uri.isHierarchical && !uri.host.isNullOrBlank()
    }
}
