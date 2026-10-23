import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
group_routes = (ROOT / "backend/groupRoutes.js").read_text(encoding="utf-8")
membership_routes = (ROOT / "backend/groupMembershipRoutes.js").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt").read_text(encoding="utf-8")
active = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt").read_text(encoding="utf-8")
settings = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupSettingsPanel.kt").read_text(encoding="utf-8")
group_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt").read_text(encoding="utf-8")
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")

normalized_group_routes = "".join(group_routes.split())
ast.parse(runtime, filename="scripts/verify_authenticated_runtime_navigation.py")

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
    "group sends distinguish a first insert from an idempotent retry": "ON CONFLICT(id) DO NOTHING RETURNING id" in group_routes and "const created = inserted.rows.length > 0" in group_routes,
    "group message ID collisions cannot return another sender's or another group's message": "m.group_id,m.sender_id" in group_routes and "if(String(row.group_id)!==groupId||String(row.sender_id)!==String(req.user.sub))" in normalized_group_routes and "MESSAGE_ID_CONFLICT" in group_routes,
    "group retries rebroadcast the canonical message to recover failed realtime delivery": "await broadcastGroupMessage(groupId, message)" in group_routes and "const created = inserted.rows.length > 0" in group_routes and "res.status(created?201:200).json({message})" in normalized_group_routes,
"group POST retries replay stable IDs after persisted history confirms a possibly committed send": "suspend fun postGroupMessage(): RemoteMessage" in group_client and group_client.count("postGroupMessage()") >= 2 and "if (persistedOnServer == null) throw firstError" in group_client and "throw retryError" in group_client,
    "runtime cert checks persisted group roster and account ADMIN role": "backend_group_access_contract(group_id)" in runtime and '"/api/groups/"+group_path+"/members"' in runtime and 'role!="ADMIN"' in runtime,
    "runtime cert verifies permission changes and restores original CI-group settings": 'probe["inviteLinks"]=not original["inviteLinks"]' in runtime and 'request_json("PATCH","/api/groups/"+group_path+"/permissions",original)' in runtime and "restored_ok=all(restored.get(key)==permissions[key] for key in fields)" in runtime,
    "runtime cert exercises the real group Gallery picker with a generated image fixture": "certify_group_gallery_image(group_id)" in runtime and "/sdcard/Pictures/FYNX_CI_GROUP_ATTACHMENT.png" in runtime and 'tap_control(group_ui,["Add attachment"]' in runtime and 'tap_control(menu,["Gallery"]' in runtime,
    "runtime group-send markers stay short enough for reliable ADB input while remaining per-run unique": 'keyboard_marker="CI_GROUP_KEYBOARD_"+uuid.uuid4().hex[:5]' in runtime and 'app_button_marker="CI_GROUP_BUTTON_"+uuid.uuid4().hex[:5]' in runtime,
    "runtime cert requires new server-persisted image ID/type and history persistence after reopening": "attachmentMediaId" in runtime and 'attachment_type!="image"' in runtime and "group-gallery-image-reopened.xml" in runtime,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("GROUP PERMISSIONS CONTRACT RED: " + "; ".join(failed))
print(f"GROUP PERMISSIONS CONTRACT GREEN ({len(checks)} checks)")
