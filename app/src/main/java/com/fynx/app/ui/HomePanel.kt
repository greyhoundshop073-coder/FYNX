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
import androidx.compose.material.icons.filled.NotificationsNone
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

/**
 * Production Home shell. Real social content is rendered by FynxRemoteHomeSocialPanel;
 * this screen deliberately contains no hard-coded users, posts, engagement or marketplace data.
 */
@Composable
fun HomePanel(
    currentUsername: String = "",
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFindPeople: () -> Unit = {},
    onOpenAi: () -> Unit = {}
) {
    val displayUsername = currentUsername.trim().removePrefix("@").trim()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            FynxVisibleUpdatesPanel(
                currentUsername = displayUsername,
                onOpenStories = onOpenStories,
                onOpenAi = onOpenAi
            )
        }

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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("FYNX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (displayUsername.isBlank()) "Your people. Your moments. Your world."
                                else "Welcome back, $displayUsername",
                                color = FynxDesign.TextSecondary
                            )
                        }
                        IconButton(onClick = onOpenNotifications) {
                            Icon(Icons.Default.NotificationsNone, "Notifications")
                        }
                    }
                    HomeAiVoiceInlineControl()
                }
            }
        }

        item {
            FynxRemoteHomeSocialPanel(
                currentUsername = displayUsername,
                onOpenFindPeople = onOpenFindPeople,
                onOpenMarketplace = onOpenMarketplace
            )
        }

        item {
            Card(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = FynxDesign.Surface,
                    contentColor = FynxDesign.TextPrimary
                ),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .45f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Your profile", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Photo, bio and account details",
                            style = MaterialTheme.typography.bodySmall,
                            color = FynxDesign.TextSecondary
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = FynxDesign.TextSecondary)
                }
            }
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
