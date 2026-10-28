package com.reiniertutoriales.vireolumatv.activity.main

import android.net.Uri
import android.content.Intent
import java.net.URISyntaxException

internal object ExternalWebNavigation {
    /** The intent described by an `intent:` URL from a page, or null when the URL is malformed. */
    fun parseIntentUrl(url: String): Intent? = try {
        Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
            // Web links must resolve through BROWSABLE filters, never a page-selected component.
            component = null
            selector = null
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = flags and URI_GRANTS.inv()
            clipData = null
        }
    } catch (e: URISyntaxException) {
        null
    }

    private val URI_GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

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
