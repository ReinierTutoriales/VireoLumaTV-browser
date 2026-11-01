package com.reiniertutoriales.vireolumatv.utils

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Follows the default network from its capabilities, as Android documents: the synchronous queries
 * race inside onAvailable, while onCapabilitiesChanged follows it for every new default network.
 * - online: the network is set up for the internet and is not stuck at a captive portal sign-in;
 * - restored: it just became online, or just passed Android's validation probe. Validation alone is
 *   not required, because networks that block the probe server never validate yet do work.
 * Callbacks arrive on a binder thread; [onChange] receives every change there.
 */
internal class NetworkWatcher(private val onChange: (online: Boolean, restored: Boolean) -> Unit) :
    ConnectivityManager.NetworkCallback() {
    private var network: Any? = null
    private var online = false
    private var validated = false

    /** Forgets the last network, before registering again: the first report then counts as restored. */
    @Synchronized fun reset() {
        network = null
        online = false
        validated = false
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update(
        network,
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    )

    // For a default-network callback this means it is no longer the default; a replacement, if any,
    // reports its own capabilities, so only the loss of the network being followed goes offline.
    override fun onLost(network: Network) = lost(network)

    @Synchronized internal fun lost(network: Any) {
        if (network == this.network) update(network, online = false, validated = false)
    }

    @Synchronized internal fun update(network: Any, online: Boolean, validated: Boolean) {
        val restored = (online && !this.online) || (online && validated && !this.validated)
        val changed = online != this.online || validated != this.validated || network != this.network
        this.network = network
        this.online = online
        this.validated = validated
        if (changed) onChange(online, restored)
    }
}
