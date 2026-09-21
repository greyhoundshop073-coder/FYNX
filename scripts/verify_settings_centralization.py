from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
profile = (ROOT / "app/src/main/java/com/fynx/app/ui/ProfilePanel.kt").read_text()
app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text()
checks = [
("Profile Settings entry", 'IconButton(onClick = { settingsOpen = true }' in profile),
("Settings accessibility label", 'contentDescription = "Settings"' in profile),
("48dp Settings touch target", 'Modifier.size(48.dp)' in profile),
("Old inline Settings button removed", 'Text("Settings")' not in profile),
("Home Settings shortcut removed", 'Icon(Icons.Default.Settings, "Settings")' not in app),
("Central Settings title", '"Settings",' in profile),
("Settings search", 'placeholder = { Text("Search settings") }' in profile),
("Account", '"Account"' in profile),
("Privacy & Security", '"Privacy & Security"' in profile),
("Notifications", '"Notifications"' in profile),
("Chat Settings", '"Chat Settings"' in profile),
("Calls", '"Calls"' in profile),
("Friends & Groups", '"Friends & Groups"' in profile),
("Stories & Status", '"Stories & Status"' in profile),
("Camera & Media", '"Camera & Media"' in profile),
("Marketplace", '"Marketplace"' in profile),
("Payments & Money", '"Payments & Money"' in profile),
("FYNX AI", '"FYNX AI"' in profile),
("Search", '"Search"' in profile),
("Appearance", '"Appearance"' in profile),
("Data & Storage", '"Data & Storage"' in profile),
("Language", '"Language"' in profile),
("Accessibility", '"Accessibility"' in profile),
("Devices & Sessions", '"Devices & Sessions"' in profile),
("Connected Accounts", '"Connected Accounts"' in profile),
("Help & Support", '"Help & Support"' in profile),
("About FYNX", '"About FYNX"' in profile),
("Privacy callback retained", 'onOpenPrivacy()' in profile),
("Notifications callback retained", 'onOpenNotifications()' in profile),
("Calls category opens real Calls surface", 'onOpenSettingsDestination("Calls")' in profile and '"Calls" -> FynxCallsPanel' in app),
("Friends & Groups category opens real Groups surface", 'onOpenSettingsDestination("Groups")' in profile and '"Groups" -> FynxGroupsPanel' in app),
("Marketplace category opens real Marketplace surface", 'onOpenSettingsDestination("Marketplace")' in profile and '"Marketplace" -> FynxMarketplacePanel' in app),
("Payments & Money category opens real Money surface", 'onOpenSettingsDestination("Money Tools")' in profile and '"Money Tools" -> MoneyCenterPanel' in app),
("FYNX AI category opens real AI surface", 'onOpenSettingsDestination("AI")' in profile and '"AI" -> FynxAiAssistantPanel' in app),
("Settings callback reaches app shell", 'onOpenSettingsDestination = { destination -> selected = destination }' in app),
("Appearance persistence", 'FynxPreferencesStore.saveAppearance(context, it)' in profile),
("Accent persistence", 'FynxPreferencesStore.saveAccent(context, it)' in profile),
("Language persistence", 'FynxPreferencesStore.saveLanguage(context, it)' in profile),
]
failed=[n for n,ok in checks if not ok]
if failed:
    print("FYNX SETTINGS CENTRALIZATION RED")
    for n in failed: print("-",n)
    raise SystemExit(1)
print("FYNX SETTINGS CENTRALIZATION GREEN")
print(f"Verified {len(checks)} invariants.")
