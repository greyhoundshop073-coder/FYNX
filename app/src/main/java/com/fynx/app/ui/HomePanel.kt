package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Production Home shell. Status/AI content is supplied to the feed's single scroll surface. */
@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenCamera: () -> Unit = {},
    onOpenFastCamera: () -> Unit = onOpenCamera,
    onOpenStatusOwner: (String) -> Unit = {},
    onCreatePost: () -> Unit = {},
    onOpenAuthorProfile: (String) -> Unit = {},
    initialPostId: String? = null,
    initialCommentId: String? = null,
    onInitialPostConsumed: () -> Unit = {}
) {
    val displayUsername = currentUsername.trim().removePrefix("@").trim()
    val context = LocalContext.current
    var showCreateMenu by remember { mutableStateOf(false) }
    var showMatureStatusComposer by remember { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    fun dismissCreateMenu() {
        showCreateMenu = false
    }

    if (showMatureStatusComposer) {
        FynxMatureStatusComposerPanel(onClose = { showMatureStatusComposer = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            FynxHomeLifecycleRefresh { refreshKey ->
                LaunchedEffect(refreshKey) {
                    if (refreshKey != 0) {
                        isPullRefreshing = true
                        delay(1200L)
                        isPullRefreshing = false
                    }
                }
                PullToRefreshBox(
                    isRefreshing = isPullRefreshing,
                    onRefresh = {
                        if (!isPullRefreshing) {
                            isPullRefreshing = true
                            FynxHomeLifecycleRefreshBus.request(context)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Keep the existing Home feed instance mounted while its authoritative
                    // refresh runs. The refresh signal is consumed by the feed itself, so a
                    // refresh does not recreate the LazyColumn, discard its scroll position,
                    // or temporarily replace its cached snapshot with a new empty instance.
                    FynxRemoteHomeSocialPanel(
                        modifier = Modifier.fillMaxSize(),
                        currentUsername = displayUsername,
                        onOpenFindPeople = onOpenFindPeople,
                        onOpenMarketplace = onOpenMarketplace,
                        onOpenMarketplaceListing = { listingId -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FynxDeepLinkParser.marketplaceAppLink(listingId)))) },
                        onCreatePost = { showCreateMenu = true },
                        onOpenAuthorProfile = onOpenAuthorProfile,
                        initialPostId = initialPostId,
                        initialCommentId = initialCommentId,
                        onInitialPostConsumed = onInitialPostConsumed,
                        header = {
                            // Explicit Home geometry frame: keep the existing Status + AI
                            // components together as one intentional upper-feed region. This
                            // controls their horizontal rhythm without changing their wiring.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FynxVisibleUpdatesPanel(
                                    currentUsername = displayUsername,
                                    onOpenStories = onOpenStories,
                                    onOpenAi = onOpenAi,
                                    onOpenCamera = onOpenCamera,
                                    onOpenFastCamera = onOpenFastCamera,
                                    onCreateStatus = { showMatureStatusComposer = true },
                                    onOpenStatusOwner = onOpenStatusOwner
                                )
                            }
                        }
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
fun FynxProfileImage(name: String, uriString: String?, modifier: Modifier = Modifier, ownerUsername: String? = null) {
    val context = LocalContext.current
    val authUsername = remember(context) {
        FynxAuthStore.load(context).username
            ?.removePrefix("@")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
    val normalizedOwner = (ownerUsername ?: authUsername)
        ?.removePrefix("@")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    var remotePhotoId by remember(normalizedOwner) {
        mutableStateOf(normalizedOwner?.let { FynxProfileRemoteClient.cachedProfilePhotoId(context, it) })
    }

    LaunchedEffect(normalizedOwner) {
        if (normalizedOwner != null) {
            FynxProfileRemoteClient.get(context, normalizedOwner)
                .onSuccess { profile -> remotePhotoId = profile.profilePhotoMediaId }
        }
    }

    if (!remotePhotoId.isNullOrBlank()) {
        FynxRemoteProfileAvatar(
            mediaId = remotePhotoId,
            contentDescription = name,
            modifier = modifier.clip(CircleShape),
            ownerUsername = normalizedOwner
        )
        return
    }

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
        FynxAvatar(name, modifier.clip(CircleShape), ownerUsername = normalizedOwner)
    }
}