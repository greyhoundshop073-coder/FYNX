package com.fynx.app.ui

import android.content.Context
import androidx.compose.runtime.Composable

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
    val publishRefreshKey = FynxHomeLifecycleRefreshBus.currentVersion()
    content(publishRefreshKey)
}
