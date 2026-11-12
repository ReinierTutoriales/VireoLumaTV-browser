package com.reiniertutoriales.vireolumatv.activity.main

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Android 17 blocks local-network traffic of apps that target API 37 unless they hold
 * ACCESS_LOCAL_NETWORK, and WebView traffic inherits the app's permission: a router, NAS or home
 * server page then only times out. After such a main-frame failure on a local address, Android's
 * own prompt is shown, once per session; granting it reloads the tab.
 * developer.android.com/privacy-and-security/local-network-permission
 */
internal class LocalNetworkPrompt(private val activity: MainActivity) {
    private var asked = false
    private var waiting: BrowserTab? = null

    fun onMainFrameNetworkError(tab: BrowserTab, url: String) {
        if (asked || !LocalNetworkAccess.blocked(activity)) return
        val host = runCatching { url.toUri().host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        activity.lifecycleScope.launch {
            val local = LocalNetworkAccess.isLocalName(host) ||
                withContext(Dispatchers.IO) { LocalNetworkAccess.resolvesLocally(host) }
            if (!local || asked || activity.tabStore.currentTab.value !== tab) return@launch
            asked = true
            waiting = tab
            activity.requestPermissions(arrayOf(LocalNetworkAccess.PERMISSION), REQUEST_LOCAL_NETWORK)
        }
    }

    /** True when [requestCode] was this prompt's; a grant reloads the tab that failed. */
    fun onPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_LOCAL_NETWORK) return false
        val tab = waiting
        waiting = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && tab != null && !tab.closed) tab.engine.reload()
        return true
    }

    private companion object {
        const val REQUEST_LOCAL_NETWORK = 10005
    }
}

internal object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Enforced only on Android 17 and later, for apps targeting API 37 or higher. */
    fun blocked(context: Context): Boolean = Build.VERSION.SDK_INT >= 37 &&
        context.applicationInfo.targetSdkVersion >= 37 &&
        context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED

    /** A `.local` (mDNS) name or a literal local address; never resolves a name. */
    fun isLocalName(host: String): Boolean {
        val name = host.trim('[', ']')
        if (name.endsWith(".local", ignoreCase = true)) return true
        val ipv4 = IPV4.matches(name) && name.split('.').all { it.toInt() <= 255 }
        // Only literals reach InetAddress here, so it never queries DNS on this thread.
        if (!ipv4 && ':' !in name) return false
        return runCatching { isLocal(InetAddress.getByName(name)) }.getOrDefault(false)
    }

    /** Resolves [host] (caller's thread: IO); false when it does not resolve. */
    fun resolvesLocally(host: String): Boolean =
        runCatching { InetAddress.getAllByName(host).any(::isLocal) }.getOrDefault(false)

    /** Private (RFC 1918), link-local and unique-local IPv6 (fc00::/7); loopback stays on the device. */
    fun isLocal(address: InetAddress): Boolean = !address.isLoopbackAddress &&
        (address.isSiteLocalAddress || address.isLinkLocalAddress ||
            (address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC))

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
}
