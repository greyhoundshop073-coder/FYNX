package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Lightweight network-quality signal used by the live UI to adapt expectations on weak links. */
object FynxNetworkQuality {
    enum class Level { OFFLINE, WEAK, GOOD }

    fun current(context: Context): Level {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return Level.OFFLINE
        var sawUsableNetwork = false
        var bestDownstreamKbps = 0

        manager.allNetworks.forEach { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@forEach
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@forEach
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return@forEach

            sawUsableNetwork = true
            bestDownstreamKbps = maxOf(bestDownstreamKbps, capabilities.linkDownstreamBandwidthKbps)
        }

        if (!sawUsableNetwork) return Level.OFFLINE
        return if (bestDownstreamKbps in 1..999) Level.WEAK else Level.GOOD
    }
}
