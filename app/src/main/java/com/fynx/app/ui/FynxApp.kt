package com.fynx.app.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class FynxNavItem(val key: String, val label: String, val icon: ImageVector)

@Composable
fun FynxApp() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf("Home") }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var callTarget by remember { mutableStateOf<String?>(null) }
    var callVideo by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var authSession by remember { mutableStateOf(FynxAuthStore.load(context)) }
    var notifications by remember { mutableStateOf(emptyList<FynxNotification>()) }

    LaunchedEffect(Unit) { FynxNotificationFoundation.createChannels(context) }

    if (authSession.state != AuthState.SIGNED_IN) {
        FynxAuthGate { username ->
            FynxAuthStore.save(context, username)
            authSession = AuthSession(AuthState.SIGNED_IN, username)
        }
        return
    }

    val mainNav = listOf(
        FynxNavItem("Home", "Home", Icons.Default.Home),
        FynxNavItem("Chats", "Chats", Icons.Default.ChatBubbleOutline),
        FynxNavItem("Groups", "Groups", Icons.Default.Group),
        FynxNavItem("Marketplace", "Market", Icons.Default.ShoppingBag),
        FynxNavItem("Money Tools", "Money", Icons.Default.AccountBalanceWallet),
        FynxNavItem("Features", "More", Icons.Default.MoreHoriz)
    )

    if (openChat != null) {
        ConversationPanel(
            chat = openChat!!,
            onBack = { openChat = null },
            onVoiceCall = {
                callTarget = openChat!!.name
                callVideo = false
                openChat = null
                selected = "Calls"
            },
            onVideoCall = {
                callTarget = openChat!!.name
                callVideo = true
                openChat = null
                selected = "Calls"
            }
        )
        return
    }

    if (openGroup != null) {
        FynxGroupConversationPanel(groupName = openGroup!!, onBack = { openGroup = null })
        return
    }

    FynxTheme {
        val mainIndex = mainNav.indexOfFirst { it.key == selected }.coerceAtLeast(0)
        val unreadNotifications = notifications.unreadNotificationCount()

        Scaffold(
            containerColor = FynxDesign.Background,
            topBar = {
                if (selected == "Home") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FynxAvatar("username", Modifier.size(34.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("FYNX", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifications > 0) Badge { Text(unreadNotifications.toString()) }
                                }
                            ) {
                                IconButton(onClick = { selected = "Notifications" }) {
                                    Icon(Icons.Default.Notifications, "Notifications")
                                }
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, "Settings")
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text(selected, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = FynxDesign.Surface, tonalElevation = 8.dp) {
                    mainNav.forEach { item ->
                        NavigationBarItem(
                            selected = selected == item.key,
                            onClick = { selected = item.key },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = FynxDesign.SelectedContainer,
                                unselectedIconColor = FynxDesign.TextSecondary,
                                unselectedTextColor = FynxDesign.TextSecondary
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)
                    .pointerInput(selected) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                if (kotlin.math.abs(totalDrag) >= 80f) {
                                    val nextIndex = if (totalDrag < 0) (mainIndex + 1).coerceAtMost(mainNav.lastIndex)
                                    else (mainIndex - 1).coerceAtLeast(0)
                                    selected = mainNav[nextIndex].key
                                }
                            }
                        )
                    }
            ) {
                when (selected) {
                    "Home" -> HomePanel()
                    "Chats" -> ChatsPanel(onOpenChat = { openChat = it })
                    "Groups" -> FynxGroupsPanel(onOpenGroup = { openGroup = it })
                    "Marketplace" -> FynxMarketplacePanel()
                    "Money Tools" -> MoneyToolsPanel()
                    "Features" -> FynxFeaturesPanel(onSelect = { selected = it })
                    "Notifications" -> NotificationPanel(
                        notifications = notifications,
                        onBack = { selected = "Features" },
                        onNotificationRead = { id -> notifications = notifications.markNotificationRead(id) },
                        onMarkAllRead = { notifications = notifications.map { it.copy(read = true) } }
                    )
                    "Share" -> FynxSharePanel()
                    "Calls" -> FynxCallsPanel(initialName = callTarget, initialVideo = callVideo)
                    "Studio" -> AiStudioPanel()
                    "To-Do" -> TodoPanel()
                    "Calendar" -> CalendarPanel()
                    "Profile" -> ProfilePanel(session = authSession, onSignOut = {
                        FynxAuthStore.clear(context)
                        authSession = AuthSession()
                    })
                    "Bills" -> BillsPaymentPanel()
                    "Transactions" -> TransactionHistoryPanel()
                    "Accounts" -> AccountsWalletsPanel()
                    "Budget" -> BudgetPlannerPanel()
                    "Currency" -> CurrencyConverterPanel()
                    "Savings" -> SavingsGoalsPanel()
                    "Subscriptions" -> SubscriptionsPanel()
                    "Overview" -> FinancialOverviewPanel()
                    "Receipts" -> ReceiptsExpensePanel()
                    "Insights" -> MoneyInsightsPanel()
                    "Spending Insights" -> SpendingInsightsPanel()
                    "Money Alerts" -> MoneyAlertsPanel()
                    "Vault" -> SecureMoneyVaultPanel()
                }
            }
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Settings") },
                text = { Text("FYNX settings") },
                confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } }
            )
        }
    }
}

@Composable
private fun FynxFeaturesPanel(onSelect: (String) -> Unit) {
    val features = listOf(
        Triple("Calls", "Voice & Video Calls", Icons.Default.Call),
        Triple("Notifications", "Notifications", Icons.Default.Notifications),
        Triple("Share", "Share & Invite", Icons.Default.Share),
        Triple("Studio", "AI Studio", Icons.Default.Build),
        Triple("To-Do", "To-Do", Icons.Default.CheckCircle),
        Triple("Calendar", "Calendar", Icons.Default.DateRange),
        Triple("Bills", "Bills & Payment Reminders", Icons.Default.AccountBalance),
        Triple("Transactions", "Transaction History", Icons.Default.History),
        Triple("Accounts", "Accounts & Wallets", Icons.Default.AccountBalanceWallet),
        Triple("Budget", "Budget Planner", Icons.Default.AttachMoney),
        Triple("Currency", "Currency Converter", Icons.Default.AttachMoney),
        Triple("Savings", "Savings Goals", Icons.Default.AccountBalance),
        Triple("Subscriptions", "Subscriptions & Recurring Payments", Icons.Default.Autorenew),
        Triple("Overview", "Financial Overview", Icons.Default.PieChart),
        Triple("Receipts", "Receipts & Expenses", Icons.Default.AccountBalance),
        Triple("Insights", "Money Insights", Icons.Default.PieChart),
        Triple("Spending Insights", "Spending Insights", Icons.Default.PieChart),
        Triple("Money Alerts", "Money Alerts", Icons.Default.Notifications),
        Triple("Vault", "Secure Money Vault", Icons.Default.Lock)
    )

    Column(Modifier.fillMaxSize()) {
        Text("FYNX Features", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Access your tools in one place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(features, key = { it.first }) { (key, label, icon) ->
                Card(onClick = { onSelect(key) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
