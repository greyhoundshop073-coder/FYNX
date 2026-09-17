package com.fynx.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Coordinates authoritative Home refreshes after returning from another FYNX surface. */
object FynxHomeLifecycleRefreshBus {
    private const val FEED_CACHE_PREFS = "fynx_feed_cache"
    private const val FEED_CACHE_KEY_PREFIX = "fynx_feed_cache_v1_"
    private const val FEED_CACHE_TIME_KEY_PREFIX = "fynx_feed_cache_time_v1_"
    private val refreshSignal = mutableIntStateOf(0)

    fun request(context: Context) {
        runCatching {
            FynxAuthStore.accountStorageKey(context)?.takeIf { it.isNotBlank() }?.let { accountKey ->
                context.getSharedPreferences(FEED_CACHE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(FEED_CACHE_KEY_PREFIX + accountKey)
                    .remove(FEED_CACHE_TIME_KEY_PREFIX + accountKey)
                    .apply()
            }
        }
        refreshSignal.intValue++
    }

    @Composable
    fun currentVersion(): Int = refreshSignal.intValue
}

@Composable
fun FynxHomeLifecycleRefresh(content: @Composable (refreshKey: Int) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleRefreshKey by remember { mutableIntStateOf(0) }
    val publishRefreshKey = FynxHomeLifecycleRefreshBus.currentVersion()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) lifecycleRefreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    content(lifecycleRefreshKey + publishRefreshKey)
}
