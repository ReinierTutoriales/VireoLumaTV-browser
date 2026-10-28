package com.reiniertutoriales.vireolumatv.activity.main

import android.util.Patterns
import java.net.URLEncoder
import java.util.Locale

/** What the address field's text means: a web address to open, or words to search for. */
object AddressInput {
    /**
     * The address to load for [text]: itself when it is a web address (https:// added when no
     * scheme is given), otherwise [searchTemplate] with "[query]" replaced by the encoded text.
     */
    fun resolve(text: String, searchTemplate: String): String {
        val lower = text.trim().lowercase(Locale.ROOT)
        val isAddress = Patterns.WEB_URL.matcher(text).matches() || lower.startsWith("http://") || lower.startsWith("https://")
        if (isAddress) return if (text.lowercase(Locale.ROOT).contains("://")) text else "https://$text"
        return searchTemplate.replace("[query]", URLEncoder.encode(text, "UTF-8"))
    }
}
