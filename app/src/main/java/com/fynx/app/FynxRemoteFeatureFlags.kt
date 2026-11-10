package com.fynx.app

import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Allowlist for remotely controlled experiments. All switches fail closed.
 *
 * These flags are reserved for optional experiences that are already included in
 * the installed APK. They must never gate login, account recovery, safety, wallet,
 * payments, orders, or authorization.
 */
internal object FynxRemoteFeatureFlagPolicy {
    const val HOME_FEED_EXPERIMENT = "home_feed_experiment_enabled"
    const val MARKETPLACE_DISCOVERY_EXPERIMENT = "marketplace_discovery_experiment_enabled"
    const val AI_VOICE_EXPERIMENT = "ai_voice_experiment_enabled"

    val defaults: Map<String, Boolean> = mapOf(
        HOME_FEED_EXPERIMENT to false,
        MARKETPLACE_DISCOVERY_EXPERIMENT to false,
        AI_VOICE_EXPERIMENT to false
    )

    fun isSupported(key: String): Boolean = key in defaults

    fun defaultValue(key: String): Boolean = defaults[key] ?: false
}

/**
 * Fetches safe boolean feature flags without blocking app startup.
 *
 * Cached activated values remain available offline. A failed fetch never prevents
 * FYNX from starting, and unknown flags always evaluate to false.
 */
internal object FynxRemoteFeatureFlags {
    private const val PRODUCTION_FETCH_INTERVAL_SECONDS = 12L * 60L * 60L

    private val initializationStarted = AtomicBoolean(false)
    private val mutableValues = MutableStateFlow(FynxRemoteFeatureFlagPolicy.defaults)
    private var updateRegistration: ConfigUpdateListenerRegistration? = null

    val values: StateFlow<Map<String, Boolean>> = mutableValues.asStateFlow()

    fun isEnabled(key: String): Boolean {
        if (!FynxRemoteFeatureFlagPolicy.isSupported(key)) return false
        return mutableValues.value[key] ?: FynxRemoteFeatureFlagPolicy.defaultValue(key)
    }

    fun initialize() {
        if (!initializationStarted.compareAndSet(false, true)) return

        val remoteConfig = try {
            FirebaseRemoteConfig.getInstance()
        } catch (_: Exception) {
            initializationStarted.set(false)
            return
        }

        val minimumFetchInterval = if (BuildConfig.DEBUG) {
            0L
        } else {
            PRODUCTION_FETCH_INTERVAL_SECONDS
        }
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
            .build()

        remoteConfig.setConfigSettingsAsync(settings).addOnCompleteListener {
            remoteConfig.setDefaultsAsync(FynxRemoteFeatureFlagPolicy.defaults)
                .addOnCompleteListener {
                    publishActivatedValues(remoteConfig)
                    remoteConfig.fetchAndActivate().addOnCompleteListener {
                        // A failed fetch still leaves the app on defaults or its last
                        // successfully activated values.
                        publishActivatedValues(remoteConfig)
                        registerRealtimeUpdates(remoteConfig)
                    }
                }
        }
    }

    private fun registerRealtimeUpdates(remoteConfig: FirebaseRemoteConfig) {
        if (updateRegistration != null) return

        try {
            updateRegistration = remoteConfig.addOnConfigUpdateListener(
                object : ConfigUpdateListener {
                    override fun onUpdate(configUpdate: ConfigUpdate) {
                        if (configUpdate.updatedKeys.none(FynxRemoteFeatureFlagPolicy::isSupported)) {
                            return
                        }
                        remoteConfig.activate().addOnCompleteListener {
                            publishActivatedValues(remoteConfig)
                        }
                    }

                    override fun onError(error: FirebaseRemoteConfigException) {
                        // Remote updates are optional; cached values remain active.
                    }
                }
            )
        } catch (_: Exception) {
            // Unsupported/unavailable Remote Config must never affect app startup.
        }
    }

    private fun publishActivatedValues(remoteConfig: FirebaseRemoteConfig) {
        mutableValues.value = FynxRemoteFeatureFlagPolicy.defaults.mapValues { (key, _) ->
            remoteConfig.getBoolean(key)
        }
    }
}