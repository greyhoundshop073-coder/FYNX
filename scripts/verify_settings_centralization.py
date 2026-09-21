from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

profile = (ROOT / "app/src/main/java/com/fynx/app/ui/ProfilePanel.kt").read_text()
app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text()

checks = [
    ("Profile owns the Settings entry point", 'IconButton(onClick = { settingsOpen = true }' in profile),
    ("Profile Settings icon has an accessible label", 'contentDescription = "Settings"' in profile),
    ("Profile Settings icon uses a 48dp touch target", 'Modifier.size(48.dp)' in profile),
    ("Old inline Profile Settings button is removed", 'Text("Settings")' not in profile),
    ("Home Settings shortcut is removed", 'Icon(Icons.Default.Settings, "Settings")' not in app),
    ("Central Settings title is present", '"Settings",' in profile),
    ("Settings search is present", 'placeholder = { Text("Search settings") }' in profile),
    ("Account settings category", '"Account"' in profile and '"Profile, username, bio and account information"' in profile),
    ("Privacy and Security category", '"Privacy & Security"' in profile),
    ("Notifications category", '"Notifications"' in profile),
    ("Chat Settings category", '"Chat Settings"' in profile),
    ("Calls category", '"Calls"' in profile),
    ("Friends and Groups category", '"Friends & Groups"' in profile),
    ("Stories and Status category", '"Stories & Status"' in profile),
    ("Camera and Media category", '"Camera & Media"' in profile),
    ("Marketplace category", '"Marketplace"' in profile),
    ("Payments and Money category", '"Payments & Money"' in profile),
    ("FYNX AI category", '"FYNX AI"' in profile),
    ("Search category", '"Search"' in profile),
    ("Appearance category", '"Appearance"' in profile),
    ("Data and Storage category", '"Data & Storage"' in profile),
    ("Language category", '"Language"' in profile),
    ("Accessibility category", '"Accessibility"' in profile),
    ("Devices and Sessions category", '"Devices & Sessions"' in profile),
    ("Connected Accounts category", '"Connected Accounts"' in profile),
    ("Help and Support category", '"Help & Support"' in profile),
    ("About FYNX category", '"About FYNX"' in profile),
    ("Existing privacy settings remain wired", 'onOpenPrivacy()' in profile),
    ("Existing notification settings remain wired", 'onOpenNotifications()' in profile),
    ("Appearance persistence remains wired", 'FynxPreferencesStore.saveAppearance(context, it)' in profile),
    ("Accent persistence remains wired", 'FynxPreferencesStore.saveAccent(context, it)' in profile),
    ("Language persistence remains wired", 'FynxPreferencesStore.saveLanguage(context, it)' in profile),
]

failed = [name for name, ok in checks if not ok]
if failed:
    print("FYNX SETTINGS CENTRALIZATION RED")
    for name in failed:
        print(f"- {name}")
    raise SystemExit(1)

print("FYNX SETTINGS CENTRALIZATION GREEN")
print(f"Verified {len(checks)} settings/navigation invariants.")
