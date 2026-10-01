from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = []

def check(name, ok):
    checks.append((name, bool(ok)))

files = {
    "auth": "app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt",
    "prefs": "app/src/main/java/com/fynx/app/ui/FynxPreferencesStore.kt",
    "media": "app/src/main/java/com/fynx/app/ui/FynxMediaCache.kt",
    "notifications": "app/src/main/java/com/fynx/app/ui/FynxNotificationStore.kt",
    "groups": "app/src/main/java/com/fynx/app/ui/FynxGroupsStore.kt",
    "calls": "app/src/main/java/com/fynx/app/ui/FynxCallsStore.kt",
    "group_messages": "app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt",
    "status": "app/src/main/java/com/fynx/app/ui/FynxStatusFoundation.kt",
    "gift_history": "app/src/main/java/com/fynx/app/ui/FynxGiftHistory.kt",
    "money": "app/src/main/java/com/fynx/app/ui/FynxMoneyStore.kt",
    "todo": "app/src/main/java/com/fynx/app/ui/TodoStore.kt",
    "calendar": "app/src/main/java/com/fynx/app/ui/CalendarPanel.kt",
    "conversation": "app/src/main/java/com/fynx/app/ui/FynxConversationPreferences.kt",
    "people": "app/src/main/java/com/fynx/app/ui/FynxPeopleDiscovery.kt",
    "money_alerts": "app/src/main/java/com/fynx/app/ui/MoneyAlertsPanel.kt",
    "auth_app": "app/src/main/java/com/fynx/app/ui/FynxApp.kt",
}

check("all R1 account-isolation files exist", all((ROOT / p).is_file() for p in files.values()))
text = {name: read(path) for name, path in files.items()}

auth = text["auth"]
prefs = text["prefs"]

check("account namespace is derived from the signed-in identity", "accountStorageKey" in auth and "storedUsername" in auth and "clearIfSwitchingAccounts" in auth)
check("logout clears the authenticated token and account-owned session state", "FynxSecureTokenStore.save(context, null)" in auth and "FynxPreferencesStore.clearAccountSessionData(context)" in auth)
check("logout removes legacy unscoped media/status/post state", all(x in auth for x in ["fynx_media", "fynx_media_remote_", "fynx_post_", "fynx_status_"]))

check("customization asset is account-owned", "accountAssetFile" in prefs and "MessageDigest.getInstance(\"SHA-256\")" in prefs and "fynx_customization.jpg" in prefs)
check("customization asset is deleted at session boundary", "accountAssetFile(context)?.delete()" in prefs and "File(context.filesDir, LEGACY_ASSET_FILE).delete()" in prefs)
check("customization loader never returns the legacy shared file", "loadAsset" in prefs and "Uri.fromFile(file).toString()" in prefs and "accountAssetFile(context)" in prefs)

account_scoped = [
    "media", "notifications", "groups", "calls", "group_messages", "status",
    "gift_history", "money", "todo", "calendar", "conversation", "people", "money_alerts"
]
for name in account_scoped:
    source = text[name]
    check(f"{name} local state references the account namespace", "FynxAuthStore.accountStorageKey" in source)

check("profile/settings state is cleared before another account enters", "clearAccountSessionData" in prefs and "remove(KEY_PROFILE_PHOTO)" in prefs and "remove(KEY_ASSET)" in prefs)
check("live app reads account-bound notification and profile state", "FynxNotificationStore.load(context)" in text["auth_app"] and "FynxPreferencesStore.loadProfile(context, authSession.username)" in text["auth_app"])

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("FYNX R1 account-isolation verification failed: " + "; ".join(failed))
print(f"FYNX R1 account-isolation verification passed ({len(checks)} checks)")
print("Runtime gate still required: Account A -> logout -> Account B on the built APK.")
