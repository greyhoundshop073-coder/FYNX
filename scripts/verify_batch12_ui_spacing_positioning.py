#!/usr/bin/env python3
"""Batch 12 compact-screen layout and navigation guardrails."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text()
conv = (ROOT / "app/src/main/java/com/fynx/app/ui/ConversationPanel.kt").read_text()
emoji = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxChatEmojiPanel.kt").read_text()
status = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxStatusHubPanel.kt").read_text()
updates = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxVisibleUpdatesPanel.kt").read_text()

checks = {
    "primary navigation includes Home": 'FynxNavItem("Home", "Home"' in app,
    "primary navigation includes Chat": 'FynxNavItem("Chats", "Chat"' in app,
    "primary navigation includes Friends": 'FynxNavItem("Friends", "Friends"' in app,
    "primary navigation keeps Marketplace": 'FynxNavItem("Marketplace", "Market", Icons.Default.ShoppingBag)' in app,
    "primary navigation keeps More as fifth compact destination": 'FynxNavItem("Features", "More"' in app,
    "Marketplace remains reachable from More": 'Triple("Marketplace", "Marketplace", Icons.Default.ShoppingBag)' in app,
    "Money Center remains reachable from More": 'Triple("Money Tools", "Money Center", Icons.Default.AccountBalanceWallet)' in app,
    "chat header uses compact back icon": 'IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }' in conv,
    "chat header search is menu action, not extra header icon": 'Text(if (searchOpen) "Close search" else "Search messages")' in conv,
    "chat composer has controlled horizontal padding": 'Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)' in conv,
    "emoji categories can scroll horizontally": '.horizontalScroll(rememberScrollState())' in emoji,
    "emoji grid capped for compact screens": 'heightIn(min = 176.dp, max = 260.dp)' in emoji,
    "status composer actions respect bottom safe area": 'navigationBarsPadding()' in status,
    "friends header actions can scroll horizontally": '.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)' in (ROOT / "app/src/main/java/com/fynx/app/ui/FriendsPanel.kt").read_text(),
    "home status row is horizontally scrollable": 'LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)' in updates,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    print("BATCH 12 RED")
    for name in failed:
        print(" -", name)
    raise SystemExit(1)

print("BATCH 12 GREEN")
for name in checks:
    print("PASS:", name)
