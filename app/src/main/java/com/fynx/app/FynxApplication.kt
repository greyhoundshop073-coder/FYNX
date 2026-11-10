package com.fynx.app

import android.app.Application

/**
 * Process-level initialization for non-blocking, optional app configuration.
 * Firebase Remote Config is deliberately isolated from authentication and payments.
 */
class FynxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FynxRemoteFeatureFlags.initialize()
    }
}