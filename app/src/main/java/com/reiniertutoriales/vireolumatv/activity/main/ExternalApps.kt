package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.util.Log
import android.webkit.URLUtil
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.R

/**
 * Everything that leaves the browser for another app: addresses pages want opened elsewhere
 * (always confirmed by the user first), sharing and the clipboard.
 */
internal class ExternalApps(private val activity: Activity) {
    private var confirmation: AlertDialog? = null

    /** What a page's navigation to [url] should do. */
    enum class Navigation { LOAD, BLOCK, ASK_INTENT, ASK_EXTERNAL }

    /**
     * Web, javascript, data, about and blob addresses load in the page; `intent:` and other schemes
     * are offered to other apps only after the user confirms; an address without a scheme is blocked.
     */
    fun classify(url: String): Navigation {
        val scheme = runCatching { url.toUri().scheme }.getOrNull() ?: return Navigation.BLOCK
        return when {
            URLUtil.isNetworkUrl(url) || IN_PAGE_SCHEMES.any { scheme.equals(it, ignoreCase = true) } -> Navigation.LOAD
            scheme.equals("intent", ignoreCase = true) -> Navigation.ASK_INTENT
            else -> Navigation.ASK_EXTERNAL
        }
    }

    /** Asks before opening a page's `intent:` address; addresses aimed at this browser are refused. */
    fun askToOpenIntentUrl(url: String) {
        val intent = externalIntent(url) ?: return
        askToOpen(url, intent)
    }

    /**
     * Asks before opening [url], a non-web address a page navigated to, in the app that handles it.
     * Only activities that declare themselves browsable can be reached, as with `intent:` addresses.
     */
    fun askToOpenInApp(url: String) = askToOpen(url, Intent(Intent.ACTION_VIEW, url.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** The link menu's "open in another app": the user chose it, so no question. */
    fun openInApp(url: String) {
        start(externalIntent(url) ?: return)
    }

    private fun externalIntent(url: String): Intent? {
        val intent = ExternalWebNavigation.parseIntentUrl(url)
        if (intent == null || intent.resolveActivity(activity.packageManager)?.packageName == activity.packageName) {
            toast(R.string.error_no_app)
            return null
        }
        return intent
    }

    /** One question at a time: a page cannot stack them. */
    private fun askToOpen(url: String, intent: Intent) {
        if (confirmation != null) return
        confirmation = AlertDialog.Builder(activity)
            .setTitle(R.string.external_open_title)
            .setMessage(activity.getString(R.string.external_open_message) + "\n\n" + url)
            .setPositiveButton(R.string.action_open) { _, _ -> start(intent) }
            .setNegativeButton(R.string.action_cancel, null)
            .setOnDismissListener { confirmation = null }
            .show()
    }

    fun share(url: String) {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.link_share))
            .putExtra(Intent.EXTRA_TEXT, url)
        try {
            activity.startActivity(share)
        } catch (error: Exception) {
            Log.w(TAG, "Sharing failed", error)
            toast(R.string.error_no_app)
        }
    }

    fun copyToClipboard(url: String) {
        activity.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("URL", url))
        toast(R.string.link_copied)
    }

    private fun start(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "No app for $intent", error)
            toast(R.string.external_no_app)
        } catch (error: Exception) {
            Log.w(TAG, "Opening another app failed", error)
            toast(R.string.error_no_app)
        }
    }

    private fun toast(text: Int) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    private companion object {
        const val TAG = "ExternalApps"
        val IN_PAGE_SCHEMES = listOf("javascript", "data", "about", "blob")
    }
}
