package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.Context
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.text.BidiFormatter
import com.reiniertutoriales.vireolumatv.R

/**
 * HTTP authentication requests of one page view. WebView reports only the host and realm, not
 * whether the page itself or one of its resources asks, so the page state decides:
 * - the shown page's own host is asked, with a warning when its connection is not encrypted;
 * - while a top-level load is under way (it may have been redirected), any host is asked, and one
 *   that is not the address being loaded is named as another site;
 * - once a page is shown, another host can only be one of its resources: refused without a prompt,
 *   so an embedded resource cannot ask for a password under a page the user trusts.
 * One prompt at a time: requests for the same host and realm share its answer, others are refused.
 */
internal class HttpAuthPrompts(private val context: () -> Context) {
    /** The two answers of WebView's HttpAuthHandler. */
    interface Answer {
        fun proceed(user: String, password: String)
        fun cancel()
    }

    /** Who asks: [host], [cleartext] when known to be unencrypted, [otherThan] the host being loaded. */
    data class Scope(val host: String, val cleartext: Boolean, val otherThan: String?)

    private var openKey: String? = null
    private val waiting = mutableListOf<Answer>()

    fun request(host: String?, realm: String?, shown: Uri?, loading: Uri?, answer: Answer) {
        val scope = scope(host, shown, loading) ?: return answer.cancel()
        val key = "${scope.host.lowercase()}\n${realm.orEmpty()}"
        when (openKey) {
            key -> { waiting += answer; return }
            null -> Unit
            else -> return answer.cancel()
        }
        openKey = key
        waiting += answer
        show(scope, realm)
    }

    private fun show(scope: Scope, realm: String?) {
        val context = context()
        val user = EditText(context).apply {
            setHint(R.string.http_auth_user)
            isSingleLine = true
        }
        val password = EditText(context).apply {
            setHint(R.string.http_auth_password)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.http_auth_title)
            .setMessage(message(context, scope, realm))
            .setCancelable(false)
            .setView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(user)
                addView(password)
            })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                answerAll { it.proceed(user.text.toString(), password.text.toString()) }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> answerAll { it.cancel() } }
            .show()
    }

    private fun answerAll(action: (Answer) -> Unit) {
        val answers = waiting.toList()
        waiting.clear()
        openKey = null
        answers.forEach(action)
    }

    internal companion object {
        /** Realms are page text; a long one could pass for browser wording. */
        const val MAX_REALM = 80

        fun scope(host: String?, shown: Uri?, loading: Uri?): Scope? {
            if (host.isNullOrBlank()) return null
            if (loading != null) {
                val same = sameHost(host, loading)
                return Scope(host, same && isCleartext(loading), if (same) null else loading.host)
            }
            if (shown != null && sameHost(host, shown)) return Scope(host, isCleartext(shown), null)
            return if (shown == null) Scope(host, false, null) else null
        }

        fun message(context: Context, scope: Scope, realm: String?): String {
            val bidi = BidiFormatter.getInstance()
            val lines = mutableListOf(context.getString(R.string.http_auth_message, bidi.unicodeWrap(scope.host)))
            realm?.trim()?.takeIf { it.isNotEmpty() }?.let {
                val shown = if (it.length > MAX_REALM) it.take(MAX_REALM) + "…" else it
                lines += context.getString(R.string.http_auth_realm, bidi.unicodeWrap(shown))
            }
            if (scope.cleartext) lines += context.getString(R.string.http_auth_cleartext)
            scope.otherThan?.let { lines += context.getString(R.string.http_auth_other_site, bidi.unicodeWrap(it)) }
            return lines.joinToString("\n\n")
        }

        private fun sameHost(host: String, page: Uri): Boolean {
            val pageHost = page.host ?: return false
            return host.equals(pageHost, ignoreCase = true) ||
                (page.port != -1 && host.equals("$pageHost:${page.port}", ignoreCase = true))
        }

        private fun isCleartext(page: Uri) = page.scheme.equals("http", ignoreCase = true)
    }
}
