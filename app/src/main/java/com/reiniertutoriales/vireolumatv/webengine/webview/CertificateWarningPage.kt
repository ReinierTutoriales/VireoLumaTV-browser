package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.Context
import android.net.http.SslError
import android.text.TextUtils
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.utils.AppLanguages

/**
 * The page shown instead of a site whose certificate failed. The asset is a template; the text,
 * the site and the error are filled in here, HTML-escaped, so the page needs no bridge to read them.
 */
internal object CertificateWarningPage {
    private val PLACEHOLDER = Regex("""\{\{(\w+)}}""")

    /** The page for a [primaryError] at [url]; [details] describes the certificate. */
    fun render(context: Context, template: String, url: String, primaryError: Int, details: String): String {
        val locale = AppLanguages.localeOf(context)
        val values = mapOf(
            "lang" to locale.toLanguageTag(),
            "dir" to if (AppLanguages.isRtl(locale)) "rtl" else "ltr",
            "title" to context.getString(R.string.cert_title),
            "hostLabel" to context.getString(R.string.cert_site),
            "host" to runCatching { url.toUri().host }.getOrNull().orEmpty(),
            "errorLabel" to context.getString(R.string.cert_problem),
            "error" to context.getString(describe(primaryError)),
            "detailsLabel" to context.getString(R.string.cert_details),
            "details" to details,
            "back" to context.getString(R.string.cert_go_back),
            "proceed" to context.getString(R.string.cert_proceed),
        )
        return fill(template, values)
    }

    /** Replaces each `{{name}}` with its escaped value; unknown names become empty. */
    fun fill(template: String, values: Map<String, String>): String =
        PLACEHOLDER.replace(template) { TextUtils.htmlEncode(values[it.groupValues[1]].orEmpty()) }

    fun describe(primaryError: Int): Int = when (primaryError) {
        SslError.SSL_EXPIRED -> R.string.cert_expired
        SslError.SSL_IDMISMATCH -> R.string.cert_wrong_site
        SslError.SSL_NOTYETVALID, SslError.SSL_DATE_INVALID -> R.string.cert_bad_date
        SslError.SSL_UNTRUSTED -> R.string.cert_untrusted
        else -> R.string.cert_invalid
    }
}
