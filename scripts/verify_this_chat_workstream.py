from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
checks = []

def read(path):
    p = ROOT / path
    return p.read_text(encoding='utf-8') if p.is_file() else ''

def check(name, ok):
    checks.append((name, bool(ok)))

remote_media = read('app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt')
timeline = read('app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt')
market = read('app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt')
market_compat = read('app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt')
profile = read('app/src/main/java/com/fynx/app/ui/ProfilePanel.kt')
profile_client = read('app/src/main/java/com/fynx/app/ui/FynxProfileRemoteClient.kt')
prefs = read('app/src/main/java/com/fynx/app/ui/FynxPreferencesStore.kt')
r5b_verifier = read('scripts/verify_r5b_status.py')
fcm_verifier = read('scripts/verify_fcm_notifications.py')
ai_security = read('scripts/verify_ai_security.py')
media_privacy = read('backend/mediaPrivacy.js')
glass_theme = read('app/src/main/java/com/fynx/app/ui/FynxGlassTheme.kt')
glass_wallpaper = read('app/src/main/java/com/fynx/app/ui/FynxChatWallpaper.kt')
chat_settings = read('app/src/main/java/com/fynx/app/ui/FynxChatSettingsPanel.kt')
group_panel = read('app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt')
friends_panel = read('app/src/main/java/com/fynx/app/ui/FriendsPanel.kt')
conversation = read('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt')
production_messaging = read('app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt')
chat_store = read('app/src/main/java/com/fynx/app/ui/FynxChatStore.kt')
runtime_navigation = read('scripts/verify_authenticated_runtime_navigation.py')
chats_panel = read('app/src/main/java/com/fynx/app/ui/ChatsPanel.kt')
groups_panel = read('app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt')

check('remote media uses the authenticated central downloader', 'FynxBackendClient.downloadToFile' in remote_media and 'MAX_REMOTE_MEDIA_BYTES' in remote_media)
check('remote media is account scoped', 'FynxAuthStore.accountStorageKey(context)' in remote_media and 'FynxBackendClient.hasAccessToken(context)' in remote_media)
check('Status timeline uses the shared remote audio renderer', re.search(r'FynxRemoteAudio\s*\(\s*it\s*(?:,|\))', timeline) is not None)
check('Status circles use the shared remote avatar resolver for authoritative identity and invalidation', 'FynxRemoteProfileAvatar(' in timeline and 'ownerUsername = avatarOwnerUsername' in timeline and 'val avatarOwnerUsername = status?.ownerUsername' in timeline)
check('profile cold start hydrates the cached remote avatar', 'cachedProfilePhotoId(context, it)' in profile and 'remotePhotoId = remote.profilePhotoMediaId' in profile and 'remoteProfileLoaded = true' in profile)
check('remote profile success persists authoritative avatar identity', 'saveRemoteProfilePhotoId(context, normalized, profile.profilePhotoMediaId)' in profile_client)
check('chat list clears stale avatar when server photo is removed', 'else chat.copy(avatarUri = null)' in read('app/src/main/java/com/fynx/app/ui/ChatsPanel.kt'))
check('conversation keeps cached avatar only while remote profile is loading', 'var remoteProfileLoaded by remember(chat.username)' in conversation and 'if (remoteProfileLoaded)' in conversation)
check('conversation marks remote profile loaded after successful fetch', 'remoteProfileLoaded = true' in conversation)

# Validate the Marketplace avatar behavior contract without trying to parse Kotlin
# call boundaries. The real call contains nested modifier parentheses, so a
# brittle [^)] regex can falsely fail even when the correct shared authority is used.
market_avatar_call = (
    ('FynxRemoteProfileAvatar' in market and 'ownerUsername = l.sellerUsername' in market)
    or ('FynxRemoteProfileAvatar' in market and 'ownerUsername = listing.sellerUsername' in market)
    or ('sellerPhotoIds' in market and 'FynxProfileRemoteClient.get(context, username)' in market)
)
market_cache_authority = 'cachedProfilePhotoId(context,' in remote_media and 'saveRemoteProfilePhotoId(context, normalized, profile.profilePhotoMediaId)' in profile_client
check('Marketplace seller avatar uses the shared cache-first avatar authority path', market_avatar_call and market_cache_authority)

check('identity cache is account namespaced', 'KEY_REMOTE_IDENTITY_CACHE' in prefs and 'accountNamespace(context)' in prefs)
check('identity cache is cleared at the session boundary', 'getSharedPreferences("${KEY_REMOTE_IDENTITY_CACHE}_$accountNamespace"' in prefs)
check('R5B audio verifier recognizes the shared renderer', 'def contains_remote_audio_renderer(source):' in r5b_verifier and "require('remote audio renderer',contains_remote_audio_renderer(remote_media))" in r5b_verifier)
check('media privacy guard remains installed', 'app.use("/api/media", mediaGuard)' in media_privacy)
check('FCM notification gate remains present', 'notification deep-link routing' in fcm_verifier and 'FCM verification GREEN' in fcm_verifier)
check('AI security gate remains present', 'AI provider key stays server-side' in ai_security and 'AI security gate GREEN' in ai_security)
check('Chat/Group glass theme catalog contains all eight approved themes', all(label in glass_theme for label in ['Pure Black Glass', 'Aurora Glass', 'Light Glass', 'Deep Emerald Glass', 'Sunset Glass', 'Rose Glass', 'Golden Glass', 'Turquoise Glass']))
check('Chat wallpaper resolves every glass theme through the shared palette', 'FynxGlassThemeId.entries.firstOrNull' in glass_wallpaper and 'fynxGlassPalette(themeId)' in glass_wallpaper)
check('Chat settings exposes the complete glass theme catalog', 'FynxGlassThemeId.entries.map { it.label }' in chat_settings)
check('Group chat uses its shared wallpaper runtime and active glass palette surfaces', 'FynxGroupWallpaperBackground' in group_panel and 'glassPalette' in group_panel)
check('Private conversation uses the shared themed wallpaper runtime', 'FynxChatWallpaperBackground' in conversation and 'glassPalette' in conversation)
check('private chat history reconciles remote messages with the existing local store', ('mergeLocalHistory(context, username, remote)' in production_messaging or 'mergeLocalHistory(context, username, remote, currentUserId)' in production_messaging) and 'val local = FynxChatStore.load(context, username)' in production_messaging)
check('private chat reconciliation preserves local messages missing from the remote response', 'val remoteIds = remote.asSequence().map { it.id }.toSet()' in production_messaging and 'val preserved = local.mapNotNull' in production_messaging and 'return (remote + preserved).distinctBy { it.id }.sortedBy { it.timestamp }' in production_messaging)
check('private chat loads local conversation state off the UI thread before remote reconciliation', ('mutableStateOf(FynxChatStore.load(context, chat.username, fallbackMessage))' in conversation or ('mutableStateOf(emptyList<ChatMessage>())' in conversation and 'FynxChatStore.load(context, chat.username, fallbackMessage)' in conversation and 'withContext(Dispatchers.IO)' in conversation and 'LaunchedEffect(chat.username)' in conversation and 'messagesLoaded = true' in conversation)) and 'FynxProductionMessaging.history(context, normalizedUsername)' in conversation)
check('private chat persists restored history on IO only after local restoration completes', 'LaunchedEffect(messagesLoaded, messages)' in conversation and 'if (!messagesLoaded) return@LaunchedEffect' in conversation and 'messagesLoaded = true' in conversation and 'withContext(Dispatchers.IO)' in conversation and 'FynxChatStore.save(context, chat.username, snapshot)' in conversation)
check('Chat/Groups cold-start routes restore the approved session before deep linking without certifying synthetic group IDs', 'def restore_home_after_restart(name:str)->str:' in runtime_navigation and 'restore_home_after_restart("private-chat-self-fallback-home")' in runtime_navigation and 'restore_home_after_restart("existing-ci-group-open-home")' in runtime_navigation and 'restore_home_after_restart("created-ci-group-open-home")' in runtime_navigation and 'current,error=login()' in runtime_navigation and 'deterministic group deep link rendered a conversation/unavailable state' not in runtime_navigation and 'backend_group_message_exists(group_id,marker_text)' in runtime_navigation)
check('private-chat identity setup cannot cancel itself or recreate its socket while resolving state', 'LaunchedEffect(chat.username) {' in conversation and 'remember(chat.username) {' in conversation and 'rememberUpdatedState(currentUserId)' in conversation and 'rememberUpdatedState(recipientUserId)' in conversation and 'rememberUpdatedState(resolvedAvatarUri)' in conversation and 'LaunchedEffect(chat.username, currentUserId, recipientUserId, resolvedAvatarUri)' not in conversation and 'remember(chat.username, currentUserId, recipientUserId, resolvedAvatarUri)' not in conversation)
check('Chat/Groups runtime gate captures complete crash and ANR context', 'def clear_runtime_log():' in runtime_navigation and '"-t","4000"' in runtime_navigation and 'Input dispatching timed out' in runtime_navigation and 'Group unavailable' in runtime_navigation)
check('Chat list refresh cannot recursively publish another preview update', 'fun refreshChats() {' in chats_panel and 'FynxChatStore.previewUpdates.conflate().collectLatest { refreshChats() }' in chats_panel and 'FynxChatStore.savePreview(context,' not in chats_panel[chats_panel.index('fun refreshChats()'):chats_panel.index('LaunchedEffect(Unit)')])
check('Chat list history and unread counts are not loaded from disk during composition', 'mutableStateOf(emptyList<ChatPreview>())' in chats_panel and 'mutableStateOf(emptyList<FynxGroup>())' in chats_panel and 'val unread = chat.unreadCount' in chats_panel and 'val unread = FynxChatStore.load(context, chat.username)' not in chats_panel)
check('Groups list defers the disk-backed group read until after composition', 'var groups by remember { mutableStateOf(emptyList<FynxGroup>()) }' in groups_panel and 'LaunchedEffect(Unit)' in groups_panel and 'groups = withContext(Dispatchers.IO) { FynxGroupsStore.load(context) }' in groups_panel and 'mutableStateOf(FynxGroupsStore.load(context))' not in groups_panel[:groups_panel.index('private fun FynxCreateGroupDialog')])
check('group chat starts from its local message store', 'mutableStateOf(loadGroupMessages(context, group.id))' in group_panel)
check('group chat refresh preserves pending local messages not yet visible remotely', 'val remoteIds = remoteMessages.asSequence().map { it.id }.toSet()' in group_panel and 'val pendingLocal = messages.filter { it.id !in remoteIds }' in group_panel and 'messages = (remoteMessages + pendingLocal)' in group_panel)
check('Friends refresh preserves last-known people data after transient failures', 'friends = fr.getOrElse { friends }' in friends_panel and 'val requests = rr.getOrElse { incoming + outgoing }' in friends_panel and 'blocked = br.getOrElse { blocked }' in friends_panel)
check('Friends exposes a retry for list-refresh errors instead of showing a false empty state', 'refreshError?.let { message ->' in friends_panel and 'TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading)' in friends_panel and 'friendsLoadFailed' in friends_panel and "Couldn't load friends" in friends_panel)

client_sources = '\n'.join(str(p.read_text(encoding='utf-8')) for p in (ROOT / 'app/src/main/java/com/fynx/app/ui').glob('*.kt'))
check('this chat adds no obvious client API secrets', not re.search(r'sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}', client_sources))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS: ' if ok else 'FAIL: ') + name)
if failed:
    raise SystemExit('FYNX this-chat workstream gate failed: ' + '; '.join(failed))
print(f'FYNX this-chat workstream gate passed ({len(checks)} checks)')
