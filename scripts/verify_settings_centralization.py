#!/usr/bin/env python3
"""Verify central FYNX Settings routing and real-control ownership."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
profile = read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt")
prefs = read("app/src/main/java/com/fynx/app/ui/FynxPreferencesStore.kt")

checks = [
    ("Profile owns the central Settings entry point", 'IconButton(onClick = { settingsOpen = true' in profile),
    ("Home no longer exposes a Settings shortcut", 'IconButton(onClick = { selected = "Profile"; openProfileSettings = true })' not in app),
    ("Settings has searchable central surface", 'placeholder = { Text("Search settings") }' in profile and 'var search by remember' in profile),
    ("Privacy routes to real privacy controls", 'onOpenPrivacy()' in profile and '"Privacy" -> FynxPrivacySettingsPanel' in app),
    ("Notifications route to real notification controls", 'onOpenNotifications()' in profile and '"Notifications" -> NotificationPanel' in app),
    ("Chat settings persist through existing store", 'ChatPersonalizationDialog(settings, onSettingsChange)' in profile and 'saveChatWallpaper' in prefs),
    ("Appearance persists", 'FynxPreferencesStore.saveAppearance(context, it)' in profile),
    ("Accent persists", 'FynxPreferencesStore.saveAccent(context, it)' in profile),
    ("Language persists", 'FynxPreferencesStore.saveLanguage(context, it)' in profile),
    ("Calls route to existing calls surface", 'onOpenSettingsDestination("Calls")' in profile and '"Calls" -> FynxCallsPanel' in app),
    ("Groups route to existing groups surface", 'onOpenSettingsDestination("Groups")' in profile and '"Groups" -> FynxGroupsPanel' in app),
    ("Marketplace routes to existing marketplace surface", 'onOpenSettingsDestination("Marketplace")' in profile and '"Marketplace" -> FynxMarketplacePanel' in app),
    ("Money routes to existing money surface", 'onOpenSettingsDestination("Money Tools")' in profile and '"Money Tools" -> MoneyCenterPanel' in app),
    ("AI routes to existing AI surface", 'onOpenSettingsDestination("AI")' in profile and '"AI" -> FynxAiAssistantPanel' in app),
    ("Professional tools route to existing surfaces", 'onOpenSettingsDestination("Business Account")' in profile and 'onOpenSettingsDestination("Advertising")' in profile),
    ("Saved posts route to existing surface", 'onOpenSettingsDestination("Saved Posts")' in profile and '"Saved Posts" -> FynxSavedPostsPanel' in app),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Settings centralization verification failed: " + "; ".join(failed))
print(f"Settings centralization verification passed ({len(checks)} checks).")
