package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PhotoCamera
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
    var showSettings by remember { mutableStateOf(false) }
    var authSession by remember { mutableStateOf(AuthSession(state = AuthState.SIGNED_IN, username = "username")) }

    if (openChat != null) {
        ConversationPanel(chat = openChat!!, onBack = { openChat = null })
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
                when (selected) {
                    "Home" -> TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = fynxDarkColors.background,
                            titleContentColor = fynxDarkColors.primary
                        ),
                        navigationIcon = {
                            FynxAvatar("username", Modifier.size(34.dp))
                        },
                        title = {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("FYNX", color = fynxDarkColors.primary, fontWeight = FontWeight.Bold)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    )
                    "Friends" -> TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = fynxDarkColors.background,
                            titleContentColor = fynxDarkColors.primary
                        ),
                        navigationIcon = {
                            IconButton(onClick = { selected = "Home" }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Friends", color = fynxDarkColors.primary, fontWeight = FontWeight.Bold)
                            }
                        },
                        actions = { Spacer(Modifier.size(48.dp)) }
                    )
                    "Stories" -> TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = fynxDarkColors.background,
                            titleContentColor = fynxDarkColors.primary
                        ),
                        navigationIcon = {
                            IconButton(onClick = { selected = "Home" }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Stories", color = fynxDarkColors.primary, fontWeight = FontWeight.Bold)
                            }
                        },
                        actions = { Spacer(Modifier.size(48.dp)) }
                    )
                    else -> TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = fynxDarkColors.background),
                        title = {
                            Text(selected, color = fynxDarkColors.primary, fontWeight = FontWeight.Bold)
                        }
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = fynxDarkColors.surface,
                    tonalElevation = 8.dp
                ) {
                    val items = listOf(
                        Triple("Home", Icons.Default.Home, "Home"),
                        Triple("Chats", Icons.Default.ChatBubbleOutline, "Chats"),
                        Triple("Friends", Icons.Default.People, "Friends"),
                        Triple("Stories", Icons.Default.PhotoCamera, "Stories"),
                        Triple("Profile", Icons.Default.Person, "Profile")
                    )
                    items.forEach { (item, icon, label) ->
                        NavigationBarItem(
                            selected = selected == item,
                            onClick = { selected = item },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
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

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Settings") },
                text = { Text("FYNX settings") },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) { Text("Done") }
                }
            )
        }
    }
}
