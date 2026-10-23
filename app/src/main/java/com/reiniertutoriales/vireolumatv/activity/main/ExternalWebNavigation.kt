package com.reiniertutoriales.vireolumatv.activity.main

import android.net.Uri
import android.content.Intent
import java.net.URISyntaxException

internal object ExternalWebNavigation {
    /** The intent described by an `intent:` URL from a page, or null when the URL is malformed. */
    fun parseIntentUrl(url: String): Intent? = try {
        Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
    } catch (e: URISyntaxException) {
        null
    }

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
