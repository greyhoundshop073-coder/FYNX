package com.fynx.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.fynx.app.MainActivity

class FynxContactsActivity : ComponentActivity() {
    private fun openCall(username: String, video: Boolean) {
        val target = username.removePrefix("@").trim()
        if (target.isBlank()) return
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                data = android.net.Uri.parse(FynxDeepLinkParser.callAppLink(target, video))
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

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
                FynxContactsPanel(
                    onBack = { finish() },
                    onVoiceCall = { openCall(it, false) },
                    onVideoCall = { openCall(it, true) }
                )
            }
        }
    }
}
