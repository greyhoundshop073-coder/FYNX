package com.fynx.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme

private const val FYNX_PREVIEW_MODE = true
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
    var authSession by remember { mutableStateOf(if (FYNX_PREVIEW_MODE) AuthSession(AuthState.SIGNED_IN, "preview") else FynxAuthStore.load(context)) }
    var notifications by remember { mutableStateOf(FynxNotificationStore.load(context)) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var accent by remember { mutableStateOf(FynxPreferencesStore.loadAccent(context)) }
    var appearance by remember { mutableStateOf(FynxPreferencesStore.loadAppearance(context)) }
    val systemDark = isSystemInDarkTheme()
    val darkMode = when (appearance) { "Dark" -> true; "Light" -> false; else -> systemDark }

    LaunchedEffect(deepLinkDestination) { when (val destination = deepLinkDestination) { is FynxDeepLinkDestination.Invite -> { inviteCode = destination.code; selected = "Invite" }; FynxDeepLinkDestination.Home -> selected = "Home"; null -> Unit } }
    LaunchedEffect(Unit) { FynxNotificationFoundation.createChannels(context); notifications = FynxNotificationStore.load(context) }

    if (!FYNX_PREVIEW_MODE && authSession.state != AuthState.SIGNED_IN) {
        FynxTheme(accent = accent, darkMode = darkMode) { FynxAuthGate { username -> FynxAuthStore.save(context, username); authSession = AuthSession(AuthState.SIGNED_IN, username) } }
        return
    }

    val mainNav = listOf(FynxNavItem("Home", "Home", Icons.Default.Home), FynxNavItem("Chats", "Chats", Icons.Default.ChatBubbleOutline), FynxNavItem("Friends", "Friends", Icons.Default.Person), FynxNavItem("Marketplace", "Market", Icons.Default.ShoppingBag), FynxNavItem("Money Tools", "Money", Icons.Default.AccountBalanceWallet), FynxNavItem("Features", "More", Icons.Default.MoreHoriz))
    val mainDestinationKeys = remember(mainNav) { mainNav.map { it.key }.toSet() }
    val isSecondaryDestination = selected !in mainDestinationKeys
    BackHandler(enabled = profileUser != null) { profileUser = null }; BackHandler(enabled = openChat != null) { openChat = null }; BackHandler(enabled = openGroup != null && openChat == null) { openGroup = null }
    BackHandler(enabled = openChat == null && openGroup == null && selected != "Home") { selected = if (isSecondaryDestination) when (selected) { "Notifications", "Stories", "Profile" -> "Home"; "Groups" -> "Chats"; "Gifts" -> "Features"; else -> "Features" } else "Home" }

    if (profileUser != null) { FynxTheme(accent = accent, darkMode = darkMode) { OtherUserProfilePanel(username = profileUser!!, onBack = { profileUser = null }, onMessage = { username -> val normalized = username.trim().let { if (it.startsWith("@")) it else "@$it" }; val existing = FynxChatStore.loadPreviews(context).firstOrNull { it.username.equals(normalized, true) }; openChat = existing ?: ChatPreview(normalized.removePrefix("@").ifBlank { "FYNX user" }, normalized, "Start a conversation", "Now"); FynxChatStore.savePreview(context, openChat!!); profileUser = null }) }; return }
    if (openChat != null) { FynxTheme(accent = accent, darkMode = darkMode) { ConversationPanel(chat = openChat!!, onBack = { openChat = null }, onOpenProfile = { profileUser = it; openChat = null }, onVoiceCall = { callTarget = openChat!!.name; callVideo = false; openChat = null; selected = "Calls" }, onVideoCall = { callTarget = openChat!!.name; callVideo = true; openChat = null; selected = "Calls" }) }; return }
    if (openGroup != null) { FynxTheme(accent = accent, darkMode = darkMode) { FynxGroupConversationPanel(groupId = openGroup!!, currentUsername = authSession.username?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview", onBack = { openGroup = null }) }; return }

    FynxTheme(accent = accent, darkMode = darkMode) {
        val mainIndex = mainNav.indexOfFirst { it.key == selected }.coerceAtLeast(0); val unreadNotifications = notifications.unreadNotificationCount()
        Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { if (selected == "Home") Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 12.dp, vertical = 8.dp), Alignment.CenterVertically) { IconButton(onClick = { selected = "Profile" }) { FynxAvatar(authSession.username?.ifBlank { "preview" } ?: "preview", Modifier.size(34.dp)) }; Box(Modifier.weight(1f), Alignment.Center) { Text("FYNX", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; Row(Alignment.CenterVertically) { BadgedBox(badge = { if (unreadNotifications > 0) Badge { Text(unreadNotifications.toString()) } }) { IconButton(onClick = { selected = "Notifications" }) { Icon(Icons.Default.Notifications, "Notifications") } }; IconButton(onClick = { selected = "Profile" }) { Icon(Icons.Default.Settings, "Settings") } } } else Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 6.dp, vertical = 6.dp), Alignment.CenterVertically) { if (isSecondaryDestination) IconButton(onClick = { selected = if (selected == "Notifications" || selected == "Stories" || selected == "Profile") "Home" else if (selected == "Money Center") "Money Tools" else "Features" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } else Spacer(Modifier.size(48.dp)); Box(Modifier.weight(1f), Alignment.Center) { Text(if (selected == "Marketplace") "Marketplace" else if (selected == "Money Tools") "Money Center" else selected, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; Spacer(Modifier.size(48.dp)) }, bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) { mainNav.forEach { item -> NavigationBarItem(selected = selected == item.key, onClick = { selected = item.key }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = FynxDesign.SelectedContainer, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant)) } } }) { padding ->
            Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 6.dp).widthIn(max = 720.dp).align(Alignment.Center).pointerInput(selected) { var totalDrag = 0f; detectHorizontalDragGestures(onDragStart = { totalDrag = 0f }, onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount }, onDragEnd = { if (kotlin.math.abs(totalDrag) >= 80f) { val nextIndex = if (totalDrag < 0) (mainIndex + 1).coerceAtMost(mainNav.lastIndex) else (mainIndex - 1).coerceAtLeast(0); selected = mainNav[nextIndex].key } }) }) {
                when (selected) {
                    "Home" -> HomePanel(currentUsername = authSession.username ?: "preview", onOpenChats = { selected = "Chats" }, onOpenStories = { selected = "Stories" }, onOpenProfile = { selected = "Profile" }, onOpenMarketplace = { selected = "Marketplace" }, onOpenNotifications = { selected = "Notifications" }, onOpenFindPeople = { selected = "Friends" })
                    "Chats" -> ChatsPanel(onOpenChat = { openChat = it }, onOpenGroup = { openGroup = it }, onCreateGroup = { selected = "Groups" })
                    "Friends" -> FriendsPanel(onOpenProfile = { profileUser = it })
                    "Marketplace" -> FynxMarketplacePanel(currentUsername = authSession.username ?: "preview", onOpenProfile = { profileUser = it })
                    "Money Tools" -> MoneyCenterPanel()
                    "Features" -> FynxFeaturesPanel(onSelect = { selected = it })
                    "Extra Tools" -> FynxExtraToolsPanel(onOpenCalendar = { selected = "Calendar" })
                    "Calendar" -> CalendarPanel(); "Stories" -> StoriesPanel(); "Gifts" -> GiftsPanel(); "Groups" -> FynxGroupsPanel(currentUsername = authSession.username?.let { if (it.startsWith("@")) it else "@$it" } ?: "@preview", onOpenGroup = { openGroup = it }); "Notifications" -> NotificationPanel(notifications = notifications, onBack = { selected = "Home" }, onNotificationRead = { notifications = FynxNotificationStore.load(context) }, onMarkAllRead = { notifications = FynxNotificationStore.load(context) }); "Share" -> FynxSharePanel(); "Invite" -> FynxInvitePanel(code = inviteCode, onShare = { FynxShareActions.share(context, FynxShareActions.defaultPayload()) }, onBack = { selected = "Features" }); "Calls" -> FynxCallsPanel(initialName = callTarget, initialVideo = callVideo); "To-Do" -> TodoPanel(); "Profile" -> ProfilePanel(session = authSession, onAppearanceChanged = { appearance = it }, onAccentChanged = { accent = it }, onSignOut = { authSession = if (FYNX_PREVIEW_MODE) AuthSession(AuthState.SIGNED_IN, "preview") else { FynxAuthStore.clear(context); AuthSession() } }); else -> HomePanel(currentUsername = authSession.username ?: "preview", onOpenChats = { selected = "Chats" }, onOpenStories = { selected = "Stories" }, onOpenProfile = { selected = "Profile" }, onOpenMarketplace = { selected = "Marketplace" })
                }
            } }
        }
    }
}

@Composable
private fun FynxFeaturesPanel(onSelect: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val features = listOf(Triple("Calls", "Voice & Video Calls", Icons.Default.Call), Triple("Notifications", "Notifications", Icons.Default.Notifications), Triple("Gifts", "Gifts", Icons.Default.CardGiftcard), Triple("Share", "Share & Invite", Icons.Default.Share), Triple("To-Do", "To-Do", Icons.Default.CheckCircle), Triple("Calendar", "Calendar", Icons.Default.DateRange), Triple("Money Tools", "Money Center", Icons.Default.AccountBalanceWallet), Triple("Extra Tools", "Extra Tools", Icons.Default.Build))
    val filteredFeatures = features.filter { (_, label, _) -> searchQuery.isBlank() || label.contains(searchQuery.trim(), true) }
    Column(Modifier.fillMaxSize()) { Text("FYNX Features", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Access your tools in one place. Money tools are grouped together in Money Center.", color = FynxDesign.TextSecondary); Spacer(Modifier.height(8.dp)); FynxFeatureSearchField(searchQuery, { searchQuery = it }); Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(filteredFeatures, key = { it.first }) { (key, label, icon) -> Card(onClick = { onSelect(key) }, Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .5f))) { Row(Modifier.fillMaxWidth().padding(13.dp), Alignment.CenterVertically) { Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) { Icon(icon, label, tint = MaterialTheme.colorScheme.primary, Modifier.padding(8.dp).size(21.dp)) }; Spacer(Modifier.width(12.dp)); Text(label, style = MaterialTheme.typography.titleMedium) } } } } }
}
