package com.fynx.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fynx.app.ui.FynxApp
import com.fynx.app.ui.FynxDeepLinkDestination
import com.fynx.app.ui.FynxDeepLinkParser
import com.fynx.app.ui.FynxNotificationFoundation
import com.fynx.app.ui.FynxTheme

class MainActivity : ComponentActivity() {
    private var pendingDeepLink: FynxDeepLinkDestination? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLink = FynxDeepLinkParser.parse(intent?.data)
        FynxNotificationFoundation.createChannels(this)
        setContent {
            FynxTheme {
                FynxApp(deepLinkDestination = pendingDeepLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        pendingDeepLink = FynxDeepLinkParser.parse(intent?.data)
        setContent {
            FynxTheme {
                FynxApp(deepLinkDestination = pendingDeepLink)
            }
        }
    }
}
