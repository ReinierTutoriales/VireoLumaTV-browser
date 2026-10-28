package com.reiniertutoriales.vireolumatv.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/** Whether the active network claims internet access (it may still be captive or unvalidated). */
fun Context.hasInternet(): Boolean {
    val connectivity = getSystemService<ConnectivityManager>() ?: return false
    val network = connectivity.activeNetwork ?: return false
    return connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
