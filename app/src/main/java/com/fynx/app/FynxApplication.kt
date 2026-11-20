package com.fynx.app

import android.app.Application

/**
 * Initializes optional configuration at process startup without touching MainActivity,
 * sign-in, account recovery, payments, or other critical flows.
 */
class FynxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FynxRemoteFeatureFlags.initialize()
    }
}
