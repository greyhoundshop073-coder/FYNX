from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    value = (ROOT / path).read_text(encoding="utf-8")
    if not value.strip():
        raise SystemExit(f"EMPTY: {path}")
    return value

def check(name: str, condition: bool) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {name}")
    print(f"PASS: {name}")

panel = read("app/src/main/java/com/fynx/app/ui/NotificationPanel.kt")
notification_client = read("app/src/main/java/com/fynx/app/ui/FynxNotificationPreferencesClient.kt")
notification_foundation = read("app/src/main/java/com/fynx/app/ui/NotificationFoundation.kt")
notification_controls = read("app/src/main/java/com/fynx/app/ui/FynxNotificationControlsBatch3.kt")
preferences_store = read("app/src/main/java/com/fynx/app/ui/FynxPreferencesStore.kt")
settings_panel = read("app/src/main/java/com/fynx/app/ui/ProfilePanel.kt")
deep_link = read("app/src/main/java/com/fynx/app/ui/FynxDeepLink.kt")
main = read("app/src/main/java/com/fynx/app/MainActivity.kt")
backend_preferences = read("backend/notificationPreferences.js")
backend_devices = read("backend/notificationDevices.js")

check("notification UI loads server preferences", "FynxNotificationPreferencesClient.load(context)" in panel)
check("notification UI saves through server client", "FynxNotificationPreferencesClient.update(context" in panel)
check("notification preference save failures are visible and revert optimistic state", "Notification settings were not saved" in panel and "FynxNotificationPreferencesClient.cached(context)" in panel and ".onFailure" in panel)
check("rapid notification preference writes are serialized and stale snapshots skipped", "preferenceSaveMutex.withLock" in panel and "version != preferenceSaveVersion" in panel)
check("backend safety notifications bypass ordinary preference and quiet-mode gates", 'if (type === "SAFETY") return true;' in read("backend/notificationPush.js"))
check("notification preferences are account-scoped locally", "FynxAuthStore.accountStorageKey(context)" in notification_client and '"${PREFS}_${accountKey(context)}"' in notification_client)
check("notification preferences are remote-backed", '"/api/notification-preferences"' in notification_client and "FynxBackendClient.patchJson" in notification_client)
check("notification foundation applies shared controls", "FynxNotificationPreferencesClient.cached(context)" in notification_foundation and "FynxNotificationControlsBatch3.shouldPush(preferences, type)" in notification_foundation)
check("notification channels configure default alert sound", "DEFAULT_NOTIFICATION_URI" in notification_foundation and "setSound(notificationSound, notificationAudio)" in notification_foundation)
check("incoming calls use ringtone sound", "TYPE_RINGTONE" in notification_foundation and "USAGE_NOTIFICATION_RINGTONE" in notification_foundation and "setSound(ringtoneSound, ringtoneAudio)" in notification_foundation)
check("notification channels are versioned for sound migration", "fynx_messages_v2" in notification_foundation and "fynx_calls_v2" in notification_foundation)
check("notification controls define preference mapping", "fun shouldPush" in notification_controls and "messagesEnabled" in notification_controls and "walletEnabled" in notification_controls)
check("notification device registration is backend-backed", "/api/notification-devices" in backend_devices)
check("settings persist existing preferences", "fun saveSettings(context: Context, settings: FynxSettings)" in preferences_store and "fun loadSettings(context: Context)" in preferences_store)
check("settings persist appearance and accent", "fun saveAppearance(context: Context, value: String)" in preferences_store and "fun saveAccent(context: Context, accent: FynxAccent)" in preferences_store)
check("settings screen reads persisted appearance and accent", "FynxPreferencesStore.loadAppearance(context)" in settings_panel and "FynxPreferencesStore.loadAccent(context)" in settings_panel)
check("global Appearance omits the redundant Dark theme card", 'Triple("System", "System Default"' in settings_panel and 'Triple("Light"' in settings_panel and 'Triple("Black AMOLED"' in settings_panel and 'Triple("Dark", "Deep navy surfaces with subtle blue reflections"' not in settings_panel)
check("nested Profile Settings hides the duplicate parent toolbar", 'selected == "Profile" && openProfileSettings' in read("app/src/main/java/com/fynx/app/ui/FynxApp.kt"))
appearance_dialog = read("app/src/main/java/com/fynx/app/ui/FynxChatWallpaper.kt")
design_system = read("app/src/main/java/com/fynx/app/ui/FynxDesignSystem.kt")
check("Appearance offers exactly three clear global choices", all(option in appearance_dialog for option in ['Triple("System"', 'Triple("Light"', 'Triple("Dark"']) and 'Triple("Charcoal Black"' not in appearance_dialog and 'Triple("Black AMOLED"' not in appearance_dialog)
check("Appearance choices are selectable cards with immediate callback", 'onClick = { onSelected(option) }' in appearance_dialog and 'Your choice is saved immediately' in appearance_dialog)
check("Legacy dark appearance values remain safely supported by theme runtime", '"Charcoal Black" -> true' in read("app/src/main/java/com/fynx/app/ui/FynxDesignSystem.kt") and '"Black AMOLED" -> true' in read("app/src/main/java/com/fynx/app/ui/FynxDesignSystem.kt"))
check("Black AMOLED is a true black Appearance state", 'appearance == "Black AMOLED"' in read("app/src/main/java/com/fynx/app/ui/FynxDesignSystem.kt") and "AmoledBackground = Color.Black" in read("app/src/main/java/com/fynx/app/ui/FynxDesignSystem.kt"))
check("Black AMOLED keeps white primary text", "AmoledTextPrimary = Color.White" in design_system)
check("Green replaces Charcoal Black in the accent picker", 'listOf(FynxAccent.Blue, FynxAccent.Purple, FynxAccent.Green)' in appearance_dialog and 'FynxAccent.Green -> "Green"' in appearance_dialog)
check("Green accent uses the approved Classic Green color", 'Green(FynxDesign.ClassicGreen' in design_system)
check("saved charcoal accent preferences migrate to Green", '"Black", "Charcoal", "Charcoal Black" -> FynxAccent.Green' in preferences_store)
check("deep-link parser covers core notification destinations", all(token in deep_link for token in ["FynxDeepLinkDestination.Profile", "FynxDeepLinkDestination.Chat", "FynxDeepLinkDestination.Group", "FynxDeepLinkDestination.Marketplace", "FynxDeepLinkDestination.Stories", "FynxDeepLinkDestination.Money"]))
check("deep-link parser rejects unrelated hosts", "if(!isFynxScheme&&!isFynxWeb)return null" in deep_link)
check("main activity registers notification token only for signed-in account", "registerNotificationTokenIfSignedIn" in main and "AuthState.SIGNED_IN" in main)
check("backend notification preferences route exists", "registerNotificationPreferenceRoutes" in backend_preferences and "/api/notification-preferences" in backend_preferences)
check("no client API secret pattern", not any(token in notification_client or token in panel or token in main for token in ["sk-", "OPENAI_API_KEY", "STRIPE_SECRET_KEY"]))

print("GREEN: notifications/settings integration gate")
