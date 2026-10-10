package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

@Composable
fun NotificationPanel(notifications: List<FynxNotification>, onBack: () -> Unit, onNotificationRead: (String) -> Unit = {}, onMarkAllRead: () -> Unit = {}, onUnreadCountChanged: (Int) -> Unit = {}, onNotificationOpen: (FynxNotification) -> Unit = {}, preferencesOnly: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var localNotifications by remember { mutableStateOf(FynxNotificationStore.load(context)) }
    var remoteNotifications by remember { mutableStateOf<List<FynxNotification>>(emptyList()) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var notificationPreferences by remember { mutableStateOf(FynxNotificationPreferencesClient.cached(context)) }
    var remoteUnreadCount by remember { mutableIntStateOf(0) }
    var showOverflow by remember { mutableStateOf(false) }
    var showPreferences by remember(preferencesOnly) { mutableStateOf(preferencesOnly) }
    var selectedType by remember { mutableStateOf<FynxNotificationType?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var speakNotifications by remember { mutableStateOf(FynxNotificationFoundation.isSpeakNotificationsEnabled(context)) }
    val current = remember(localNotifications, remoteNotifications, notifications) { (remoteNotifications + localNotifications + notifications).distinctBy { it.id }.sortedByDescending { it.timestamp } }
    val filtered = FynxNotificationActivityCenter.unreadOnly(FynxNotificationActivityCenter.filterByType(current, selectedType), unreadOnly)

    fun loadRemoteNotifications() {
        scope.launch {
            FynxNotificationPreferencesClient.load(context).onSuccess { notificationPreferences = it }
            FynxNotificationRemoteClient.loadFeed(context).onSuccess { feed ->
                remoteNotifications = feed.notifications
                remoteUnreadCount = feed.unreadCount
                onUnreadCountChanged(feed.unreadCount)
                FynxNotificationStore.save(context, feed.notifications)
                localNotifications = FynxNotificationStore.load(context)
                remoteError = null
            }.onFailure { remoteError = it.message ?: "Unable to refresh notifications." }
        }
    }
    LaunchedEffect(Unit) { loadRemoteNotifications() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) loadRemoteNotifications() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun savePreferences(next: FynxNotificationPreferences) {
        notificationPreferences = next
        scope.launch { FynxNotificationPreferencesClient.update(context, next).onSuccess { notificationPreferences = it } }
    }
    fun markAllRead() {
        FynxNotificationStore.markAllRead(context)
        localNotifications = FynxNotificationStore.load(context)
        remoteNotifications = remoteNotifications.map { it.copy(read = true) }
        remoteUnreadCount = 0
        onUnreadCountChanged(0)
        scope.launch {
            FynxNotificationRemoteClient.markAllRead(context).onSuccess { loadRemoteNotifications() }.onFailure { loadRemoteNotifications() }
        }
        onMarkAllRead()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Column(Modifier.weight(1f), horizontalAlignment = if (preferencesOnly) Alignment.Start else Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (preferencesOnly) "Notification Preferences" else "Notifications", style = MaterialTheme.typography.titleLarge)
                    if (!preferencesOnly) {
                        val unread = maxOf(remoteUnreadCount, current.count { !it.read })
                        if (unread > 0) {
                            Spacer(Modifier.width(8.dp))
                            Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                        }
                    }
                }
                Text(if (preferencesOnly) "Choose which FYNX alerts you receive" else "Stay up to date with FYNX",
                    style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
            }
            if (!preferencesOnly) {
                Box {
                    IconButton(onClick = { showOverflow = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Notification options") }
                    DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                        DropdownMenuItem(text = { Text("Mark all as read") }, leadingIcon = { Icon(Icons.Default.DoneAll, null) }, enabled = remoteUnreadCount > 0 || current.any { !it.read }, onClick = { showOverflow = false; markAllRead() })
                        DropdownMenuItem(text = { Text("Notification settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { showOverflow = false; showPreferences = !showPreferences })
                        DropdownMenuItem(text = { Text("Refresh notifications") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { showOverflow = false; loadRemoteNotifications() })
                    }
                }
            }
        }
        remoteError?.let { message ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { loadRemoteNotifications() }) { Text("Retry") }
                }
            }
        }
        if (showPreferences) {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("GENERAL", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp, bottom = 3.dp))
                    NotificationPreferenceSwitch("Notifications", "Master switch for FYNX alerts", notificationPreferences.enabled) { savePreferences(FynxNotificationControlsBatch3.update(notificationPreferences, enabled = it)) }
                    NotificationPreferenceSwitch("Push alerts", "Allow notification banners and sounds", notificationPreferences.pushEnabled) { savePreferences(FynxNotificationControlsBatch3.update(notificationPreferences, pushEnabled = it)) }
                    NotificationPreferenceSwitch("Quiet mode", "Temporarily silence non-safety alerts", notificationPreferences.quietMode) { savePreferences(FynxNotificationControlsBatch3.update(notificationPreferences, quietMode = it)) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp))
                    Text("COMMUNICATION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    NotificationPreferenceSwitch("Chat notifications", "Direct messages", notificationPreferences.messagesEnabled) { savePreferences(notificationPreferences.copy(messagesEnabled = it)) }
                    NotificationPreferenceSwitch("Friends", "Friend requests and new followers", notificationPreferences.friendRequestsEnabled) { savePreferences(notificationPreferences.copy(friendRequestsEnabled = it)) }
                    NotificationPreferenceSwitch("Groups", "Group activity", notificationPreferences.groupEnabled) { savePreferences(notificationPreferences.copy(groupEnabled = it)) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp))
                    Text("SOCIAL ACTIVITY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    NotificationPreferenceSwitch("Reactions", "Reactions to your posts", notificationPreferences.reactionsEnabled) { savePreferences(notificationPreferences.copy(reactionsEnabled = it)) }
                    NotificationPreferenceSwitch("Comments", "Comments on your posts", notificationPreferences.commentsEnabled) { savePreferences(notificationPreferences.copy(commentsEnabled = it)) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp))
                    Text("STORIES & STATUS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    NotificationPreferenceSwitch("Stories", "Story activity", notificationPreferences.storiesEnabled) { savePreferences(notificationPreferences.copy(storiesEnabled = it)) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp))
                    Text("MARKETPLACE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    NotificationPreferenceSwitch("Marketplace", "Orders and marketplace activity", notificationPreferences.marketplaceEnabled) { savePreferences(notificationPreferences.copy(marketplaceEnabled = it)) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp))
                    Text("MONEY ALERTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    NotificationPreferenceSwitch("Money", "Wallet activity", notificationPreferences.walletEnabled) { savePreferences(notificationPreferences.copy(walletEnabled = it)) }
                    NotificationPreferenceSwitch("Reminders", "FYNX reminders", notificationPreferences.remindersEnabled) { savePreferences(notificationPreferences.copy(remindersEnabled = it)) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 5.dp))
                    Text("SOUNDS & VIBRATION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    NotificationPreferenceSwitch("Speak notifications", "Read new alerts aloud", speakNotifications) { speakNotifications = it; FynxNotificationFoundation.setSpeakNotificationsEnabled(context, it) }
                    Text("Call ringtone and vibration follow your device's FYNX call notification channel.",
                        style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!preferencesOnly) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !unreadOnly, onClick = { unreadOnly = false }, label = { Text("All") }, shape = FynxDesign.ControlShape)
            FilterChip(selected = unreadOnly, onClick = { unreadOnly = true }, label = { Text("Unread") }, shape = FynxDesign.ControlShape)
            FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("All types") }, shape = FynxDesign.ControlShape)
            FynxNotificationType.entries.forEach { type -> FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(typeLabel(type)) }, shape = FynxDesign.ControlShape) }
        }
        HorizontalDivider(color = FynxDesign.Outline.copy(alpha = .6f))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (filtered.isEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) { Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(28.dp)) }
                        Spacer(Modifier.height(12.dp)); Text(if (current.isEmpty()) "No notifications yet" else "Nothing matches this filter", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(4.dp)); Text("Your FYNX activity and reminders will appear here.", color = FynxDesign.TextSecondary)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { notification ->
                        val containerColor = if (notification.read) FynxDesign.Surface else FynxDesign.SelectedContainer
                        Card(onClick = {
                            if (!notification.read) {
                                FynxNotificationStore.markRead(context, notification.id)
                                localNotifications = FynxNotificationStore.load(context)
                                remoteNotifications = remoteNotifications.map { if (it.id == notification.id) it.copy(read = true) else it }
                                remoteUnreadCount = maxOf(0, remoteUnreadCount - 1)
                                onUnreadCountChanged(remoteUnreadCount)
                                scope.launch { FynxNotificationRemoteClient.markRead(context, notification.id) }
                                onNotificationRead(notification.id)
                            }
                            onNotificationOpen(notification)
                        }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = containerColor), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                NotificationActorAvatar(notification = notification, modifier = Modifier.size(46.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(notification.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2)
                                        if (!notification.read) { Spacer(Modifier.width(8.dp)); Text("NEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(notification.message, style = MaterialTheme.typography.bodyMedium, color = if (notification.read) FynxDesign.TextSecondary else FynxDesign.TextPrimary, maxLines = 2)
                                    Spacer(Modifier.height(3.dp))
                                    Text(formatNotificationTimestamp(notification.timestamp), style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        }
    }
}

@Composable
private fun NotificationActorAvatar(notification: FynxNotification, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val username = notification.sourceUsername?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }
    val actorTypes = setOf(FynxNotificationType.MESSAGE, FynxNotificationType.FRIEND_REQUEST, FynxNotificationType.FOLLOW, FynxNotificationType.STORY, FynxNotificationType.GROUP, FynxNotificationType.REACTION, FynxNotificationType.COMMENT)
    val canUseActorAvatar = username != null && notification.type in actorTypes
    var remotePhotoId by remember(username) { mutableStateOf<String?>(FynxProfileRemoteClient.cachedProfilePhotoId(context, username ?: "")) }
    LaunchedEffect(username) {
        if (username != null) FynxProfileRemoteClient.get(context, username).onSuccess { remotePhotoId = it.profilePhotoMediaId }
    }
    Box(modifier = modifier) {
        if (canUseActorAvatar) {
            FynxRemoteProfileAvatar(mediaId = remotePhotoId, contentDescription = username, modifier = Modifier.fillMaxSize(), ownerUsername = username)
        } else {
            Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SurfaceRaised, modifier = Modifier.fillMaxSize()) {
                Icon(notificationIcon(notification.type), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp).fillMaxSize())
            }
        }
        if (canUseActorAvatar) {
            Surface(shape = FynxDesign.ControlShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), modifier = Modifier.size(18.dp).align(Alignment.BottomEnd)) {
                Icon(notificationIcon(notification.type), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(3.dp).fillMaxSize())
            }
        }
    }
}

private fun formatNotificationTimestamp(timestamp: Long): String {
    val age = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    return when {
        age < minute -> "Just now"
        age < hour -> "${age / minute}m ago"
        age < day -> "${age / hour}h ago"
        age < 7L * day -> "${age / day}d ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(timestamp))
    }
}

@Composable
private fun NotificationPreferenceSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(description, style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary) }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun typeLabel(type: FynxNotificationType): String = when (type) {
    FynxNotificationType.MESSAGE -> "Messages"
    FynxNotificationType.FRIEND_REQUEST -> "Friends"
    FynxNotificationType.FOLLOW -> "Followers"
    FynxNotificationType.STORY -> "Stories"
    FynxNotificationType.REMINDER -> "Reminders"
    FynxNotificationType.SAFETY -> "Safety"
    FynxNotificationType.GROUP -> "Groups"
    FynxNotificationType.REACTION -> "Reactions"
    FynxNotificationType.COMMENT -> "Comments"
    FynxNotificationType.MARKETPLACE_ORDER, FynxNotificationType.MARKETPLACE_PRICE -> "Marketplace"
    FynxNotificationType.WALLET_ACTIVITY -> "Money"
}

private fun notificationIcon(type: FynxNotificationType) = when (type) {
    FynxNotificationType.MESSAGE -> Icons.Default.Message
    FynxNotificationType.FRIEND_REQUEST -> Icons.Default.PersonAdd
    FynxNotificationType.FOLLOW -> Icons.Default.PersonAdd
    FynxNotificationType.STORY -> Icons.Default.AutoStories
    FynxNotificationType.REMINDER -> Icons.Default.Schedule
    FynxNotificationType.SAFETY -> Icons.Default.Security
    FynxNotificationType.GROUP -> Icons.Default.Group
    FynxNotificationType.REACTION -> Icons.Default.Favorite
    FynxNotificationType.COMMENT -> Icons.Default.Comment
    FynxNotificationType.MARKETPLACE_ORDER, FynxNotificationType.MARKETPLACE_PRICE -> Icons.Default.ShoppingBag
    FynxNotificationType.WALLET_ACTIVITY -> Icons.Default.AccountBalanceWallet
}
