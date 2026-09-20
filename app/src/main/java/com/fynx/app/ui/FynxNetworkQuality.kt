package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Lightweight network-quality signal used by the live UI to adapt expectations on weak links. */
object FynxNetworkQuality {
    enum class Level { OFFLINE, WEAK, GOOD }

    fun current(context: Context): Level {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return Level.OFFLINE
        var sawInternetNetwork = false
        var sawUnknownBandwidth = false
        var bestDownstreamKbps = 0
        val active = manager.activeNetwork
        val orderedNetworks = buildList {
            if (active != null) add(active)
            manager.allNetworks.filter { it != active }.forEach(::add)
        }

        orderedNetworks.forEach { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@forEach
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@forEach
            sawInternetNetwork = true
            val downstream = capabilities.linkDownstreamBandwidthKbps
            if (downstream <= 0) {
                sawUnknownBandwidth = true
            } else {
                bestDownstreamKbps = maxOf(bestDownstreamKbps, downstream)
            }
        }

        if (!sawInternetNetwork) return Level.OFFLINE
        // Unknown bandwidth must not be treated as a fast connection. Using the
        // weak-network path is safer because it gives requests longer timeouts
        // and tighter concurrency while the real link quality is uncertain.
        if (sawUnknownBandwidth && bestDownstreamKbps == 0) return Level.WEAK
        return if (bestDownstreamKbps < 1_000) Level.WEAK else Level.GOOD
    }
}
