package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FynxApp() {
    var selected by remember { mutableStateOf("Home") }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var showNotifications by remember { mutableStateOf(false) }
    var authSession by remember { mutableStateOf(AuthSession(state = AuthState.SIGNED_IN, username = "username")) }

    if (openChat != null) {
        ConversationPanel(chat = openChat!!, onBack = { openChat = null })
        return
    }
    if (showNotifications) {
        NotificationPanel(notifications = emptyList(), onBack = { showNotifications = false })
        return
    }

    val fynxDarkColors = darkColorScheme(
        primary = Color(0xFF2F8CFF),
        onPrimary = Color.White,
        secondary = Color(0xFF22C7F2),
        background = Color(0xFF071326),
        onBackground = Color(0xFFF5F8FF),
        surface = Color(0xFF0D1B2E),
        onSurface = Color(0xFFF5F8FF),
        surfaceVariant = Color(0xFF15263D),
        onSurfaceVariant = Color(0xFFB9C6D8),
        outline = Color(0xFF31445F)
    )

    MaterialTheme(colorScheme = fynxDarkColors) {
        Scaffold(
            containerColor = fynxDarkColors.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = fynxDarkColors.background,
                        titleContentColor = fynxDarkColors.onBackground
                    ),
                    navigationIcon = {
                        FynxAvatar("FYNX", Modifier.size(34.dp))
                    },
                    title = {
                        Text(
                            "FYNX",
                            color = fynxDarkColors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = { showNotifications = true }) {
                            Text("⚙", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = fynxDarkColors.surface,
                    tonalElevation = 8.dp
                ) {
                    listOf("Home", "Chats", "Friends", "Stories", "Profile").forEach { item ->
                        NavigationBarItem(
                            selected = selected == item,
                            onClick = { selected = item },
                            icon = { Text(item.take(1)) },
                            label = { Text(item) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = fynxDarkColors.primary,
                                selectedTextColor = fynxDarkColors.primary,
                                indicatorColor = Color(0xFF132B49),
                                unselectedIconColor = fynxDarkColors.onSurfaceVariant,
                                unselectedTextColor = fynxDarkColors.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (selected) {
                    "Home" -> HomePanel()
                    "Chats" -> ChatsPanel(onOpenChat = { openChat = it })
                    "Friends" -> FriendsPanel()
                    "Stories" -> StoriesPanel()
                    "Profile" -> ProfilePanel(
                        session = authSession,
                        onSignOut = { authSession = AuthSession() }
                    )
                }
            }
        }
    }
}
