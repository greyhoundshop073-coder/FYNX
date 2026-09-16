package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Production Home shell. Status/AI content scrolls away with the Home feed; the feed remains the only scrolling surface. */
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
    onCreatePost: () -> Unit = {},
    onOpenAuthorProfile: (String) -> Unit = {}
) {
    val displayUsername = currentUsername.trim().removePrefix("@").trim()
    var showCreateMenu by remember { mutableStateOf(false) }
    var showMatureStatusComposer by remember { mutableStateOf(false) }

    fun dismissCreateMenu() {
        showCreateMenu = false
    }

    if (showMatureStatusComposer) {
        FynxMatureStatusComposerPanel(onClose = { showMatureStatusComposer = false })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "home_status_ai") {
            FynxVisibleUpdatesPanel(
                currentUsername = displayUsername,
                onOpenStories = onOpenStories,
                onOpenAi = onOpenAi
            )
        }

        item(key = "home_social_feed") {
            FynxHomeLifecycleRefresh { refreshKey ->
                key(refreshKey) {
                    FynxRemoteHomeSocialPanel(
                        modifier = Modifier.fillMaxWidth(),
                        currentUsername = displayUsername,
                        onOpenFindPeople = onOpenFindPeople,
                        onOpenMarketplace = onOpenMarketplace,
                        onCreatePost = { showCreateMenu = true },
                        onOpenAuthorProfile = onOpenAuthorProfile
                    )
                }
            }
        }
    }

    if (showCreateMenu) {
        FynxHomeCreateMenu(
            onDismiss = ::dismissCreateMenu,
            onPost = {
                dismissCreateMenu()
                onCreatePost()
            },
            onStatus = {
                dismissCreateMenu()
                showMatureStatusComposer = true
            },
            onMarketplace = {
                dismissCreateMenu()
                onOpenMarketplace()
            }
        )
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