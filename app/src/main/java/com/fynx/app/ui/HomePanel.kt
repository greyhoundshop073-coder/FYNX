package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Production Home shell. Real social content is rendered by FynxRemoteHomeSocialPanel. */
@Composable
fun HomePanel(
    currentUsername: String = "",
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFindPeople: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    onCreatePost: () -> Unit = {}
) {
    val displayUsername = currentUsername.trim().removePrefix("@").trim()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Master reference: FYNX header is first.
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text("FYNX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "FYNX verified",
                        tint = androidx.compose.ui.graphics.Color(0xFF3B82F6),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onCreatePost) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Create post")
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifications")
                }
            }
        }

        // Status/Stories directly follows the header like the master reference.
        item {
            FynxVisibleUpdatesPanel(
                currentUsername = displayUsername,
                onOpenStories = onOpenStories,
                onOpenAi = onOpenAi
            )
        }

        // One consolidated AI voice control; no duplicate microphone/AI controls.
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = FynxDesign.Surface,
                    contentColor = FynxDesign.TextPrimary
                ),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (displayUsername.isBlank()) "Your people. Your moments. Your world."
                        else "Welcome back, $displayUsername",
                        color = FynxDesign.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HomeAiVoiceInlineControl(Modifier.fillMaxWidth())
                }
            }
        }

        // Real remote feed owns captions, full media, engagement and marketplace posts.
        item {
            FynxRemoteHomeSocialPanel(
                currentUsername = displayUsername,
                onOpenFindPeople = onOpenFindPeople,
                onOpenMarketplace = onOpenMarketplace
            )
        }
    }
}

@Composable
fun FynxProfileImage(name: String, uriString: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uriString) {
        bitmap = withContext(Dispatchers.IO) {
            uriString?.let {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(it)).use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap!!.asImageBitmap(),
            contentDescription = name,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        FynxAvatar(name, modifier.clip(CircleShape))
    }
}
