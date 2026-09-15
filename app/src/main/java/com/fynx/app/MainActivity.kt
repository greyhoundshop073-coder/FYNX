package com.fynx.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fynx.app.ui.AuthState
import com.fynx.app.ui.FynxApp
import com.fynx.app.ui.FynxAuthStore
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
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) registerNotificationTokenIfSignedIn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkDestinationState.value = FynxDeepLinkParser.parse(intent?.data)
        FynxNotificationFoundation.createChannels(this)
        registerNotificationTokenIfSignedIn()

        setContent {
            var showLaunch by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1100)
                showLaunch = false
            }
            if (showLaunch) {
                FynxLaunchScreen()
            } else {
                FynxTheme {
                    FynxApp(deepLinkDestination = deepLinkDestinationState.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkDestinationState.value = FynxDeepLinkParser.parse(intent.data)
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
        modifier = Modifier.fillMaxSize().background(Color.Black),
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
