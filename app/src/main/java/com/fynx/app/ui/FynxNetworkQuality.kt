package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Lightweight network-quality signal used by the live UI to adapt expectations on weak links. */
object FynxNetworkQuality {
    enum class Level { OFFLINE, WEAK, GOOD }

    fun current(context: Context): Level {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return Level.OFFLINE
        val network = manager.activeNetwork ?: return Level.OFFLINE
        val capabilities = manager.getNetworkCapabilities(network) ?: return Level.OFFLINE
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return Level.WEAK
        val downstream = capabilities.linkDownstreamBandwidthKbps
        return if (downstream in 1..999) Level.WEAK else Level.GOOD
    }
}
