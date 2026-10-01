package com.fynx.app.ui

sealed class FynxDestination(val title: String, val shortLabel: String) {
    data object Home : FynxDestination("FYNX", "Home")
    data object Chats : FynxDestination("Chats", "Chats")
    data object Friends : FynxDestination("Friends", "Friends")
    data object Stories : FynxDestination("Stories", "Stories")
    data object Profile : FynxDestination("Profile", "Profile")
}
