package com.fynx.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

/** Coordinates authoritative Home refreshes after returning from another FYNX surface. */
object FynxHomeLifecycleRefreshBus {
    private const val FEED_CACHE_PREFS = "fynx_feed_cache"
    private const val FEED_CACHE_KEY_PREFIX = "fynx_feed_cache_v1_"
    private const val FEED_CACHE_TIME_KEY_PREFIX = "fynx_feed_cache_time_v1_"
    private val refreshSignal = mutableStateOf(0)

    fun request(context: Context) {
        // Keep the last known Home snapshot available while the authoritative refresh runs.
        // The Home surface can therefore continue showing valid content during refresh or
        // temporary network failure instead of first destroying its offline fallback.
        runCatching {
            FynxAuthStore.accountStorageKey(context)?.takeIf { it.isNotBlank() }?.let { accountKey ->
                context.getSharedPreferences(FEED_CACHE_PREFS, Context.MODE_PRIVATE)
                    .getString(FEED_CACHE_KEY_PREFIX + accountKey, null)
                    ?.let { cached ->
                        context.getSharedPreferences(FEED_CACHE_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(FEED_CACHE_KEY_PREFIX + accountKey, cached)
                            .apply()
                    }
                context.getSharedPreferences(FEED_CACHE_PREFS, Context.MODE_PRIVATE)
                    .getLong(FEED_CACHE_TIME_KEY_PREFIX + accountKey, 0L)
            }
        }
        refreshSignal.value++
    }

    @Composable
    fun currentVersion(): Int = refreshSignal.value
}

@Composable
fun FynxHomeLifecycleRefresh(content: @Composable (refreshKey: Int) -> Unit) {
    val publishRefreshKey = FynxHomeLifecycleRefreshBus.currentVersion()
    content(publishRefreshKey)
}
