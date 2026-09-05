package com.fynx.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

class FynxContactsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val appearance = remember { FynxPreferencesStore.loadAppearance(context) }
            val accent = remember { FynxPreferencesStore.loadAccent(context) }
            FynxTheme(
                accent = accent,
                darkMode = when (appearance) { "Light" -> false; "Dark" -> true; else -> isSystemInDarkTheme() }
            ) {
                FynxContactsPanel(onBack = { finish() })
            }
        }
    }
}
