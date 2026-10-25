package com.fynx.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

private const val FYNX_PREVIEW_MODE = false
private data class FynxNavItem(val key: String, val label: String, val icon: ImageVector)

private fun fynxBackDestination(
    selected: String,
    notificationPreferencesOnly: Boolean,
    navigationHistory: List<String>
): String = when (selected) {
    "Contacts" -> "Chats"
    "Saved Posts", "Saved Products", "Invite", "Gifts", "To-Do", "Share", "Announcements", "Admin" -> "Features"
    "Privacy" -> "Profile"
    "Notifications" -> if (notificationPreferencesOnly) "Profile" else "Home"
    "Calendar" -> "Extra Tools"
    "Money Tools" -> "Features"
    "Stories", "Calls" -> "Home"
    "Groups" -> "Chats"
    "Business Account" -> "Features"
    "Advertising" -> "Business Account"
    "Advertising Dashboard" -> "Advertising"
    "Advertising AI", "AI Creation", "AI Photo Editor" -> "AI"
    "Seller Center" -> "Marketplace"
    else -> navigationHistory.getOrNull(navigationHistory.lastIndex - 1) ?: "Home"
}

@Composable
fun FynxApp(deepLinkDestination: FynxDeepLinkDestination? = null) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val appConnectionManager = remember(context) { FynxAppConnectionManager(context.applicationContext) }
    val appConnectionState by appConnectionManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf("Home") }
    var profileAvatarMenuOpen by remember { mutableStateOf(false) }
    val currentSelected by rememberUpdatedState(selected)
    var navigationHistory by remember { mutableStateOf(listOf("Home")) }
    var backNavigationRequested by remember { mutableStateOf(false) }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var profileUser by remember { mutableStateOf<String?>(null) }
    var statusOpenOwner by remember { mutableStateOf<String?>(null) }
    var statusOpenId by remember { mutableStateOf<String?>(null) }
    var postOpenId by remember { mutableStateOf<String?>(null) }
    var postOpenCommentId by remember { mutableStateOf<String?>(null) }
    var callTarget by remember { mutableStateOf<String?>(null) }
    var callVideo by remember { mutableStateOf(false) }
    var pendingIncomingCall by remember { mutableStateOf<FynxRealtimeClient.Event.Call?>(null) }
    var marketplaceListingId by remember { mutableStateOf<String?>(null) }
    var marketplaceAiListingId by remember { mutableStateOf<String?>(null) }
    var openChatMarketplaceListingId by remember { mutableStateOf<String?>(null) }
    var authSession by remember { mutableStateOf(if (FYNX_PREVIEW_MODE) AuthSession(AuthState.SIGNED_IN, "preview") else { val stored = FynxAuthStore.load(context); val debugOfflineSession = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && stored.state == AuthState.SIGNED_IN; if (stored.state == AuthState.SIGNED_IN && (FynxBackendClient.hasAccessToken(context) || debugOfflineSession)) stored else AuthSession() }) }
    var adminRole by remember { mutableStateOf<String?>(null) }
    var notifications by remember { mutableStateOf(FynxNotificationStore.load(context)) }
    var remoteUnreadCount by remember { mutableIntStateOf(-1) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    var openProfileSettings by remember { mutableStateOf(false) }
    var notificationPreferencesOnly by remember { mutableStateOf(false) }
    var profileVersion by remember { mutableIntStateOf(0) }
    var remoteMyPhotoId: String? by remember(authSession.username, profileVersion) { mutableStateOf(FynxProfileRemoteClient.cachedProfilePhotoId(context, authSession.username ?: "")) }
    var remoteMyProfileLoaded by remember(authSession.username, profileVersion) {
        mutableStateOf(FynxProfileRemoteClient.hasCachedProfilePhotoEntry(context, authSession.username ?: ""))
    }
    var aiCaptionDraft by remember { mutableStateOf<String?>(null) }
    var homeCameraRequest by remember { mutableIntStateOf(0) }
    var navigationDirection by remember { mutableIntStateOf(1) }
    DisposableEffect(Unit) {
        FynxStatusNavigation.opener = { username -> statusOpenOwner = username; statusOpenId = null; selected = "Stories" }
        onDispose { if (FynxStatusNavigation.opener != null) FynxStatusNavigation.opener = null }
    }
    DisposableEffect(Unit) {
        onDispose { appConnectionManager.dispose() }
    }
    DisposableEffect(lifecycleOwner, authSession.state) {
        // Backend reachability is a public health probe, so the app-level connection
        // indicator must run for the supported debug saved-device session too.
        // Requiring a bearer token here leaves that session stuck at "Waiting for network".
        if (authSession.state == AuthState.SIGNED_IN) {
            lifecycleOwner.lifecycle.addObserver(appConnectionManager)
        }
        onDispose {
            appConnectionManager.stop()
            lifecycleOwner.lifecycle.removeObserver(appConnectionManager)
        }
    }

    DisposableEffect(authSession.state, authSession.username) {
        if (authSession.state == AuthState.SIGNED_IN && FynxBackendClient.hasAccessToken(context)) {
            // Keep a call-signaling socket alive while the user browses any screen.
            // The dedicated Calls panel owns media/WebRTC once the invite is routed there.
            val incomingCallListener = FynxRealtimeClient(
                context = context,
                onMessage = {},
                onEvent = { event ->
                    if (event is FynxRealtimeClient.Event.Call &&
                        FynxIncomingCallRouting.shouldRouteInvite(
                            signalType = event.signalType,
                            currentScreen = currentSelected,
                            hasPendingIncomingCall = pendingIncomingCall != null
                        )
                    ) {
                        pendingIncomingCall = event
                        callTarget = null
                        callVideo = event.callType.equals("video", true)
                        openChat = null
                        openGroup = null
                        profileUser = null
                        selected = "Calls"
                    }
                }
            )
            incomingCallListener.connect()
            onDispose { incomingCallListener.close() }
        } else {
            onDispose {}
        }
    }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("fynx_preferences", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "appearance" -> appearance = FynxPreferencesStore.loadAppearance(context)
                "accent" -> accent = FynxPreferencesStore.loadAccent(context)
                "display_name", "username", "bio", "profile_photo_uri" -> profileVersion++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(deepLinkDestination) {
        when (val destination = deepLinkDestination) {
            is FynxDeepLinkDestination.Invite -> { inviteCode = destination.code; selected = "Invite" }
            FynxDeepLinkDestination.Home -> {
                openChat = null
                openGroup = null
                profileUser = null
                selected = "Home"
            }
            is FynxDeepLinkDestination.Profile -> {
                openChat = null
                openGroup = null
                profileUser = destination.username
                selected = "Home"
            }
            is FynxDeepLinkDestination.Chat -> {
                openChatMarketplaceListingId = destination.marketplaceListingId
                val normalized = destination.username.removePrefix("@").trim()
                if (normalized.isNotBlank()) {
                    // Resolve the visible route immediately. Previously this effect awaited
                    // the remote user search before setting openChat, so a slow/failed search
                    // left a valid deep link sitting on Home and made the conversation appear
                    // not to open at all. Match the Friends -> Chat path: show a provisional
                    // conversation while the authoritative recipient lookup runs.
                    val provisionalChat = ChatPreview(
                        name = normalized,
                        username = "@$normalized",
                        lastMessage = "",
                        time = "Now"
                    )
                    openGroup = null
                    profileUser = null
                    openChat = provisionalChat

                    val local = withContext(Dispatchers.IO) {
                        FynxChatStore.loadPreviews(context).firstOrNull {
                            it.username.removePrefix("@").equals(normalized, true)
                        }
                    }
                    val selfUsername = authSession.username?.removePrefix("@")?.trim()
                    val isSelfTarget = authSession.state == AuthState.SIGNED_IN &&
                        !selfUsername.isNullOrBlank() && selfUsername.equals(normalized, true)
                    // A self-chat smoke/deep link must not wait for remote user discovery:
                    // the signed-in account identity is already authoritative locally.
                    // In particular, an unavailable backend must not prevent the chat
                    // screen from opening for the current user.
                    val remote = if (local == null && !isSelfTarget) {
                        FynxSocialClient.searchUsers(context, normalized).getOrNull()
                            ?.firstOrNull { it.username.removePrefix("@").equals(normalized, true) }
                    } else null
                    val resolvedChat = local ?: remote?.let { user ->
                        ChatPreview(
                            name = user.displayName.ifBlank { normalized },
                            username = user.username.removePrefix("@").let { "@$it" },
                            lastMessage = "",
                            time = "Now",
                            avatarUri = user.profilePhotoMediaId?.trim()?.takeIf { it.isNotBlank() }?.let { "/api/media/$it" }
                        )
                    } ?: selfUsername?.takeIf { isSelfTarget }?.let {
                        ChatPreview(name = it, username = "@$it", lastMessage = "", time = "Now")
                    }
                    if (resolvedChat != null) {
                        openChat = resolvedChat
                        withContext(Dispatchers.IO) { FynxChatStore.savePreview(context, resolvedChat) }
                    }
                }
            }
            is FynxDeepLinkDestination.Call -> {
                val normalized = destination.username.removePrefix("@").trim()
                if (normalized.isNotBlank()) {
                    callTarget = "@${normalized.lowercase()}"
                    callVideo = destination.video
                    openChat = null
                    openGroup = null
                    profileUser = null
                    selected = "Calls"
                }
            }
            is FynxDeepLinkDestination.Group -> {
                openChat = null
                profileUser = null
                openGroup = destination.id
            }
            is FynxDeepLinkDestination.Post -> { postOpenId = destination.postId; postOpenCommentId = destination.commentId; selected = "Home" }
            is FynxDeepLinkDestination.Status -> { statusOpenId = destination.statusId; statusOpenOwner = null; selected = "Stories" }
            is FynxDeepLinkDestination.Marketplace -> {
                openChat = null
                openGroup = null
                profileUser = null
                marketplaceListingId = destination.listingId
                selected = "Marketplace"
            }
            FynxDeepLinkDestination.Stories -> selected = "Stories"
            FynxDeepLinkDestination.Money -> selected = "Money Tools"
            null -> Unit
        }
    }
    LaunchedEffect(selected) {
        if (selected in setOf("Home", "Chats", "Friends", "Marketplace", "Features")) {
            backNavigationRequested = false
            navigationHistory = listOf(selected)
        } else if (backNavigationRequested) {
            backNavigationRequested = false
            navigationHistory = (navigationHistory.dropLast(1).filterNot { it == selected } + selected).ifEmpty { listOf("Home") }
        } else if (navigationHistory.lastOrNull() != selected) {
            navigationHistory = (navigationHistory + selected).takeLast(30)
        }
        if (selected != "Marketplace") marketplaceListingId = null

        // Deep-link commands are one-shot navigation intents. Once the user leaves
        // the destination surface, discard any command that was not consumed so a
        // later recomposition/navigation cannot reopen an old post or status.
        if (selected != "Home") {
            postOpenId = null
            postOpenCommentId = null
        }
        if (selected != "Stories") {
            statusOpenOwner = null
            statusOpenId = null
        }
    }
    LaunchedEffect(Unit) { FynxNotificationFoundation.createChannels(context); notifications = FynxNotificationStore.load(context) }
    LaunchedEffect(authSession.state, authSession.username) {
        if (authSession.state != AuthState.SIGNED_IN || !FynxBackendClient.hasAccessToken(context)) return@LaunchedEffect
        while (true) {
            FynxNotificationRemoteClient.loadFeed(context).onSuccess { feed ->
                remoteUnreadCount = feed.unreadCount
                notifications = feed.notifications
                FynxNotificationStore.save(context, feed.notifications)
            }
            kotlinx.coroutines.delay(15_000L)
        }
    }
    LaunchedEffect(authSession.state, authSession.username, profileVersion) {
        remoteMyPhotoId = FynxProfileRemoteClient.cachedProfilePhotoId(context, authSession.username ?: "")
        remoteMyProfileLoaded = FynxProfileRemoteClient.hasCachedProfilePhotoEntry(context, authSession.username ?: "")
        if (authSession.state == AuthState.SIGNED_IN && !authSession.username.isNullOrBlank() && FynxBackendClient.hasAccessToken(context)) {
            FynxProfileRemoteClient.get(context, authSession.username!!).onSuccess {
                remoteMyPhotoId = it.profilePhotoMediaId
                remoteMyProfileLoaded = true
            }
        }
        adminRole = null
        if (!FYNX_PREVIEW_MODE && authSession.state == AuthState.SIGNED_IN && FynxBackendClient.hasAccessToken(context)) {
            FynxAdminClient.dashboard(context).onSuccess { dashboard -> adminRole = dashboard.role.takeIf { it == "OWNER" || it == "ADMIN" } }
            FynxBackendClient.currentUserId(context).onFailure { error -> if (FynxBackendClient.isUnauthorizedFailure(error)) { FynxBackendClient.saveAccessToken(context, null); FynxAuthStore.clear(context); authSession = AuthSession(); adminRole = null; selected = "Home"; openChat = null; openGroup = null; profileUser = null } }
        }
    }
    if (!FYNX_PREVIEW_MODE && authSession.state != AuthState.SIGNED_IN) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark", "Charcoal Black" -> true; else -> isSystemInDarkTheme() }) { FynxAuthGate { username -> FynxAuthStore.save(context, username); authSession = AuthSession(AuthState.SIGNED_IN, username) } }; return }
    if (selected == "Admin" && adminRole == null) selected = "Features"
    val mainNav = listOf(FynxNavItem("Home", "Home", Icons.Default.Home), FynxNavItem("Chats", "Chat", Icons.Default.ChatBubbleOutline), FynxNavItem("Friends", "Friends", Icons.Default.Person), FynxNavItem("Marketplace", "Marketplace", Icons.Default.ShoppingBag), FynxNavItem("Features", "More", Icons.Default.MoreHoriz))
    val isSecondary = selected !in mainNav.map { it.key }.toSet()
    BackHandler(enabled = profileUser != null) { profileUser = null }
    BackHandler(enabled = openChat != null) { openChat = null }
    BackHandler(enabled = openGroup != null && openChat == null) { openGroup = null }
    BackHandler(enabled = openChat == null && openGroup == null && selected !in mainNav.map { it.key }.toSet()) {
        backNavigationRequested = true
        selected = fynxBackDestination(selected, notificationPreferencesOnly, navigationHistory)
        if (notificationPreferencesOnly) notificationPreferencesOnly = false
    }
    if (profileUser != null) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark", "Charcoal Black" -> true; else -> isSystemInDarkTheme() }) { OtherUserProfilePanel(username = profileUser!!, onBack = { profileUser = null }, onOpenStatus = { statusUsername -> profileUser = null; statusOpenOwner = statusUsername; selected = "Stories" }, onMessage = { username ->
        val normalized = username.trim().let { if (it.startsWith("@")) it else "@$it" }
        openChatMarketplaceListingId = null
        scope.launch {
            val local = withContext(Dispatchers.IO) {
                FynxChatStore.loadPreviews(context).firstOrNull { it.username.equals(normalized, true) }
            }
            val chat = local ?: ChatPreview(normalized.removePrefix("@").ifBlank { "FYNX user" }, normalized, "", "Now")
            withContext(Dispatchers.IO) { FynxChatStore.savePreview(context, chat) }
            openChat = chat
            profileUser = null
        }
    }) }; return }
    if (openChat != null) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark", "Charcoal Black" -> true; else -> isSystemInDarkTheme() }) { ConversationPanel(chat = openChat!!, marketplaceListingId = openChatMarketplaceListingId, onBack = { openChat = null; openChatMarketplaceListingId = null }, onOpenProfile = { profileUser = it; openChat = null }, onVoiceCall = { callTarget = openChat!!.username; callVideo = false; openChat = null; selected = "Calls" }, onVideoCall = { callTarget = openChat!!.username; callVideo = true; openChat = null; selected = "Calls" }) }; return }
    if (openGroup != null) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark", "Charcoal Black" -> true; else -> isSystemInDarkTheme() }) { FynxGroupConversationPanel(groupId = openGroup!!, currentUsername = authSession.username?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview", onBack = { openGroup = null }) }; return }
    FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark", "Charcoal Black" -> true; else -> isSystemInDarkTheme() }) {
        val mainIndex = mainNav.indexOfFirst { it.key == selected }.coerceAtLeast(0)
        // Home uses one LazyColumn for the real feed. Observe its nested-scroll deltas
        // here so the existing header and floating navigation can get out of the way
        // without adding a second vertical scroll container or consuming feed gestures.
        var homeChromeProgress by remember { mutableFloatStateOf(0f) }
        var homeChromeHidden by remember { mutableStateOf(false) }
        val homeChromeMaxPx = with(LocalDensity.current) { 52.dp.toPx() }
        val homeScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (selected != "Home" || source != NestedScrollSource.UserInput) return Offset.Zero
                    // Observe the feed after it has handled the gesture. Never participate
                    // in pre-scroll so the feed keeps first ownership of vertical movement.
                    val delta = -(consumed.y + available.y)
                    if (kotlin.math.abs(delta) > 0.5f) {
                        homeChromeProgress = (homeChromeProgress + delta / homeChromeMaxPx).coerceIn(0f, 1f)
                        when {
                            !homeChromeHidden && homeChromeProgress >= 0.55f -> homeChromeHidden = true
                            homeChromeHidden && homeChromeProgress <= 0.35f -> homeChromeHidden = false
                        }
                    }
                    return Offset.Zero
                }
            }
        }
        LaunchedEffect(selected) {
            if (selected != "Home") {
                homeChromeProgress = 0f
                homeChromeHidden = false
            }
        }
        // When the IME is open, hide the floating navigation instead of moving it
        // over the Home feed. It returns automatically when the keyboard closes.
        val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val unread = if (remoteUnreadCount >= 0) remoteUnreadCount else notifications.unreadNotificationCount()
        val myProfile = remember(authSession.username, profileVersion) { FynxPreferencesStore.loadProfile(context, authSession.username) }
        val myPhoto = FynxPreferencesStore.loadProfilePhoto(context)
        Scaffold(
            modifier = Modifier.nestedScroll(homeScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
            if (selected == "Home") {
                // Home header is part of the normal top-bar flow, not a floating overlay.
                // This keeps the feed clear while preserving the profile, FYNX title,
                // camera, Settings and notification controls.
                Row(
                    Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .height(52.dp * (1f - homeChromeProgress))
                        .graphicsLayer { alpha = 1f - homeChromeProgress },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(48.dp).clickable {
                            selected = "Profile"
                            openProfileSettings = false
                        }.semantics(mergeDescendants = true) { contentDescription = "Open FYNX profile" },
                        contentAlignment = Alignment.Center
                    ) {
                        if (remoteMyPhotoId != null) FynxRemoteProfileAvatar(
                            mediaId = remoteMyPhotoId,
                            contentDescription = myProfile.displayName,
                            modifier = Modifier.size(38.dp),
                            ownerUsername = myProfile.username,
                            allowImagePreview = false
                        )
                        else if (!remoteMyProfileLoaded) FynxProfileImage(myProfile.displayName, myPhoto, Modifier.size(38.dp))
                        else FynxAvatar(myProfile.displayName, Modifier.size(38.dp))
                    }
                    // The avatar and overflow menu are fixed-width siblings. Keep the
                    // brand label in the remaining space; nesting another weighted Row
                    // here makes Compose allocate overlapping bounds on narrow screens.
                    Box(
                        Modifier.size(48.dp).clickable { profileAvatarMenuOpen = true }
                            .semantics(mergeDescendants = true) { contentDescription = "Profile menu, three dots" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        DropdownMenu(
                            expanded = profileAvatarMenuOpen,
                            onDismissRequest = { profileAvatarMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open profile") },
                                onClick = { profileAvatarMenuOpen = false; selected = "Profile"; openProfileSettings = false },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Profile settings") },
                                onClick = { profileAvatarMenuOpen = false; selected = "Profile"; openProfileSettings = true },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                        }
                    }
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (appConnectionState == FynxAppConnectionManager.State.CONNECTED) {
                            Text("FYNX", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Verified, "Verified FYNX", tint = Color(0xFF1877F2), modifier = Modifier.size(18.dp))
                        } else {
                            var connectingDotCount by remember { mutableIntStateOf(1) }
                            LaunchedEffect(appConnectionState) {
                                if (appConnectionState != FynxAppConnectionManager.State.CONNECTING) {
                                    connectingDotCount = 1
                                    return@LaunchedEffect
                                }
                                while (true) {
                                    delay(450L)
                                    connectingDotCount = connectingDotCount % 3 + 1
                                }
                            }
                            Text(
                                if (appConnectionState == FynxAppConnectionManager.State.WAITING_FOR_NETWORK) {
                                    "Waiting for network..."
                                } else {
                                    "Connecting" + ".".repeat(connectingDotCount)
                                },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { selected = "Search" },
                            modifier = Modifier.size(48.dp).semantics(mergeDescendants = true) {
                                contentDescription = "Search FYNX"
                            }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                        IconButton(
                            onClick = { homeCameraRequest++ },
                            modifier = Modifier.size(48.dp).semantics(mergeDescendants = true) {
                                contentDescription = "Open FYNX camera"
                            }
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                        }
                        BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toString()) } }) {
                            IconButton(onClick = { notificationPreferencesOnly = false; selected = "Notifications" }) { Icon(Icons.Default.Notifications, "Notifications") }
                        }
                    }
                }
            } else if (selected == "Friends") {
                Row(
                    Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.size(48.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("Friends", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.size(48.dp))
                }
            } else if (selected in mainNav.map { it.key }) {
                Row(
                    Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.size(48.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            when (selected) {
                                "Chats" -> "Chats"
                                "Marketplace" -> "Marketplace"
                                "Features" -> "More"
                                else -> selected
                            },
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(Modifier.size(48.dp))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            backNavigationRequested = true
                            selected = fynxBackDestination(selected, notificationPreferencesOnly, navigationHistory)
                            if (notificationPreferencesOnly) notificationPreferencesOnly = false
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Back to ${fynxBackDestination(selected, notificationPreferencesOnly, navigationHistory)}"
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to ${fynxBackDestination(selected, notificationPreferencesOnly, navigationHistory)}"
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            when (selected) {
                                "Marketplace" -> "Marketplace"
                                "Contacts" -> "Phone Contacts"
                                "Money Tools" -> "Money Center"
                                "Privacy" -> "Privacy & Safety"
                                "Seller Center" -> "Seller Center"
                                "Business Account" -> "Business Account"
                                "Advertising" -> "Advertising"
                                "Advertising Dashboard" -> "Advertising Dashboard"
                                "Advertising AI" -> "FYNX AI"
                                "AI" -> "FYNX AI"
                                "AI Creation" -> "AI Creation"
                                "AI Photo Editor" -> "AI Photo Editor"
                                "Announcements" -> "Official FYNX Announcements"
                                "Admin" -> "Admin Control Center"
                                else -> selected
                            },
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(Modifier.size(48.dp))
                }
            }
        }, bottomBar = {
            // Keep the navigation in its rounded floating surface.
            // Stay above the system navigation area, but do not follow the IME.
            // When the keyboard opens, the whole surface disappears so no Home
            // post/content is covered. It reappears when the keyboard closes.
            if (!isKeyboardVisible && selected in mainNav.map { it.key }) {
                AnimatedVisibility(
                    visible = selected != "Home" || !homeChromeHidden,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(8.dp, RoundedCornerShape(32.dp)),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        tonalElevation = 0.dp
                    ) {
                        NavigationBar(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 0.dp,
                            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                        ) {
                            mainNav.forEach { item ->
                                NavigationBarItem(
                                    // Preserve the Chat tab's native selection/click semantics.
                                    // Merging its subtree made accessibility clients see the label
                                    // without the NavigationBarItem action.
                                    modifier = Modifier.height(64.dp).then(
                                        if (item.key == "Chats") Modifier
                                        else Modifier.semantics(mergeDescendants = true) {
                                            contentDescription = item.label
                                        }
                                    ),
                                    selected = selected == item.key,
                                    onClick = { notificationPreferencesOnly = false; selected = item.key },
                                    icon = {
                                        Icon(
                                            item.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            item.label,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                    }
                }
            }
        }) { padding ->
            val screenContentModifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Individual screens own their intentional horizontal content spacing.
                // Do not add a second global side gutter here: it was shrinking every
                // screen and making phone controls/composers unnecessarily cramped.
                .padding(vertical = 6.dp)
                .then(if (selected == "Home") Modifier else Modifier.navigationBarsPadding())
            Box(screenContentModifier) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        val forward = navigationDirection > 0
                        (slideInHorizontally(initialOffsetX = { width -> if (forward) width else -width }) + fadeIn()) togetherWith
                            (slideOutHorizontally(targetOffsetX = { width -> if (forward) -width else width }) + fadeOut())
                    },
                    label = "FYNX page swipe"
                ) { page ->
                    when (page) {
            "Home" -> FynxHomeSocialHubPanel(currentUsername = authSession.username ?: "preview", networkReady = appConnectionState == FynxAppConnectionManager.State.CONNECTED, initialCaption = aiCaptionDraft, onCaptionConsumed = { aiCaptionDraft = null }, cameraRequest = homeCameraRequest, onCameraRequestConsumed = { homeCameraRequest = 0 }, onOpenChats = { selected = "Chats" }, onOpenStories = { statusOpenOwner = null; selected = "Stories" }, onOpenStatusOwner = { statusOpenOwner = it; selected = "Stories" }, onOpenProfile = { selected = "Profile" }, onOpenMarketplace = { selected = "Marketplace" }, onOpenNotifications = { selected = "Notifications" }, onOpenFindPeople = { selected = "Friends" }, onOpenAi = { selected = "AI" }, onOpenAuthorProfile = { profileUser = it }, initialPostId = postOpenId, initialCommentId = postOpenCommentId, onInitialPostConsumed = { postOpenId = null; postOpenCommentId = null })
            "Search" -> FynxUniversalSearchPanel(
                onOpenProfile = { username -> profileUser = username; selected = "Home" }
            )
            "Chats" -> ChatsPanel(onOpenChat = { openChat = it }, onOpenGroup = { openGroup = it }, onCreateGroup = { selected = "Groups" }, onOpenContacts = { selected = "Contacts" })
            "Friends" -> FriendsPanel(
                onOpenProfile = { profileUser = it },
                onOpenChat = { username ->
                    val normalized = username.removePrefix("@").trim()
                    if (normalized.isNotBlank()) {
                        // Render immediately; do not parse persisted chat history on the UI thread.
                        val initialChat = ChatPreview(normalized, "@" + normalized, "", "Now")
                        openChatMarketplaceListingId = null
                        openChat = initialChat
                        scope.launch {
                            val local = withContext(Dispatchers.IO) {
                                FynxChatStore.loadPreviews(context).firstOrNull {
                                    it.username.removePrefix("@").equals(normalized, true)
                                }
                            }
                            val baseChat = local ?: initialChat
                            if (local != null && openChat?.username.equals(initialChat.username, true)) openChat = local
                            withContext(Dispatchers.IO) { FynxChatStore.savePreview(context, baseChat) }
                            if (local == null) {
                                val user = FynxSocialClient.searchUsers(context, normalized).getOrNull()
                                    ?.firstOrNull { it.username.removePrefix("@").equals(normalized, true) }
                                if (user != null) {
                                    val resolvedChat = baseChat.copy(
                                        name = user.displayName.ifBlank { normalized },
                                        username = "@" + user.username.removePrefix("@"),
                                        avatarUri = user.profilePhotoMediaId?.trim()?.takeIf { it.isNotBlank() }?.let { "/api/media/$it" }
                                    )
                                    withContext(Dispatchers.IO) { FynxChatStore.savePreview(context, resolvedChat) }
                                    if (openChat?.username.equals(initialChat.username, true)) openChat = resolvedChat
                                }
                            }
                        }
                    }
                }            )
            "Contacts" -> FynxContactsPanel(onBack = { selected = "Chats" }, onVoiceCall = { callTarget = it; callVideo = false; selected = "Calls" }, onVideoCall = { callTarget = it; callVideo = true; selected = "Calls" })
            "Marketplace" -> FynxMarketplacePanel(currentUsername = authSession.username ?: "preview", onOpenProfile = { profileUser = it }, onOpenAi = { listingId -> marketplaceAiListingId = listingId; selected = "AI" }, onLiveProof = { username -> callTarget = username; callVideo = true; selected = "Calls" }, onOpenMyShop = { selected = "Seller Center" }, onOpenSavedProducts = { selected = "Saved Products" }, initialListingId = marketplaceListingId)
            "Money Tools" -> MoneyCenterPanel()
            "Business Account" -> FynxBusinessAccountPanel(onBack = { selected = "Features" }, onOpenAdvertising = { selected = "Advertising" }, onOpenDashboard = { selected = "Advertising Dashboard" })
            "Features" -> FynxFeaturesPanel(isAdmin = adminRole != null, onSelect = { if (it != "Admin" || adminRole != null) selected = it })
            "Extra Tools" -> FynxExtraToolsPanel(onOpenCalendar = { selected = "Calendar" })
            "Calendar" -> CalendarPanel()
            "Stories" -> FynxStatusHubPanel(
                openOwnerUsername = statusOpenOwner,
                openStatusId = statusOpenId,
                onOpenCommandConsumed = { statusOpenOwner = null; statusOpenId = null },
                onOpenMessage = { username ->
                    val normalized = username.removePrefix("@").trim()
                    if (normalized.isNotBlank()) scope.launch {
                        val local = withContext(Dispatchers.IO) {
                            FynxChatStore.loadPreviews(context).firstOrNull {
                                it.username.removePrefix("@").equals(normalized, true)
                            }
                        }
                        val remote = if (local == null) FynxSocialClient.searchUsers(context, normalized).getOrNull()
                            ?.firstOrNull { it.username.removePrefix("@").equals(normalized, true) } else null
                        val chat = local ?: remote?.let { user ->
                            ChatPreview(
                                name = user.displayName.ifBlank { normalized },
                                username = "@${user.username.removePrefix("@")}",
                                lastMessage = "",
                                time = "Now",
                                avatarUri = user.profilePhotoMediaId?.trim()?.takeIf { it.isNotBlank() }?.let { "/api/media/$it" }
                            )
                        }
                        // Do not manufacture a conversation when lookup fails (for example,
                        // while the backend is unavailable). Only navigate to a verified user.
                        if (chat != null) {
                            withContext(Dispatchers.IO) { FynxChatStore.savePreview(context, chat) }
                            openGroup = null
                            profileUser = null
                            openChat = chat
                        }
                    }
                }
            )
            "Gifts" -> GiftsPanel()
            "Groups" -> FynxGroupsPanel(currentUsername = authSession.username?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview", onOpenGroup = { openGroup = it })
            "Notifications" -> NotificationPanel(
                notifications = notifications,
                preferencesOnly = notificationPreferencesOnly,
                onBack = { selected = if (notificationPreferencesOnly) "Profile" else "Home"; notificationPreferencesOnly = false },
                onNotificationRead = { notifications = FynxNotificationStore.load(context) },
                onMarkAllRead = { notifications = FynxNotificationStore.load(context); remoteUnreadCount = 0 },
                onUnreadCountChanged = { remoteUnreadCount = it },
                onNotificationOpen = { notification ->
                    val route = notification.route?.takeIf { it.isNotBlank() } ?: when (notification.type) {
                        FynxNotificationType.FOLLOW, FynxNotificationType.FRIEND_REQUEST -> notification.sourceUsername?.takeIf { it.isNotBlank() }?.let { "fynx://profile/" + Uri.encode(it) }
                        FynxNotificationType.MESSAGE -> notification.sourceUsername?.takeIf { it.isNotBlank() }?.let { "fynx://chat/" + Uri.encode(it) }
                        FynxNotificationType.GROUP -> notification.targetId?.takeIf { it.isNotBlank() }?.let { "fynx://group/" + Uri.encode(it) }
                        FynxNotificationType.STORY -> notification.route?.takeIf { it.isNotBlank() } ?: notification.targetId?.takeIf { it.isNotBlank() }?.let { "fynx://status/" + Uri.encode(it) }
                        FynxNotificationType.COMMENT, FynxNotificationType.REACTION -> notification.route?.takeIf { it.isNotBlank() } ?: notification.targetId?.takeIf { it.isNotBlank() }?.let { "fynx://post/" + Uri.encode(it) }
                        else -> "fynx://home"
                    } ?: "fynx://home"
                    when (val destination = FynxDeepLinkParser.parse(Uri.parse(route))) {
                        is FynxDeepLinkDestination.Profile -> { profileUser = destination.username; selected = "Home" }
                        is FynxDeepLinkDestination.Chat -> {
                            val normalized = destination.username.removePrefix("@").trim()
                            if (normalized.isNotBlank()) {
                                // Show the destination immediately; remote lookup must not leave a
                                // notification tap stranded on the inbox while the network waits.
                                val provisional = ChatPreview(
                                    name = normalized,
                                    username = "@$normalized",
                                    lastMessage = "",
                                    time = "Now"
                                )
                                openGroup = null
                                profileUser = null
                                openChatMarketplaceListingId = destination.marketplaceListingId
                                openChat = provisional
                                scope.launch {
                                    val local = withContext(Dispatchers.IO) {
                                        FynxChatStore.loadPreviews(context).firstOrNull {
                                            it.username.removePrefix("@").equals(normalized, true)
                                        }
                                    }
                                    val remote = if (local == null) FynxSocialClient.searchUsers(context, normalized)
                                        .getOrNull()?.firstOrNull { it.username.removePrefix("@").equals(normalized, true) }
                                    else null
                                    val resolvedChat = local ?: remote?.let { user ->
                                        ChatPreview(
                                            name = user.displayName.ifBlank { normalized },
                                            username = "@" + user.username.removePrefix("@"),
                                            lastMessage = "",
                                            time = "Now",
                                            avatarUri = user.profilePhotoMediaId?.let { "/api/media/" + it }
                                        )
                                    }
                                    if (resolvedChat != null) {
                                        withContext(Dispatchers.IO) { FynxChatStore.savePreview(context, resolvedChat) }
                                        if (openChat?.username.equals(provisional.username, true)) openChat = resolvedChat
                                    }
                                }
                            }
                        }
                        is FynxDeepLinkDestination.Call -> { callTarget = destination.username; callVideo = destination.video; selected = "Calls" }
                        is FynxDeepLinkDestination.Group -> { openChat = null; profileUser = null; openChatMarketplaceListingId = null; openGroup = destination.id }
                        is FynxDeepLinkDestination.Post -> { postOpenId = destination.postId; postOpenCommentId = destination.commentId; selected = "Home" }
                        is FynxDeepLinkDestination.Status -> { statusOpenId = destination.statusId; statusOpenOwner = null; selected = "Stories" }
                        is FynxDeepLinkDestination.Marketplace -> { marketplaceListingId = destination.listingId; selected = "Marketplace" }
                        FynxDeepLinkDestination.Stories -> selected = "Stories"
                        FynxDeepLinkDestination.Money -> selected = "Money Tools"
                        FynxDeepLinkDestination.Home, is FynxDeepLinkDestination.Invite, null -> selected = "Home"
                    }
                }
            )
            "Share" -> FynxSharePanel()
            "Invite" -> FynxInvitePanel(code = inviteCode, onShare = { FynxShareActions.share(context, FynxShareActions.defaultPayload()) }, onBack = { selected = "Features" })
            "Calls" -> FynxCallsPanel(initialName = pendingIncomingCall?.fromUsername?.removePrefix("@")?.takeIf { it.isNotBlank() } ?: pendingIncomingCall?.fromUserId ?: callTarget, initialVideo = pendingIncomingCall?.callType?.equals("video", true) ?: callVideo, initialOutgoing = callTarget != null && pendingIncomingCall == null, initialIncomingCallId = pendingIncomingCall?.callId, initialIncomingUserId = pendingIncomingCall?.fromUserId, currentUsername = authSession.username?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }, onIncomingCallConsumed = { callId -> if (pendingIncomingCall?.callId == callId) pendingIncomingCall = null })
            "To-Do" -> TodoPanel()
            "Privacy" -> FynxPrivacySettingsPanel(onBack = { selected = "Profile" })
            "Saved Posts" -> FynxSavedPostsPanel(onOpenAuthorProfile = { profileUser = it })
            "Saved Products" -> FynxSavedMarketplaceProductsPanel(onOpenListing = { listingId -> marketplaceListingId = listingId; selected = "Marketplace" })
            "Seller Center" -> FynxMarketplaceSellerCenterPanel()
            "AI" -> FynxAiAssistantPanel(onOpenDestination = { destination -> selected = destination }, initialMarketplaceListingId = marketplaceAiListingId)
            "AI Creation" -> { selected = "AI" }
            "AI Photo Editor" -> { selected = "AI" }
            "Advertising" -> FynxAdvertisingCampaignPanel(currentUsername = authSession.username ?: "preview")
            "Advertising Dashboard" -> FynxAdvertisingDashboardPanel()
            "Advertising AI" -> { selected = "AI" }
            "Announcements" -> FynxAnnouncementsPanel()
            "Admin" -> if (adminRole != null) FynxAdminControlCenterPanel()
            "Profile" -> ProfilePanel(session = authSession, openSettingsInitially = openProfileSettings, onSettingsClosed = { openProfileSettings = false; profileVersion++ }, onAppearanceChanged = { appearance = it; FynxPreferencesStore.saveAppearance(context, it) }, onAccentChanged = { accent = it }, onOpenPrivacy = { openProfileSettings = false; selected = "Privacy" }, onOpenNotifications = { openProfileSettings = false; notificationPreferencesOnly = true; selected = "Notifications" })
            else -> FynxHomeSocialHubPanel(currentUsername = authSession.username ?: "preview", networkReady = appConnectionState == FynxAppConnectionManager.State.CONNECTED)
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxFeaturesPanel(isAdmin: Boolean, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val features = buildList {
        add(Triple("Marketplace", "Marketplace", Icons.Default.ShoppingBag)); add(Triple("Calls", "Voice & Video Calls", Icons.Default.Call)); add(Triple("Notifications", "Notifications", Icons.Default.Notifications)); add(Triple("Gifts", "Gifts", Icons.Default.CardGiftcard)); add(Triple("Share", "Share & Invite", Icons.Default.Share)); add(Triple("To-Do", "To-Do", Icons.Default.CheckCircle)); add(Triple("Calendar", "Calendar", Icons.Default.DateRange)); add(Triple("Money Tools", "Money Center", Icons.Default.AccountBalanceWallet)); add(Triple("Extra Tools", "Extra Tools", Icons.Default.Build)); add(Triple("Privacy", "Privacy & Safety", Icons.Default.Lock)); add(Triple("Saved Posts", "Saved Posts", Icons.Default.Bookmark)); add(Triple("Saved Products", "Saved Marketplace Products", Icons.Default.BookmarkBorder)); add(Triple("Seller Center", "Manage Marketplace Listings", Icons.Default.Inventory2)); add(Triple("Business Account", "Business & Creator Profile", Icons.Default.Storefront)); add(Triple("Advertising", "Create & Manage Advertising", Icons.Default.Campaign)); add(Triple("Advertising Dashboard", "Advertising Analytics Dashboard", Icons.Default.Analytics)); add(Triple("AI", "FYNX AI Assistant", Icons.Default.AutoAwesome)); add(Triple("Announcements", "Official FYNX Announcements", Icons.Default.Campaign)); if (isAdmin) add(Triple("Admin", "Owner / Admin Control Center", Icons.Default.AdminPanelSettings))
    }
    val visible = features.filter { it.second.contains(query.trim(), true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) { Text("FYNX Features", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Access your tools in one place. Business & Advertising connects your professional identity, products and campaigns.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); FynxFeatureSearchField(query, { query = it }); Spacer(Modifier.height(8.dp)); LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(visible, key = { it.first }) { feature -> Card(onClick = { onSelect(feature.first) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f))) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = FynxDesign.ControlShape, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(feature.third, feature.second, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(21.dp)) }; Spacer(Modifier.width(12.dp)); Text(feature.second, style = MaterialTheme.typography.titleMedium) } } } } }
}