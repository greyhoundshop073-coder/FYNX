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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private const val FYNX_PREVIEW_MODE = false
private data class FynxNavItem(val key: String, val label: String, val icon: ImageVector)

@Composable
fun FynxApp(deepLinkDestination: FynxDeepLinkDestination? = null) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val appConnectionManager = remember(context) { FynxAppConnectionManager(context.applicationContext) }
    val appConnectionState by appConnectionManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf("Home") }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var profileUser by remember { mutableStateOf<String?>(null) }
    var statusOpenOwner by remember { mutableStateOf<String?>(null) }
    var statusOpenId by remember { mutableStateOf<String?>(null) }
    var postOpenId by remember { mutableStateOf<String?>(null) }
    var postOpenCommentId by remember { mutableStateOf<String?>(null) }
    var callTarget by remember { mutableStateOf<String?>(null) }
    var callVideo by remember { mutableStateOf(false) }
    var marketplaceListingId by remember { mutableStateOf<String?>(null) }
    var openChatMarketplaceListingId by remember { mutableStateOf<String?>(null) }
    var authSession by remember { mutableStateOf(if (FYNX_PREVIEW_MODE) AuthSession(AuthState.SIGNED_IN, "preview") else { val stored = FynxAuthStore.load(context); if (stored.state == AuthState.SIGNED_IN && FynxBackendClient.hasAccessToken(context)) stored else AuthSession() }) }
    var adminRole by remember { mutableStateOf<String?>(null) }
    var notifications by remember { mutableStateOf(FynxNotificationStore.load(context)) }
    var remoteUnreadCount by remember { mutableIntStateOf(-1) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    var openProfileSettings by remember { mutableStateOf(false) }
    var profileVersion by remember { mutableIntStateOf(0) }
    var remoteMyPhotoId: String? by remember(authSession.username, profileVersion) { mutableStateOf(FynxProfileRemoteClient.cachedProfilePhotoId(context, authSession.username ?: "")) }
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
        if (authSession.state == AuthState.SIGNED_IN && FynxBackendClient.hasAccessToken(context)) {
            lifecycleOwner.lifecycle.addObserver(appConnectionManager)
        }
        onDispose {
            appConnectionManager.stop()
            lifecycleOwner.lifecycle.removeObserver(appConnectionManager)
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
            FynxDeepLinkDestination.Home -> { openChat = null; openGroup = null; profileUser = null; selected = "Home" }
            is FynxDeepLinkDestination.Profile -> { openChat = null; openGroup = null; profileUser = destination.username; selected = "Home" }
            is FynxDeepLinkDestination.Chat -> {
                openChatMarketplaceListingId = destination.marketplaceListingId
                val normalized = destination.username.removePrefix("@").trim()
                if (normalized.isNotBlank()) {
                    val local = FynxChatStore.loadPreviews(context).firstOrNull { it.username.removePrefix("@").equals(normalized, true) }
                    val remote = if (local == null) FynxSocialClient.searchUsers(context, normalized).getOrNull()?.firstOrNull { it.username.removePrefix("@").equals(normalized, true) } else null
                    openGroup = null; profileUser = null
                    openChat = local ?: remote?.let { user -> ChatPreview(name = user.displayName.ifBlank { normalized }, username = user.username.removePrefix("@").let { "@$it" }, lastMessage = "Start a conversation", time = "Now", avatarUri = user.profilePhotoMediaId?.trim()?.takeIf { it.isNotBlank() }?.let { "/api/media/$it" }) }
                        ?: authSession.username?.takeIf { it.removePrefix("@").equals(normalized, true) }?.let { ChatPreview(name = it.removePrefix("@"), username = "@${it.removePrefix("@")}", lastMessage = "Start a conversation", time = "Now") }
                    if (openChat != null) FynxChatStore.savePreview(context, openChat!!)
                }
            }
            is FynxDeepLinkDestination.Call -> { val normalized = destination.username.removePrefix("@").trim(); if (normalized.isNotBlank()) { callTarget = "@${normalized.lowercase()}"; callVideo = destination.video; openChat = null; openGroup = null; profileUser = null; selected = "Calls" } }
            is FynxDeepLinkDestination.Group -> { openChat = null; profileUser = null; openGroup = destination.id }
            is FynxDeepLinkDestination.Post -> { postOpenId = destination.postId; postOpenCommentId = destination.commentId; selected = "Home" }
            is FynxDeepLinkDestination.Status -> { statusOpenId = destination.statusId; statusOpenOwner = null; selected = "Stories" }
            is FynxDeepLinkDestination.Marketplace -> { openChat = null; openGroup = null; profileUser = null; marketplaceListingId = destination.listingId; selected = "Marketplace" }
            FynxDeepLinkDestination.Stories -> selected = "Stories"
            FynxDeepLinkDestination.Money -> selected = "Money Tools"
            null -> Unit
        }
    }
    LaunchedEffect(selected) {
        if (selected != "Marketplace") marketplaceListingId = null
        if (selected != "Home") { postOpenId = null; postOpenCommentId = null }
        if (selected != "Stories") { statusOpenOwner = null; statusOpenId = null }
    }
    LaunchedEffect(Unit) { FynxNotificationFoundation.createChannels(context); notifications = FynxNotificationStore.load(context) }
    LaunchedEffect(authSession.state, authSession.username) {
        if (authSession.state != AuthState.SIGNED_IN || !FynxBackendClient.hasAccessToken(context)) return@LaunchedEffect
        while (true) {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                FynxNotificationRemoteClient.loadFeed(context).onSuccess { feed ->
                    remoteUnreadCount = feed.unreadCount
                    notifications = feed.notifications
                    FynxNotificationStore.save(context, feed.notifications)
                }
                kotlinx.coroutines.delay(15_000L)
            } else {
                kotlinx.coroutines.delay(1_000L)
            }
        }
    }
    LaunchedEffect(authSession.state, authSession.username, profileVersion) {
        remoteMyPhotoId = FynxProfileRemoteClient.cachedProfilePhotoId(context, authSession.username ?: "")
        if (authSession.state == AuthState.SIGNED_IN && !authSession.username.isNullOrBlank() && FynxBackendClient.hasAccessToken(context)) FynxProfileRemoteClient.get(context, authSession.username!!).onSuccess { remoteMyPhotoId = it.profilePhotoMediaId }
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
    BackHandler(enabled = openChat == null && openGroup == null && selected == "Contacts") { selected = "Chats" }
    BackHandler(enabled = openChat == null && openGroup == null && selected != "Home" && selected != "Contacts") { selected = "Home" }
    if (profileUser != null) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark", "Charcoal Black" -> true; else -> isSystemInDarkTheme() }) { OtherUserProfilePanel(username = profileUser!!, onBack = { profileUser = null }, onOpenStatus = { statusUsername -> profileUser = null; statusOpenOwner = statusUsername; selected = "Stories" }, onMessage = { username -> openChatMarketplaceListingId = null; val normalized = username.trim().let { if (it.startsWith("@")) it else "@$it" }; openChat = FynxChatStore.loadPreviews(context).firstOrNull { it.username.equals(normalized, true) } ?: ChatPreview(normalized.removePrefix("@").ifBlank { "FYNX user" }, normalized, "Start a conversation", "Now"); FynxChatStore.savePreview(context, openChat!!); profileUser = null }) }; return }
    if (openChat != null) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark", "Charcoal Black" -> true; else -> isSystemInDarkTheme() }) { ConversationPanel(chat = openChat!!, marketplaceListingId = openChatMarketplaceListingId, onBack = { openChat = null; openChatMarketplaceListingId = null }, onOpenProfile = { profileUser = it; openChat = null }, onVoiceCall = { callTarget = openChat!!.username; callVideo = false; openChat = null; selected = "Calls" }, onVideoCall = { callTarget = openChat!!.username; callVideo = true; openChat = null; selected = "Calls" }) }; return }
    if (openGroup != null) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark", "Charcoal Black" -> true; else -> isSystemInDarkTheme() }) { FynxGroupConversationPanel(groupId = openGroup!!, currentUsername = authSession.username?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview", onBack = { openGroup = null }) }; return }
    FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark", "Charcoal Black" -> true; else -> isSystemInDarkTheme() }) {
        val mainIndex = mainNav.indexOfFirst { it.key == selected }.coerceAtLeast(0)
        var homeChromeProgress by remember { mutableFloatStateOf(0f) }
        var homeChromeHidden by remember { mutableStateOf(false) }
        val homeChromeMaxPx = with(LocalDensity.current) { 52.dp.toPx() }
        val homeScrollConnection = remember { object : NestedScrollConnection { override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset { if (selected != "Home" || source != NestedScrollSource.UserInput) return Offset.Zero; val delta = -(consumed.y + available.y); if (kotlin.math.abs(delta) > 0.5f) { homeChromeProgress = (homeChromeProgress + delta / homeChromeMaxPx).coerceIn(0f, 1f); when { !homeChromeHidden && homeChromeProgress >= 0.55f -> homeChromeHidden = true; homeChromeHidden && homeChromeProgress <= 0.35f -> homeChromeHidden = false } }; return Offset.Zero } } }
        LaunchedEffect(selected) { if (selected != "Home") { homeChromeProgress = 0f; homeChromeHidden = false } }
        val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val unread = if (remoteUnreadCount >= 0) remoteUnreadCount else notifications.unreadNotificationCount()
        val myProfile = remember(authSession.username, profileVersion) { FynxPreferencesStore.loadProfile(context, authSession.username) }
        val myPhoto = FynxPreferencesStore.loadProfilePhoto(context)
        Scaffold(modifier = Modifier.nestedScroll(homeScrollConnection), containerColor = MaterialTheme.colorScheme.background, topBar = { /* existing top bar */ })
    }
}

@Composable
private fun FynxLaunchPlaceholder() {}
