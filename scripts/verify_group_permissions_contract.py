from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
group_routes = (ROOT / "backend/groupRoutes.js").read_text(encoding="utf-8")
membership_routes = (ROOT / "backend/groupMembershipRoutes.js").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt").read_text(encoding="utf-8")
active = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt").read_text(encoding="utf-8")
settings = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupSettingsPanel.kt").read_text(encoding="utf-8")

normalized_group_routes = "".join(group_routes.split())

checks = {
    "server blocks text sends when group send_messages is disabled": "GROUP_SEND_MESSAGES_DISABLED" in group_routes and "if(!canManageMembers(actor.role)&&text&&!groupSettings.send_messages)" in normalized_group_routes,
    "server blocks attachments when group send_media is disabled": "GROUP_SEND_MEDIA_DISABLED" in group_routes and "!groupSettings.send_media" in group_routes,
    "server grants permission overrides only to group managers": "!canManageMembers(actor.role)&&text&&!groupSettings.send_messages" in group_routes and "!canManageMembers(actor.role)&&attachmentMediaId!==null&&!groupSettings.send_media" in group_routes,
    "server has a dedicated add-member endpoint": "app.post('/api/groups/:groupId/members'" in membership_routes and "GROUP_ADD_MEMBERS_DISABLED" in membership_routes,
    "non-manager member adds respect the server add_members setting": "!canManageMembers(actor.role) && !settings?.add_members" in membership_routes,
    "direct member adds cannot bypass admin approval": "GROUP_APPROVAL_REQUIRED" in membership_routes and "settings?.approve_new_members" in membership_routes,
    "active group UI respects the approval-required setting": "remotePermissions?.approveNewMembers != true" in active,
    "Android client uses the dedicated member-add endpoint": '"/api/groups/$groupId/members"' in client and "suspend fun addMember" in client,
    "active group chat loads and uses remote send/media/member permissions": "FynxGroupRemoteClient.loadPermissions(context, groupId)" in active and "remotePermissions?.sendMessages" in active and "remotePermissions?.sendMedia" in active and "remotePermissions?.addMembers" in active,
    "authenticated group actions fail closed until server permissions load": "val hasRemoteAccessToken = FynxBackendClient.hasAccessToken(context)" in active and "if (hasRemoteAccessToken) remotePermissions?.sendMessages == true" in active and "if (hasRemoteAccessToken) remotePermissions?.sendMedia == true" in active and "remotePermissions?.addMembers == true" in active and "permissionLoadError" in active,
    "active group member action no longer uses manager-only full-group sync": "FynxGroupRemoteClient.addMember(context, groupId, cleanUsername)" in active and "onInvite = { username ->" in active,
    "permission toggles preserve existing disappearing_seconds value": "disappearingSeconds=remote.disappearingSeconds" in settings and "approveNewMembers,disappearingSeconds" in settings,
    "story shares are pending until the backend accepts them and remain retryable on failure": 'ChatMessage("Story shared to ${groupForDialogs.name}", true, UUID.randomUUID().toString(), delivered = false, read = false)' in active and "Story not sent yet. Your share is saved" in active,
    "Group Tools media shares use the staged retry-safe attachment pipeline instead of a separate send path": 'onMedia = { uri, type ->' in active and 'sendGroupAttachment(uri, type)' in active[active.find('onMedia = { uri, type ->'):active.find('}, onStoryShare = {', active.find('onMedia = { uri, type ->'))] and 'createGroupMediaMessage(uri, type)' not in active[active.find('onMedia = { uri, type ->'):active.find('}, onStoryShare = {', active.find('onMedia = { uri, type ->'))],
    "retry-all filters each unsent group message by its text-versus-media permission": "val retryablePendingMessages = messages.filter { failedMessage ->" in active and "TextButton(enabled = !sending && retryablePendingMessages.isNotEmpty(), onClick = {" in active and "if (!sending && retryablePendingMessages.isNotEmpty()) {" in active and active.count("failedMessage.attachmentUri != null || failedMessage.attachmentType in setOf(\"image\", \"video\", \"video_note\", \"audio\", \"document\")") >= 2 and "val pendingMessages = messages.filter { failedMessage ->" in active,
    "group message sync does not clear a local persistence error immediately after setting it": active.find("if (showError) syncMessage = null") < active.find('FynxChatStore.save(context, "group_$groupId", mergedMessages)') and "Your group messages could not be saved locally. Please try again." in active,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("GROUP PERMISSIONS CONTRACT RED: " + "; ".join(failed))
print(f"GROUP PERMISSIONS CONTRACT GREEN ({len(checks)} checks)")
