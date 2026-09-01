package com.fynx.app.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class FynxNavItem(val key: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FynxApp() {
    var selected by remember { mutableStateOf("Home") }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var authSession by remember { mutableStateOf(AuthSession(state = AuthState.SIGNED_IN, username = "username")) }
    val colors = darkColorScheme(primary = Color(0xFF2F8CFF), onPrimary = Color.White, secondary = Color(0xFF22C7F2), background = Color(0xFF071326), onBackground = Color(0xFFF5F8FF), surface = Color(0xFF0D1B2E), onSurface = Color(0xFFF5F8FF), surfaceVariant = Color(0xFF15263D), onSurfaceVariant = Color(0xFFB9C6D8), outline = Color(0xFF31445F))
    val mainNav = listOf(FynxNavItem("Home", "Home", Icons.Default.Home), FynxNavItem("Chats", "Chats", Icons.Default.Person), FynxNavItem("Friends", "Friends", Icons.Default.Person), FynxNavItem("Stories", "Stories", Icons.Default.Home), FynxNavItem("Money Tools", "Money", Icons.Default.Person), FynxNavItem("Features", "Features", Icons.Default.Settings))
    if (openChat != null) { ConversationPanel(chat = openChat!!, onBack = { openChat = null }); return }
    MaterialTheme(colorScheme = colors) {
        val mainIndex = mainNav.indexOfFirst { it.key == selected }.coerceAtLeast(0)
        Scaffold(containerColor = colors.background, topBar = { when (selected) {
            "Home" -> TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background), navigationIcon = { FynxAvatar("username", Modifier.size(34.dp)) }, title = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("FYNX", color = colors.primary, fontWeight = FontWeight.Bold) } }, actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings") } })
            "Friends", "Stories" -> TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background), navigationIcon = { IconButton(onClick = { selected = "Home" }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, title = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(selected, color = colors.primary, fontWeight = FontWeight.Bold) } }, actions = { Spacer(Modifier.size(48.dp)) })
            else -> TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background), navigationIcon = { if (selected != "Money Tools" && selected != "Features") IconButton(onClick = { selected = "Home" }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, title = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(selected, color = colors.primary, fontWeight = FontWeight.Bold) } }, actions = { Spacer(Modifier.size(48.dp)) })
        } }, bottomBar = { NavigationBar(containerColor = colors.surface, tonalElevation = 8.dp) { mainNav.forEach { item -> NavigationBarItem(selected = selected == item.key, onClick = { selected = item.key }, icon = { Icon(item.icon, contentDescription = item.label) }, label = { Text(item.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = colors.primary, selectedTextColor = colors.primary, indicatorColor = Color(0xFF132B49), unselectedIconColor = colors.onSurfaceVariant, unselectedTextColor = colors.onSurfaceVariant)) } } }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp).pointerInput(selected) { var totalDrag = 0f; detectHorizontalDragGestures(onDragStart = { totalDrag = 0f }, onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount }, onDragEnd = { if (kotlin.math.abs(totalDrag) >= 80f) { val nextIndex = if (totalDrag < 0) (mainIndex + 1).coerceAtMost(mainNav.lastIndex) else (mainIndex - 1).coerceAtLeast(0); selected = mainNav[nextIndex].key } }) }) {
                when (selected) {
                    "Home" -> HomePanel()
                    "Chats" -> ChatsPanel(onOpenChat = { openChat = it })
                    "Friends" -> FriendsPanel()
                    "Stories" -> StoriesPanel()
                    "Money Tools" -> MoneyToolsPanel()
                    "Features" -> FynxFeaturesPanel(onSelect = { selected = it })
                    "Studio" -> AiStudioPanel()
                    "To-Do" -> TodoPanel()
                    "Calendar" -> CalendarPanel()
                    "Profile" -> ProfilePanel(session = authSession, onSignOut = { authSession = AuthSession() })
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
        if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Settings") }, text = { Text("FYNX settings") }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } })
    }
}

@Composable
private fun FynxFeaturesPanel(onSelect: (String) -> Unit) {
    val features = listOf(
        Triple("Studio", "AI Studio", Icons.Default.Settings),
        Triple("To-Do", "To-Do", Icons.Default.Person),
        Triple("Calendar", "Calendar", Icons.Default.Home),
        Triple("Bills", "Bills & Payment Reminders", Icons.Default.Person),
        Triple("Transactions", "Transaction History", Icons.Default.Person),
        Triple("Accounts", "Accounts & Wallets 🏦", Icons.Default.Person),
        Triple("Budget", "Budget Planner 💸", Icons.Default.Person),
        Triple("Currency", "Currency Converter 💱", Icons.Default.Person),
        Triple("Savings", "Savings Goals 🎯", Icons.Default.Person),
        Triple("Subscriptions", "Subscriptions & Recurring Payments 🔄", Icons.Default.Person),
        Triple("Overview", "Financial Overview 📊", Icons.Default.Home),
        Triple("Receipts", "Receipts & Expenses 🧾", Icons.Default.Person),
        Triple("Insights", "Money Insights 📈", Icons.Default.Home),
        Triple("Spending Insights", "Spending Insights 📊", Icons.Default.Home),
        Triple("Money Alerts", "Money Alerts 🔔", Icons.Default.Settings),
        Triple("Vault", "Secure Money Vault 🔐", Icons.Default.Settings)
    )
    Column(Modifier.fillMaxSize()) {
        Text("FYNX Features", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Access your existing tools in one place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
