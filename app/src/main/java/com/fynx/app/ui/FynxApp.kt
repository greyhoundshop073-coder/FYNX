package com.fynx.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val FYNX_PREVIEW_MODE = false
private data class FynxNavItem(val key: String, val label: String, val icon: ImageVector)

@Composable
fun FynxApp(deepLinkDestination: FynxDeepLinkDestination? = null) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf("Home") }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var profileUser by remember { mutableStateOf<String?>(null) }
    var callTarget by remember { mutableStateOf<String?>(null) }
    var callVideo by remember { mutableStateOf(false) }
    var authSession by remember { mutableStateOf(if (FYNX_PREVIEW_MODE) AuthSession(AuthState.SIGNED_IN, "preview") else { val stored = FynxAuthStore.load(context); if (stored.state == AuthState.SIGNED_IN && FynxBackendClient.hasAccessToken(context)) stored else AuthSession() }) }
    var notifications by remember { mutableStateOf(FynxNotificationStore.load(context)) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    var openProfileSettings by remember { mutableStateOf(false) }
    LaunchedEffect(deepLinkDestination) { when (val destination = deepLinkDestination) { is FynxDeepLinkDestination.Invite -> { inviteCode = destination.code; selected = "Invite" }; FynxDeepLinkDestination.Home -> selected = "Home"; null -> Unit } }
    LaunchedEffect(Unit) { FynxNotificationFoundation.createChannels(context); notifications = FynxNotificationStore.load(context) }
    if (!FYNX_PREVIEW_MODE && authSession.state != AuthState.SIGNED_IN) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark" -> true; else -> isSystemInDarkTheme() }) { FynxAuthGate { username -> FynxAuthStore.save(context, username); authSession = AuthSession(AuthState.SIGNED_IN, username) } }; return }
    val mainNav = listOf(FynxNavItem("Home", "Home", Icons.Default.Home), FynxNavItem("Chats", "Chats", Icons.Default.ChatBubbleOutline), FynxNavItem("Friends", "Friends", Icons.Default.Person), FynxNavItem("Marketplace", "Market", Icons.Default.ShoppingBag), FynxNavItem("Money Tools", "Money", Icons.Default.AccountBalanceWallet), FynxNavItem("Features", "More", Icons.Default.MoreHoriz))
    val isSecondary = selected !in mainNav.map { it.key }.toSet()
    BackHandler(enabled = profileUser != null) { profileUser = null }
    BackHandler(enabled = openChat != null) { openChat = null }
    BackHandler(enabled = openGroup != null && openChat == null) { openGroup = null }
    BackHandler(enabled = openChat == null && openGroup == null && selected != "Home") { selected = "Home" }
    if (profileUser != null) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark" -> true; else -> isSystemInDarkTheme() }) { OtherUserProfilePanel(username = profileUser!!, onBack = { profileUser = null }, onMessage = { username -> val normalized = username.trim().let { if (it.startsWith("@")) it else "@$it" }; openChat = FynxChatStore.loadPreviews(context).firstOrNull { it.username.equals(normalized, true) } ?: ChatPreview(normalized.removePrefix("@").ifBlank { "FYNX user" }, normalized, "Start a conversation", "Now"); FynxChatStore.savePreview(context, openChat!!); profileUser = null }) }; return }
    if (openChat != null) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark" -> true; else -> isSystemInDarkTheme() }) { ConversationPanel(chat = openChat!!, onBack = { openChat = null }, onOpenProfile = { profileUser = it; openChat = null }, onVoiceCall = { callTarget = openChat!!.name; callVideo = false; openChat = null; selected = "Calls" }, onVideoCall = { callTarget = openChat!!.name; callVideo = true; openChat = null; selected = "Calls" }) }; return }
    if (openGroup != null) { FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark" -> true; else -> isSystemInDarkTheme() }) { FynxGroupConversationPanel(groupId = openGroup!!, currentUsername = authSession.username?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview", onBack = { openGroup = null }) }; return }
    FynxTheme(accent = accent, darkMode = when (appearance) { "Light" -> false; "Dark" -> true; else -> isSystemInDarkTheme() }) {
        val mainIndex = mainNav.indexOfFirst { it.key == selected }.coerceAtLeast(0)
        val unread = notifications.unreadNotificationCount()
        val myProfile = remember(authSession.username) { FynxPreferencesStore.loadProfile(context, authSession.username) }
        val myPhoto = FynxPreferencesStore.loadProfilePhoto(context)
        Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { if (selected == "Home") { IconButton(onClick = { selected = "Profile"; openProfileSettings = false }) { FynxProfileImage(myProfile.displayName, myPhoto, Modifier.size(40.dp)) }; Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("FYNX", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }; IconButton(onClick = { selected = "Profile"; openProfileSettings = true }) { Icon(Icons.Default.Settings, "Settings") }; BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toString()) } }) { IconButton(onClick = { selected = "Notifications" }) { Icon(Icons.Default.Notifications, "Notifications") } } } else if (selected == "Friends") { IconButton(onClick = { selected = "Profile"; openProfileSettings = false }) { FynxProfileImage(myProfile.displayName, myPhoto, Modifier.size(40.dp)) }; Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("Friends", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }; Spacer(Modifier.size(48.dp)) } else { if (isSecondary) IconButton(onClick = { selected = "Home" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } else Spacer(Modifier.size(48.dp)); Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(when (selected) { "Marketplace" -> "Marketplace"; "Money Tools" -> "Money Center"; "Privacy" -> "Privacy & Safety"; else -> selected }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }; Spacer(Modifier.size(48.dp)) } } }, bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) { mainNav.forEach { item -> NavigationBarItem(selected = selected == item.key, onClick = { selected = item.key }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.secondaryContainer, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant)) } } }) { padding -> Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 6.dp).pointerInput(selected) { var drag = 0f; detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { _, amount -> drag += amount }, onDragEnd = { if (kotlin.math.abs(drag) >= 80f) { val next = if (drag < 0) (mainIndex + 1).coerceAtMost(mainNav.lastIndex) else (mainIndex - 1).coerceAtLeast(0); selected = mainNav[next].key } }) }) { when (selected) {
            "Home" -> FynxHomeSocialHubPanel(currentUsername = authSession.username ?: "preview", onOpenChats = { selected = "Chats" }, onOpenStories = { selected = "Stories" }, onOpenProfile = { selected = "Profile" }, onOpenMarketplace = { selected = "Marketplace" }, onOpenNotifications = { selected = "Notifications" }, onOpenFindPeople = { selected = "Friends" })
            "Chats" -> ChatsPanel(onOpenChat = { openChat = it }, onOpenGroup = { openGroup = it }, onCreateGroup = { selected = "Groups" })
            "Friends" -> FriendsPanel(onOpenProfile = { profileUser = it })
            "Marketplace" -> FynxMarketplaceRemotePanel(currentUsername = authSession.username ?: "preview", onOpenProfile = { profileUser = it })
            "Money Tools" -> MoneyCenterPanel()
            "Features" -> FynxFeaturesPanel(onSelect = { selected = it })
            "Extra Tools" -> FynxExtraToolsPanel(onOpenCalendar = { selected = "Calendar" })
            "Calendar" -> CalendarPanel()
            "Stories" -> FynxStatusHubPanel()
            "Gifts" -> GiftsPanel()
            "Groups" -> FynxGroupsPanel(currentUsername = authSession.username?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview", onOpenGroup = { openGroup = it })
            "Notifications" -> NotificationPanel(notifications = notifications, onBack = { selected = "Home" }, onNotificationRead = { notifications = FynxNotificationStore.load(context) }, onMarkAllRead = { notifications = FynxNotificationStore.load(context) })
            "Share" -> FynxSharePanel()
            "Invite" -> FynxInvitePanel(code = inviteCode, onShare = { FynxShareActions.share(context, FynxShareActions.defaultPayload()) }, onBack = { selected = "Features" })
            "Calls" -> FynxCallsPanel(initialName = callTarget, initialVideo = callVideo)
            "To-Do" -> TodoPanel()
            "Privacy" -> FynxPrivacySettingsPanel(onBack = { selected = "Profile" })
            "Profile" -> ProfilePanel(session = authSession, openSettingsInitially = openProfileSettings, onSettingsClosed = { openProfileSettings = false }, onAppearanceChanged = { appearance = it; FynxPreferencesStore.saveAppearance(context, it) }, onAccentChanged = { accent = it })
            else -> FynxHomeSocialHubPanel(currentUsername = authSession.username ?: "preview", onOpenChats = { selected = "Chats" }, onOpenStories = { selected = "Stories" }, onOpenProfile = { selected = "Profile" }, onOpenMarketplace = { selected = "Marketplace" })
        } } }
    }
}

@Composable
private fun FynxFeaturesPanel(onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val features = listOf(Triple("Calls", "Voice & Video Calls", Icons.Default.Call), Triple("Notifications", "Notifications", Icons.Default.Notifications), Triple("Gifts", "Gifts", Icons.Default.CardGiftcard), Triple("Share", "Share & Invite", Icons.Default.Share), Triple("To-Do", "To-Do", Icons.Default.CheckCircle), Triple("Calendar", "Calendar", Icons.Default.DateRange), Triple("Money Tools", "Money Center", Icons.Default.AccountBalanceWallet), Triple("Extra Tools", "Extra Tools", Icons.Default.Build), Triple("Privacy", "Privacy & Safety", Icons.Default.Lock))
    val visible = features.filter { it.second.contains(query.trim(), true) }
    Column(Modifier.fillMaxSize()) { Text("FYNX Features", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Access your tools in one place. Privacy & Safety controls your visibility settings.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); FynxFeatureSearchField(query, { query = it }); Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(visible, key = { it.first }) { feature -> Card(onClick = { onSelect(feature.first) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f))) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = FynxDesign.ControlShape, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(feature.third, feature.second, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(21.dp)) }; Spacer(Modifier.width(12.dp)); Text(feature.second, style = MaterialTheme.typography.titleMedium) } } } } }
}
