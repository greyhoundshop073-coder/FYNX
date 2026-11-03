package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Lightweight network-quality signal used by the live UI to adapt expectations on weak links. */
object FynxNetworkQuality {
    enum class Level { OFFLINE, WEAK, GOOD }

    fun current(context: Context): Level {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return Level.OFFLINE
        // HttpURLConnection routes over Android's default network. Evaluate that
        // same network rather than borrowing bandwidth from a secondary network
        // that the current request is not using.
        val active = manager.activeNetwork ?: return Level.OFFLINE
        val capabilities = manager.getNetworkCapabilities(active) ?: return Level.OFFLINE
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return Level.OFFLINE

        val downstream = capabilities.linkDownstreamBandwidthKbps
        // Unknown bandwidth must not be treated as a fast connection. Using the
        // weak-network path is safer because it gives requests longer timeouts
        // and tighter concurrency while the real link quality is uncertain.
        if (downstream <= 0) return Level.WEAK
        return if (downstream < 1_000) Level.WEAK else Level.GOOD
    }
}
