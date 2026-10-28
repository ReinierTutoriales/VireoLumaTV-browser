package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.Context
import android.net.http.SslError
import android.text.TextUtils
import android.view.View
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.R
import java.util.Locale

/**
 * The page shown instead of a site whose certificate failed. The asset is a template; the text,
 * the site and the error are filled in here, HTML-escaped, so the page needs no bridge to read them.
 */
internal object CertificateWarningPage {
    private val PLACEHOLDER = Regex("""\{\{(\w+)}}""")

    /** The page for a [primaryError] at [url]; [details] describes the certificate. */
    fun render(context: Context, template: String, url: String, primaryError: Int, details: String): String {
        val locale = Locale.getDefault()
        val values = mapOf(
            "lang" to locale.toLanguageTag(),
            "dir" to if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) "rtl" else "ltr",
            "title" to context.getString(R.string.connection_isnt_secure),
            "hostLabel" to context.getString(R.string.hostname),
            "host" to runCatching { url.toUri().host }.getOrNull().orEmpty(),
            "errorLabel" to context.getString(R.string.err_desk),
            "error" to context.getString(describe(primaryError)),
            "detailsLabel" to context.getString(R.string.details),
            "details" to details,
            "back" to context.getString(R.string.back_to_safety),
            "proceed" to context.getString(R.string.go_im_aware),
        )
        return fill(template, values)
    }

    /** Replaces each `{{name}}` with its escaped value; unknown names become empty. */
    fun fill(template: String, values: Map<String, String>): String =
        PLACEHOLDER.replace(template) { TextUtils.htmlEncode(values[it.groupValues[1]].orEmpty()) }

    fun describe(primaryError: Int): Int = when (primaryError) {
        SslError.SSL_EXPIRED -> R.string.ssl_expired
        SslError.SSL_IDMISMATCH -> R.string.ssl_idmismatch
        SslError.SSL_NOTYETVALID, SslError.SSL_DATE_INVALID -> R.string.ssl_date_invalid
        SslError.SSL_UNTRUSTED -> R.string.ssl_untrusted
        else -> R.string.ssl_invalid
    }
}
