package com.wiggyserver.immichwallpaper.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.wiggyserver.immichwallpaper.data.NetworkMonitor

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private fun caps() = cm.activeNetwork?.let(cm::getNetworkCapabilities)

    override val isOnline: Boolean get() = caps()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    override val isUnmetered: Boolean get() = caps()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
}
