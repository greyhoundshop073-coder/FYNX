package com.fynx.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fynx.app.ui.AuthState
import com.fynx.app.ui.FynxApp
import com.fynx.app.ui.FynxAuthStore
import com.fynx.app.ui.FynxIncomingCall
import com.fynx.app.ui.FynxDeepLinkDestination
import com.fynx.app.ui.FynxDeepLinkParser
import com.fynx.app.ui.FynxNotificationDeviceManager
import com.fynx.app.ui.FynxNotificationFoundation
import com.fynx.app.ui.FynxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val deepLinkDestinationState = mutableStateOf<FynxDeepLinkDestination?>(null)
    private val incomingCallState = mutableStateOf<FynxIncomingCall?>(null)
    private var lastHandledDeepLink: String? = null
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) registerNotificationTokenIfSignedIn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateDeepLink(intent?.data?.toString())
        updateIncomingCall(intent)
        FynxNotificationFoundation.createChannels(this)
        registerNotificationTokenIfSignedIn()

        setContent {
            var showLaunch by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1100)
                showLaunch = false
            }
            if (showLaunch) {
                FynxLaunchScreen()
            } else {
                FynxTheme {
                    FynxApp(deepLinkDestination = deepLinkDestinationState.value, incomingCall = incomingCallState.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateDeepLink(intent.data?.toString())
        updateIncomingCall(intent)
    }

    private fun updateDeepLink(rawUri: String?) {
        val normalized = rawUri?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == lastHandledDeepLink) return
        lastHandledDeepLink = normalized
        deepLinkDestinationState.value = FynxDeepLinkParser.parse(normalized?.let(android.net.Uri::parse))
    }

    private fun updateIncomingCall(intent: Intent?) {
        if (intent?.action != "com.fynx.app.action.INCOMING_CALL") return
        val callId = intent.getStringExtra("fynx_call_id")?.trim().orEmpty()
        val fromUserId = intent.getStringExtra("fynx_call_from_user_id")?.trim().orEmpty()
        val fromUsername = intent.getStringExtra("fynx_call_from_username")?.trim().orEmpty()
        if (callId.isBlank() || fromUserId.isBlank() || fromUsername.isBlank()) return
        incomingCallState.value = FynxIncomingCall(
            callId = callId,
            fromUserId = fromUserId,
            fromUsername = fromUsername,
            video = intent.getBooleanExtra("fynx_call_video", false),
            action = intent.getStringExtra("fynx_call_action")?.trim().orEmpty().ifBlank { "VIEW" }
        )
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun registerNotificationTokenIfSignedIn() {
        if (FynxAuthStore.load(this).state != AuthState.SIGNED_IN) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        activityScope.launch {
            FynxNotificationDeviceManager.registerCurrentToken(this@MainActivity)
        }
    }
}

@Composable
private fun FynxLaunchScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(com.fynx.app.R.drawable.ic_fynx_logo),
                contentDescription = "FYNX",
                modifier = Modifier.size(104.dp)
            )
            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.Text(
                "FYNX",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}
