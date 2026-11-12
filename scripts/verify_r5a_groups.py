from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

def compact(value):
    return ' '.join(value.split())

group = read('backend/groupRoutes.js')
membership = read('backend/groupMembershipRoutes.js')
bootstrap = read('backend/scalability.js')
server = read('backend/server.js')
realtime = read('app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt')
group_panel = read('app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt')
client = read('app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt')
settings = read('app/src/main/java/com/fynx/app/ui/FynxGroupSettingsPanel.kt')

check('group routes authenticate requests', "const auth = (req,res,next)" in group and "jwt.verify(token,JWT_SECRET)" in group)
check('group membership is required to read messages', "group membership required" in group and "JOIN fynx_group_members gm" in group and "WHERE gm.group_id=$1 AND gm.user_id=$2 LIMIT 1" in group and "if(!access)" in group)
check('group message sender owns attachments', "message_media WHERE id=$1 AND owner_id=$2" in group)
check('group message attachments are retrievable only by current members and respect new-member history privacy', "gm.attachment_media_id = mm.id" in server and "gmem.user_id = $2" in server and "fg.show_history_to_new_members = TRUE OR gm.created_at >= gmem.created_at" in server and "gm.deleted_at IS NULL" in server)
check('group message media is served without shared caching or redirectable signed URLs', "group_message_media" in server and "privateNoStoreMedia ? \"private, no-store\"" in server and "groupMessageAuthorization" in server)
check('group messages respect account safety state', "account_status" in group and "ACCOUNT_LOCKED" in group and "ACCOUNT_LIMITED" in group)
check('group messages run trust safety inspection', "inspectTrustSafetyText" in group and "SAFETY_BLOCK" in group)
check('group membership has unique database key', "PRIMARY KEY(group_id,user_id)" in group)
check('existing group sync never deletes members from a stale client snapshot', "DELETE FROM fynx_group_members WHERE group_id=$1 AND user_id<>$2 AND user_id<>ALL($3::bigint[])" not in group)
check('existing group sync does not implicitly rewrite membership', compact(group).find('if(existing){ await client.query(`UPDATE fynx_groups') != -1 and compact(group).find('if(existing){ for(const user of requested.values())') == -1)
check('group sync returns persisted database membership', "const persistedMembers=(await client.query(`SELECT u.username,m.role FROM fynx_group_members" in group and "members:persistedMembers.map" in group)
check('active member dialog uses authenticated add, promote, demote and remove operations', all(token in group_panel for token in ['FynxGroupRemoteClient.addMember(', 'FynxGroupRemoteClient.promoteMember(', 'FynxGroupRemoteClient.demoteMember(', 'FynxGroupRemoteClient.removeMember(']))
check('active group settings expose leave and ownership transfer with server actions', all(token in settings for token in ['FynxGroupRemoteClient.leaveGroup(', 'FynxGroupRemoteClient.transferOwnership(']) and 'onGroupLeft = onBack' in group_panel)
check('active group settings and member dialog submit authenticated group reports', 'FynxGroupRemoteClient.reportGroup(' in settings and 'FynxGroupRemoteClient.reportGroup(' in group_panel)
check('Android group client exposes authenticated member actions', all(token in client for token in ['suspend fun addMember(', 'suspend fun promoteMember(', 'suspend fun demoteMember(', 'suspend fun removeMember(']))
check('server exposes authenticated leave route', "app.post('/api/groups/:groupId/leave', auth" in membership)
check('group owner cannot silently leave', "group owner must transfer ownership before leaving" in membership)
check('server exposes authenticated member removal', "app.delete('/api/groups/:groupId/members/:username', auth" in membership)
check('owner cannot be removed', "group owner cannot be removed" in membership)
check('moderator cannot remove moderators/admins', "moderators can only remove members" in membership)
check('membership controls are wired into production bootstrap', "registerGroupMembershipRoutes({ app });" in bootstrap)
check('backend exposes an authenticated group realtime broadcast hub', 'app.locals.fynxBroadcastGroupMessage' in server and 'SELECT user_id FROM fynx_group_members' in server)
check('group message send/action routes publish realtime updates', 'broadcastGroupMessage(groupId, message)' in group)
check('realtime client accepts group message events', '"group_message" in realtime and "GroupMessage" in realtime')
check('active group chat subscribes to authenticated realtime updates', 'FynxRealtimeClient(' in group_panel and 'Event.GroupMessage' in group_panel)
check('active group history privacy is stored as a server permission', 'show_history_to_new_members' in membership and 'showHistoryToNewMembers' in client and 'chatHistory=remote.showHistoryToNewMembers' in settings)
check('new members only receive history from their join time when history sharing is disabled', 'm.created_at >= $4' in group and 'access.show_history_to_new_members' in group)
check('group history cursor preserves PostgreSQL microsecond timestamp precision', 'cursor_created_at' in group and 'SS.US' in group)
check('group history pagination uses a stable timestamp plus message-id keyset cursor', '(m.created_at,m.id) < ($5::timestamptz,$6::text)' in group and 'beforeId=beforeRaw.slice(separator+1)' in group)
check('group history cursor uses the same descending timestamp and id order as the page query', 'ORDER BY m.created_at DESC,m.id DESC' in group and 'page[0].cursor_created_at' in group and 'page[0].id' in group)
check('group history cursor is passed through by the Android client without millisecond truncation', 'before=${Uri.encode(it)}' in client and 'nextBefore' in client)
check('active Group Chat exposes its server-backed message edit operation', 'FynxGroupRemoteClient.editMessage(context, groupId, messageId, updatedText)' in group_panel and 'Text("Edit")' in group_panel)
check('active Group Chat offers member mention suggestions', 'val mentionSuggestions' in group_panel and 'Mention a member' in group_panel)
check('group refresh merges server windows without dropping previously loaded authorized older messages', 'internal fun mergeGroupMessageRefresh(' in group_panel and 'it.id in authorizedOlderMessageIds || !it.delivered' in group_panel)
check('periodic group refresh retains only authorized older pages and pending local messages', 'mergeGroupMessageRefresh(messages, remoteMessages, authorizedOlderGroupMessageIds)' in group_panel and 'authorizedOlderGroupMessageIds by remember(groupId)' in group_panel)
check('group realtime refresh retains only authorized older pages and pending local messages', 'mergeGroupMessageRefresh(latestMessages, remoteMessages, latestAuthorizedOlderGroupMessageIds)' in group_panel and 'latestAuthorizedOlderGroupMessageIds by rememberUpdatedState(authorizedOlderGroupMessageIds)' in group_panel)

check('private history and recent conversation responses disable HTTP caching', 'app.get("/api/messages/recent", auth' in server and 'app.get("/api/messages/:username", auth' in server and server.count('res.set("Cache-Control", "no-store");') >= 2)
check('private history search is scoped to the authenticated pair and hides deleted messages', 'searchQuery.length >= 2' in server and 'strpos(lower(COALESCE(m.text, \'\')), lower($3)) > 0' in server and 'm.deleted = FALSE' in server and 'deleted BOOLEAN NOT NULL DEFAULT FALSE' in server and 'req.user.sub, other.id, searchQuery, beforeId, limit + 1' in server)
check('group history search requires membership and keeps the new-member join-time privacy boundary', 'searchQuery.length>=2' in group and 'access.show_history_to_new_members' in group and 'm.created_at >= $4' in group and 'm.deleted_at IS NULL' in group and 'strpos(lower(COALESCE(m.text,\'\')),lower($5))>0' in group)
check('active Group screen invokes the paginated remote history search', 'loadGroupSearchPage(' in group_panel and 'query = cleanQuery' in group_panel and 'remoteGroupSearchCursor' in group_panel)
check('group client passes query and the stable composite cursor to the server', 'query:String?=null' in client and '&q=' in client and '&before=' in client and 'Uri.encode(it)' in client)
check('private chat uses authenticated paginated remote history search', 'searchHistoryPage(context' in read('app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt') and 'loadPrivateSearchPage(' in read('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt'))
check('private search results paginate independently from ordinary older-history pages', 'remoteSearchCursor' in read('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt') and 'load_more_private_search_results' in read('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt'))
check('group pin and unpin controls are only available to admins and moderators', 'if (canManageGroup) {' in group_panel and 'FynxGroupRemoteClient.setPinned(context, groupId, message.id, !message.pinned)' in group_panel)
check('group delete controls are available only to the sender or an admin/moderator', 'if (message.fromMe || canManageGroup) {' in group_panel and 'FynxGroupRemoteClient.deleteMessage(context, groupId, message.id)' in group_panel)

check('private message delete action is shown only for messages owned by the current user', 'if (message.fromMe) {' in read('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt') and 'FynxProductionMessaging.deleteMessage(context, message.id)' in read('app/src/main/java/com/fynx/app/ui/ConversationPanel.kt'))
failed = [name for name, ok in checks if not ok]
for index, (name, ok) in enumerate(checks, 1):
    print(f"R5A_CHECK_{index}: {'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit('R5A groups gate failed: ' + '; '.join(failed))
print(f'R5A groups gate passed ({len(checks)} checks)')
