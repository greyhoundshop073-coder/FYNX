package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun NotificationPanel(
    notifications: List<FynxNotification>,
    onBack: () -> Unit,
    onNotificationRead: (String) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onNotificationOpen: (FynxNotification) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localNotifications by remember { mutableStateOf(FynxNotificationStore.load(context)) }
    var remoteNotifications by remember { mutableStateOf<List<FynxNotification>>(emptyList()) }
    var notificationPreferences by remember { mutableStateOf(FynxNotificationPreferencesClient.cached(context)) }
    val current = remember(localNotifications, remoteNotifications, notifications) {
        (remoteNotifications + localNotifications + notifications)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    }
    var selectedType by remember { mutableStateOf<FynxNotificationType?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var speakNotifications by remember { mutableStateOf(FynxNotificationFoundation.isSpeakNotificationsEnabled(context)) }
    val filtered = FynxNotificationActivityCenter.unreadOnly(
        FynxNotificationActivityCenter.filterByType(current, selectedType), unreadOnly
    )

    LaunchedEffect(Unit) {
        FynxNotificationPreferencesClient.load(context).onSuccess { notificationPreferences = it }
        FynxNotificationRemoteClient.load(context).onSuccess { remoteNotifications = it }
    }

    fun savePreferences(next: FynxNotificationPreferences) {
        notificationPreferences = next
        scope.launch {
            FynxNotificationPreferencesClient.update(context, next).onSuccess { notificationPreferences = it }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Notifications", style = MaterialTheme.typography.titleLarge)
                Text("Stay up to date with FYNX", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
            }
            TextButton(
                onClick = {
                    FynxNotificationStore.markAllRead(context)
                    localNotifications = FynxNotificationStore.load(context)
                    scope.launch {
                        current.filter { !it.read }.forEach { FynxNotificationRemoteClient.markRead(context, it.id) }
                        FynxNotificationRemoteClient.load(context).onSuccess { remoteNotifications = it }
                    }
                    onMarkAllRead()
                }, enabled = current.any { !it.read }
            ) { Text("Read all") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape,
            colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
            border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NotificationPreferenceSwitch("Notifications", "Master switch for FYNX alerts", notificationPreferences.enabled) {
                    savePreferences(FynxNotificationControlsBatch3.update(notificationPreferences, enabled = it))
                }
                NotificationPreferenceSwitch("Push alerts", "Allow notification banners and sounds", notificationPreferences.pushEnabled) {
                    savePreferences(FynxNotificationControlsBatch3.update(notificationPreferences, pushEnabled = it))
                }
                NotificationPreferenceSwitch("Quiet mode", "Temporarily silence non-safety alerts", notificationPreferences.quietMode) {
                    savePreferences(FynxNotificationControlsBatch3.update(notificationPreferences, quietMode = it))
                }
                NotificationPreferenceSwitch("Speak notifications", "Read new alerts aloud", speakNotifications) {
                    speakNotifications = it
                    FynxNotificationFoundation.setSpeakNotificationsEnabled(context, it)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape,
            colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
            border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Notification types", style = MaterialTheme.typography.titleSmall)
                Text("Choose which activity alerts you receive. Safety alerts always remain enabled.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                NotificationPreferenceSwitch("Messages", "Direct messages", notificationPreferences.messagesEnabled) { savePreferences(notificationPreferences.copy(messagesEnabled = it)) }
                NotificationPreferenceSwitch("Friends", "Friend requests", notificationPreferences.friendRequestsEnabled) { savePreferences(notificationPreferences.copy(friendRequestsEnabled = it)) }
                NotificationPreferenceSwitch("Stories", "Story activity", notificationPreferences.storiesEnabled) { savePreferences(notificationPreferences.copy(storiesEnabled = it)) }
                NotificationPreferenceSwitch("Reactions", "Reactions to your posts", notificationPreferences.reactionsEnabled) { savePreferences(notificationPreferences.copy(reactionsEnabled = it)) }
                NotificationPreferenceSwitch("Comments", "Comments on your posts", notificationPreferences.commentsEnabled) { savePreferences(notificationPreferences.copy(commentsEnabled = it)) }
                NotificationPreferenceSwitch("Groups", "Group activity", notificationPreferences.groupEnabled) { savePreferences(notificationPreferences.copy(groupEnabled = it)) }
                NotificationPreferenceSwitch("Reminders", "FYNX reminders", notificationPreferences.remindersEnabled) { savePreferences(notificationPreferences.copy(remindersEnabled = it)) }
                NotificationPreferenceSwitch("Marketplace", "Orders and marketplace activity", notificationPreferences.marketplaceEnabled) { savePreferences(notificationPreferences.copy(marketplaceEnabled = it)) }
                NotificationPreferenceSwitch("Money", "Wallet activity", notificationPreferences.walletEnabled) { savePreferences(notificationPreferences.copy(walletEnabled = it)) }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = !unreadOnly, onClick = { unreadOnly = false }, label = { Text("All") }, shape = FynxDesign.ControlShape)
            FilterChip(selected = unreadOnly, onClick = { unreadOnly = true }, label = { Text("Unread") }, shape = FynxDesign.ControlShape)
            FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("All types") }, shape = FynxDesign.ControlShape)
            FynxNotificationType.entries.forEach { type ->
                FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(typeLabel(type)) }, shape = FynxDesign.ControlShape)
            }
        }

        HorizontalDivider(color = FynxDesign.Outline.copy(alpha = 0.6f))

        if (filtered.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape,
                colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
            ) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(28.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(if (current.isEmpty()) "No notifications yet" else "Nothing matches this filter", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Your FYNX activity and reminders will appear here.", color = FynxDesign.TextSecondary)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { notification ->
                    val containerColor = if (notification.read) FynxDesign.Surface else FynxDesign.SelectedContainer
                    Card(
                        onClick = {
                            if (!notification.read) {
                                FynxNotificationStore.markRead(context, notification.id)
                                localNotifications = FynxNotificationStore.load(context)
                                scope.launch {
                                    FynxNotificationRemoteClient.markRead(context, notification.id)
                                    FynxNotificationRemoteClient.load(context).onSuccess { remoteNotifications = it }
                                }
                                onNotificationRead(notification.id)
                            }
                            onNotificationOpen(notification)
                        }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape,
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SurfaceRaised) {
                                Icon(notificationIcon(notification.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(notification.title, style = MaterialTheme.typography.titleMedium)
                                    if (!notification.read) Text("NEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(notification.message, style = MaterialTheme.typography.bodyMedium, color = if (notification.read) FynxDesign.TextSecondary else FynxDesign.TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationPreferenceSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun typeLabel(type: FynxNotificationType): String = when (type) {
    FynxNotificationType.MESSAGE -> "Messages"
    FynxNotificationType.FRIEND_REQUEST -> "Friends"
    FynxNotificationType.STORY -> "Stories"
    FynxNotificationType.REMINDER -> "Reminders"
    FynxNotificationType.SAFETY -> "Safety"
    FynxNotificationType.GROUP -> "Groups"
    FynxNotificationType.REACTION -> "Reactions"
    FynxNotificationType.COMMENT -> "Comments"
    FynxNotificationType.MARKETPLACE_ORDER -> "Marketplace"
    FynxNotificationType.WALLET_ACTIVITY -> "Money"
}

private fun notificationIcon(type: FynxNotificationType) = when (type) {
    FynxNotificationType.MESSAGE -> Icons.Default.ChatBubbleOutline
    FynxNotificationType.FRIEND_REQUEST -> Icons.Default.Person
    FynxNotificationType.STORY -> Icons.Default.Star
    FynxNotificationType.REMINDER -> Icons.Default.CheckCircle
    FynxNotificationType.SAFETY -> Icons.Default.Notifications
    FynxNotificationType.GROUP -> Icons.Default.Group
    FynxNotificationType.REACTION, FynxNotificationType.COMMENT -> Icons.Default.Star
    FynxNotificationType.MARKETPLACE_ORDER -> Icons.Default.ShoppingBag
    FynxNotificationType.WALLET_ACTIVITY -> Icons.Default.AccountBalanceWallet
}
