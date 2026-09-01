package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun NotificationPanel(
    notifications: List<FynxNotification>,
    onBack: () -> Unit,
    onNotificationRead: (String) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onNotificationOpen: (FynxNotification) -> Unit = {}
) {
    var selectedType by remember { mutableStateOf<FynxNotificationType?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    val filtered = FynxNotificationActivityCenter.unreadOnly(
        FynxNotificationActivityCenter.filterByType(notifications, selectedType), unreadOnly
    )

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Notifications", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onMarkAllRead, enabled = notifications.any { !it.read }) { Text("Read all") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !unreadOnly, onClick = { unreadOnly = false }, label = { Text("All") })
            FilterChip(selected = unreadOnly, onClick = { unreadOnly = true }, label = { Text("Unread") })
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("All types") })
            FynxNotificationType.entries.take(4).forEach { type ->
                FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(typeLabel(type)) })
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (notifications.isEmpty()) "No notifications yet" else "Nothing matches this filter")
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { notification ->
                    val containerColor = if (notification.read) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                    Card(onClick = { if (!notification.read) onNotificationRead(notification.id); onNotificationOpen(notification) }, colors = CardDefaults.cardColors(containerColor = containerColor), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(notificationIcon(notification.type), null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(notification.title, style = MaterialTheme.typography.titleMedium)
                                    if (!notification.read) Text("NEW", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(notification.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
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
