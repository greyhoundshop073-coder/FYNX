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
}
checks.update({
    "group message insert uses RETURNING to detect first delivery": "".join("ON CONFLICT(id) DO NOTHING RETURNING id,group_id,sender_id".split()) in normalized_group_routes,
    "duplicate group message IDs are checked against original group and sender": "String(row.group_id)!==groupId||String(row.sender_id)!==String(req.user.sub)" in normalized_group_routes,
    "conflicting group message ID returns HTTP 409": "MESSAGE_ID_CONFLICT" in group_routes and "res.status(409)" in normalized_group_routes,
    "duplicate group retry rebroadcasts persisted message for recovery": "".join("if(!inserted.rows.length){await broadcastGroupMessage(groupId,message);return res.status(200).json({message,duplicate:true})}".split()) in normalized_group_routes,
    "new and duplicate group deliveries both broadcast after message ownership validation": "".join("if(String(row.group_id)!==groupId||String(row.sender_id)!==String(req.user.sub))return res.status(409).json({error:'message id already belongs to another message',code:'MESSAGE_ID_CONFLICT'});const message=messageJson(row);if(!inserted.rows.length){await broadcastGroupMessage(groupId,message);return res.status(200).json({message,duplicate:true});}await broadcastGroupMessage(groupId,message)".split()) in normalized_group_routes,
    "new group message response marks first delivery": "res.status(201).json({message,duplicate:false})" in normalized_group_routes,
})
checks.update({
    "group send failures show friendly retry copy instead of raw HTTP details": "Message not sent yet. It is saved; tap Retry to try again." in active and "FYNX backend returned HTTP" not in active,
    "group attachment failures show friendly retry copy": "Attachment not sent yet. It is saved for retry; try again when the connection is ready." in active,
    "initial group sync failure uses friendly recovery copy": "Group details could not sync yet. Check your connection and retry." in active,
    "periodic group message sync failure uses friendly recovery copy": "Group messages could not sync yet. Your saved messages are still here; reconnect and retry." in active,
    "realtime group refresh failure uses friendly recovery copy": "New group messages could not refresh yet. Check your connection and try again." in active,
    "group permission load failure uses friendly recovery copy": "Group permissions could not load yet. Check your connection and retry." in active,
})
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("GROUP PERMISSIONS CONTRACT RED: " + "; ".join(failed))
print(f"GROUP PERMISSIONS CONTRACT GREEN ({len(checks)} checks)")
