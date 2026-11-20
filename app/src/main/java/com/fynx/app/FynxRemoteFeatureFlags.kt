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
 * Strict allowlist for remotely controlled, optional experiences already shipped in this APK.
 *
 * Remote Config is not authorization and must never control authentication, account recovery,
 * safety, wallet, payments, orders, refunds, disputes, or any other critical user protection.
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
 * Fetches optional configuration without blocking FYNX startup.
 *
 * In-app defaults are immediately safe. Activated values are cached by Firebase and remain
 * usable offline. Failed requests never prevent the app from opening. Unknown parameters and
 * parameters outside the explicit allowlist always evaluate to false.
 */
internal object FynxRemoteFeatureFlags {
    private const val PRODUCTION_FETCH_INTERVAL_SECONDS = 12L * 60L * 60L

    private val initializationStarted = AtomicBoolean(false)
    private val mutableValues = MutableStateFlow(FynxRemoteFeatureFlagPolicy.defaults)

    @Volatile
    private var updateRegistration: ConfigUpdateListenerRegistration? = null

    /** Read-only state for optional UI consumers added in separately reviewed feature changes. */
    val values: StateFlow<Map<String, Boolean>> = mutableValues.asStateFlow()

    fun isEnabled(key: String): Boolean {
        if (!FynxRemoteFeatureFlagPolicy.isSupported(key)) return false
        return mutableValues.value[key] ?: FynxRemoteFeatureFlagPolicy.defaultValue(key)
    }

    /**
     * Called once by [FynxApplication]. Every network operation is asynchronous and optional.
     */
    fun initialize() {
        if (!initializationStarted.compareAndSet(false, true)) return

        val remoteConfig = try {
            FirebaseRemoteConfig.getInstance()
        } catch (_: Exception) {
            // Firebase may be unavailable in local/test variants. Keep safe in-app defaults.
            initializationStarted.set(false)
            return
        }

        val minimumFetchInterval = if (BuildConfig.DEBUG) {
            0L
        } else {
            PRODUCTION_FETCH_INTERVAL_SECONDS
        }

        try {
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
                .build()

            remoteConfig.setConfigSettingsAsync(settings).addOnCompleteListener {
                // Even a settings failure must not prevent applying safe local defaults.
                remoteConfig.setDefaultsAsync(FynxRemoteFeatureFlagPolicy.defaults)
                    .addOnCompleteListener {
                        publishActivatedValues(remoteConfig)
                        registerRealtimeUpdates(remoteConfig)
                        fetchAndActivate(remoteConfig)
                    }
            }
        } catch (_: Exception) {
            // Remote configuration is a best-effort enhancement, never a startup dependency.
            publishActivatedValues(remoteConfig)
        }
    }

    private fun fetchAndActivate(remoteConfig: FirebaseRemoteConfig) {
        try {
            remoteConfig.fetchAndActivate().addOnCompleteListener {
                // Publish defaults/cached values as well as a successful new activation.
                publishActivatedValues(remoteConfig)
            }
        } catch (_: Exception) {
            // Retain the last published values if a Firebase SDK call is unavailable.
        }
    }

    private fun registerRealtimeUpdates(remoteConfig: FirebaseRemoteConfig) {
        if (updateRegistration != null) return

        try {
            updateRegistration = remoteConfig.addOnConfigUpdateListener(
                object : ConfigUpdateListener {
                    override fun onUpdate(configUpdate: ConfigUpdate) {
                        // Ignore unrelated or unknown parameters; only the reviewed allowlist
                        // can affect the state exposed to FYNX screens.
                        if (configUpdate.updatedKeys.none(FynxRemoteFeatureFlagPolicy::isSupported)) {
                            return
                        }
                        remoteConfig.activate().addOnCompleteListener {
                            publishActivatedValues(remoteConfig)
                        }
                    }

                    override fun onError(error: FirebaseRemoteConfigException) {
                        // Remote updates are optional. The last activated values remain in use.
                    }
                }
            )
        } catch (_: Exception) {
            // Devices/builds without realtime support simply use the next normal fetch.
        }
    }

    private fun publishActivatedValues(remoteConfig: FirebaseRemoteConfig) {
        mutableValues.value = FynxRemoteFeatureFlagPolicy.defaults.mapValues { (key, _) ->
            remoteConfig.getBoolean(key)
        }
    }
}
