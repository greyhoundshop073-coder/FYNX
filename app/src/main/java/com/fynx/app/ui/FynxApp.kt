package com.fynx.app.ui

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FynxApp() {
    var selected by remember { mutableStateOf("Home") }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var toolsExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var authSession by remember { mutableStateOf(AuthSession(state = AuthState.SIGNED_IN, username = "username")) }
    if (openChat != null) { ConversationPanel(chat = openChat!!, onBack = { openChat = null }); return }
    val colors = darkColorScheme(primary = Color(0xFF2F8CFF), onPrimary = Color.White, secondary = Color(0xFF22C7F2), background = Color(0xFF071326), onBackground = Color(0xFFF5F8FF), surface = Color(0xFF0D1B2E), onSurface = Color(0xFFF5F8FF), surfaceVariant = Color(0xFF15263D), onSurfaceVariant = Color(0xFFB9C6D8), outline = Color(0xFF31445F))
    MaterialTheme(colorScheme = colors) {
        Scaffold(containerColor = colors.background, topBar = {
            when (selected) {
                "Home" -> TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background), navigationIcon = { FynxAvatar("username", Modifier.size(34.dp)) }, title = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("FYNX", color = colors.primary, fontWeight = FontWeight.Bold) } }, actions = { Box { IconButton(onClick = { toolsExpanded = true }) { Icon(Icons.Default.Settings, contentDescription = "Tools and settings") }; DropdownMenu(expanded = toolsExpanded, onDismissRequest = { toolsExpanded = false }) { DropdownMenuItem(text = { Text("AI Studio") }, onClick = { selected = "Studio"; toolsExpanded = false }); DropdownMenuItem(text = { Text("To-Do") }, onClick = { selected = "To-Do"; toolsExpanded = false }); DropdownMenuItem(text = { Text("Calendar") }, onClick = { selected = "Calendar"; toolsExpanded = false }); DropdownMenuItem(text = { Text("Money Tools 💰") }, onClick = { selected = "Money Tools"; toolsExpanded = false }); DropdownMenuItem(text = { Text("Settings") }, onClick = { toolsExpanded = false; showSettings = true }) } } })
                else -> TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background), navigationIcon = { IconButton(onClick = { selected = "Home" }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, title = { Text(selected, color = colors.primary, fontWeight = FontWeight.Bold) })
            }
        }, bottomBar = {
            NavigationBar(containerColor = colors.surface) {
                val items = listOf("Home", "Chats", "Friends", "Stories", "Profile")
                items.forEach { item -> NavigationBarItem(selected = selected == item, onClick = { selected = item }, icon = { Icon(Icons.Default.Person, contentDescription = item) }, label = { Text(item) }) }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
                when (selected) {
                    "Home" -> HomePanel(); "Chats" -> ChatsPanel(onOpenChat = { openChat = it }); "Friends" -> FriendsPanel(); "Stories" -> StoriesPanel(); "Studio" -> AiStudioPanel(); "To-Do" -> TodoPanel(); "Calendar" -> CalendarPanel(); "Money Tools" -> MoneyToolsPanel(); "Profile" -> ProfilePanel(session = authSession, onSignOut = { authSession = AuthSession() })
                }
            }
        }
        if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Settings") }, text = { Text("FYNX settings") }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } })
    }
}