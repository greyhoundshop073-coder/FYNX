from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
group_routes = (ROOT / "backend/groupRoutes.js").read_text(encoding="utf-8")
membership_routes = (ROOT / "backend/groupMembershipRoutes.js").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt").read_text(encoding="utf-8")
active = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt").read_text(encoding="utf-8")
settings = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupSettingsPanel.kt").read_text(encoding="utf-8")
scalability = (ROOT / "backend/scalability.js").read_text(encoding="utf-8")
server = (ROOT / "backend/server.js").read_text(encoding="utf-8")
chat_list = (ROOT / "app/src/main/java/com/fynx/app/ui/ChatsPanel.kt").read_text(encoding="utf-8")
chat_messaging = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt").read_text(encoding="utf-8")
chat_store = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxChatStore.kt").read_text(encoding="utf-8")

normalized_group_routes = "".join(group_routes.split())
normalized_settings = "".join(settings.split())
normalized_active = "".join(active.split())

checks = {
    "server blocks text sends when group send_messages is disabled": "GROUP_SEND_MESSAGES_DISABLED" in group_routes and "if(!canManageMembers(actor.role)&&text&&!groupSettings.send_messages)" in normalized_group_routes,
    "server blocks attachments when group send_media is disabled": "GROUP_SEND_MEDIA_DISABLED" in group_routes and "!groupSettings.send_media" in group_routes,
    "server grants permission overrides only to group managers": "!canManageMembers(actor.role)&&text&&!groupSettings.send_messages" in group_routes and "!canManageMembers(actor.role)&&attachmentMediaId!==null&&!groupSettings.send_media" in group_routes,
    "server has a dedicated add-member endpoint": "app.post('/api/groups/:groupId/members'" in membership_routes and "GROUP_ADD_MEMBERS_DISABLED" in membership_routes,
    "non-manager member adds respect the server add_members setting": "!canManageMembers(actor.role) && !settings?.add_members" in membership_routes,
    "direct member adds cannot bypass admin approval": "GROUP_APPROVAL_REQUIRED" in membership_routes and "settings?.approve_new_members" in membership_routes,
    "active group UI respects the approval-required setting": "remotePermissions?.approveNewMembers != true" in active,
    "pending join-request UI is shown only after the server advertises approval support": "if(isAdmin&&permissionsLoaded&&FynxBackendClient.hasAccessToken(context)&&approveNewMembers&&joinRequestApprovalSupported)" in normalized_settings,
    "invite-link creation and revocation require a loaded server permission snapshot and explicit invite support": "if(isAdmin&&permissionsLoaded&&FynxBackendClient.hasAccessToken(context)&&inviteLinks)" in normalized_settings and "GroupSwitchRow(\"Invitelinks\",inviteLinks,canEditPermissions&&(inviteLinks||invitePreviewSupported))" in normalized_settings and "FynxGroupRemoteClient.createInvite(context,groupId)" in normalized_settings and "FynxGroupRemoteClient.revokeInvite(context,groupId,token)" in normalized_settings and "Group invite links are unavailable until this server supports invite preview" in settings,
    "Android client uses the dedicated member-add endpoint": '"/api/groups/$groupId/members"' in client and "suspend fun addMember" in client,
    "active group chat loads and uses remote send/media/member permissions": "FynxGroupRemoteClient.loadPermissions(context, groupId)" in active and "remotePermissions?.sendMessages" in active and "remotePermissions?.sendMedia" in active and "remotePermissions?.addMembers" in active,
    "authenticated group actions fail closed until server permissions load": "val hasRemoteAccessToken = FynxBackendClient.hasAccessToken(context)" in active and "if (hasRemoteAccessToken) remotePermissions?.sendMessages == true" in active and "if (hasRemoteAccessToken) remotePermissions?.sendMedia == true" in active and "remotePermissions?.addMembers == true" in active and "permissionLoadError" in active,
    "active group member action no longer uses manager-only full-group sync": "FynxGroupRemoteClient.addMember(context, groupId, cleanUsername)" in active and "onInvite = { username ->" in active,
    "permission toggles preserve existing disappearing_seconds value": "disappearingSeconds=remote.disappearingSeconds" in normalized_settings and "approveNewMembers,disappearingSeconds" in normalized_settings,
    "new-member history visibility is disabled until the server advertises support": "canEditPermissions&&showHistoryPolicySupported" in normalized_settings and "This server does not yet support saving and enforcing new-member history visibility" in settings,
    "group media saving uses the local gallery preference for received media only": normalized_active.count("autoSaveToGallery=FynxConversationPreferences.groupSaveMedia(context,groupId)&&!message.fromMe") >= 2,
    "story-to-group is not advertised as a text-only message until a real Status share flow exists": "onStoryShare" not in active and "Share a story to group" not in active and "Story shared to group" not in active and "onStoryShare" not in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupSocialBatch.kt").read_text(encoding="utf-8"),
    "group message sync does not clear a local persistence error immediately after setting it": active.find("if (showError) syncMessage = null") < active.find('FynxChatStore.save(context, "group_$groupId", mergedMessages)') and "Your group messages could not be saved locally. Please try again." in active,
    "production startup registers group, membership and content routes exactly once": all(token in scalability for token in ["registerGroupRoutes({ app })", "registerGroupMembershipRoutes({ app })", "registerGroupContentRoutes({ app })"]) and "registerGroupInviteRoutes" not in scalability,
    "server exposes authenticated recent conversations and bounded private-history paging": all(token in server for token in ['app.get("/api/messages/recent"', "hasPageContract", "nextBeforeId"]),
    "Chat list hydrates from the remote conversation endpoint while preserving local pending messages": "FynxProductionMessaging.recentConversations(" in chat_list and "FynxChatStore.savePreviews(context, merged, expectedAccountUsername)" in chat_list and "pendingLocal" in chat_list,
    "remote conversation and history APIs verify account-session continuity": all(token in chat_messaging for token in ["suspend fun recentConversations", "suspend fun historyPage", "suspend fun searchHistoryPage", "isMessageRequestSessionCurrent"]),
    "remote preview snapshot persistence does not emit recursive refresh events": "fun savePreviews(context: Context" in chat_store and "previewUpdates.tryEmit" not in chat_store[chat_store.index("fun savePreviews(context: Context"):chat_store.index("fun savePreview(context: Context")],
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("GROUP PERMISSIONS CONTRACT RED: " + "; ".join(failed))
print(f"GROUP PERMISSIONS CONTRACT GREEN ({len(checks)} checks)")
