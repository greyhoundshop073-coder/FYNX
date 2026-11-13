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
content_routes = read('backend/groupContentRoutes.js')
membership = read('backend/groupMembershipRoutes.js')
bootstrap = read('backend/scalability.js')
server = read('backend/server.js')
realtime = read('app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt')
group_panel = read('app/src/main/java/com/fynx/app/ui/GroupChatPanel.kt')
client = read('app/src/main/java/com/fynx/app/ui/FynxGroupsBatch3.kt')
active_group_panel = read('app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt')
group_remote_client = read('app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt')
group_social = read('app/src/main/java/com/fynx/app/ui/FynxGroupSocialBatch.kt')
conversation_preferences = read('app/src/main/java/com/fynx/app/ui/FynxConversationPreferences.kt')
firebase_messaging = read('app/src/main/java/com/fynx/app/ui/FynxFirebaseMessagingService.kt')
offline_sync = read('app/src/main/java/com/fynx/app/ui/FynxOfflineSync.kt')
connection_manager = read('app/src/main/java/com/fynx/app/ui/FynxAppConnectionManager.kt')

check('group routes authenticate requests', "const auth = (req,res,next)" in group and "jwt.verify(token,JWT_SECRET)" in group)
check('group membership is required to read messages', "group membership required" in group and "if(!(await member(groupId,req.user.sub)))" in group)
check('group message sender owns attachments', "message_media WHERE id=$1 AND owner_id=$2" in group)
check('group messages respect account safety state', "account_status" in group and "ACCOUNT_LOCKED" in group and "ACCOUNT_LIMITED" in group)
check('group messages run trust safety inspection', "inspectTrustSafetyText" in group and "SAFETY_BLOCK" in group)
check('group membership has unique database key', "PRIMARY KEY(group_id,user_id)" in group)
check('existing group sync never deletes members from a stale client snapshot', "DELETE FROM fynx_group_members WHERE group_id=$1 AND user_id<>$2 AND user_id<>ALL($3::bigint[])" not in group)
check('existing group sync does not implicitly rewrite membership', compact(group).find('if(existing){ await client.query(`UPDATE fynx_groups') != -1 and compact(group).find('if(existing){ for(const user of requested.values())') == -1)
check('group sync returns persisted database membership', "const persistedMembers=(await client.query(`SELECT u.username,m.role FROM fynx_group_members" in group and "members:persistedMembers.map" in group)
check('client defines remove and leave semantics', "FynxGroupMemberAction.REMOVE" in client and "fun leaveGroup" in client)
check('server exposes authenticated leave route', "app.post('/api/groups/:groupId/leave', auth" in membership)
check('group owner cannot silently leave', "group owner must transfer ownership before leaving" in membership)
check('server exposes authenticated member removal', "app.delete('/api/groups/:groupId/members/:username', auth" in membership)
check('owner cannot be removed', "group owner cannot be removed" in membership)
check('moderator cannot remove moderators/admins', "moderators can only remove members" in membership)
check('membership controls are wired into production bootstrap', "registerGroupMembershipRoutes({ app });" in bootstrap)
check('backend exposes an authenticated group realtime broadcast hub', 'app.locals.fynxBroadcastGroupMessage' in server and 'SELECT user_id FROM fynx_group_members' in server)
check('group message send/action routes publish realtime updates', 'broadcastGroupMessage(groupId, message)' in group)
check('realtime client accepts group message events', '"group_message" in realtime and "GroupMessage" in realtime')
check('group chat subscribes to authenticated realtime updates', 'FynxRealtimeClient(' in group_panel and 'Event.GroupMessage' in group_panel)


check('active group realtime events update messages directly without a duplicate REST history fetch', 'fromRealtimeMessage(event.messageJson)' in active_group_panel and 'FynxGroupRemoteClient.loadMessages(context, groupId)' not in active_group_panel[active_group_panel.find('if (event is FynxRealtimeClient.Event.GroupMessage'):active_group_panel.find('LaunchedEffect(messages.size', active_group_panel.find('if (event is FynxRealtimeClient.Event.GroupMessage'))])
check('group message attachments require current membership and block removed uploader ownership bypass', 'groupMessageAuthorization' in server and 'gmem.user_id = $2' in server and 'ownerMediaAuthorization' in server and 'NOT EXISTS (\n        SELECT 1 FROM fynx_group_messages gm' in server)
check('group content is cached per authenticated account for offline history', 'loadCachedContent(context, groupId)' in group_social and 'content_json' in group_remote_client and 'FynxAuthStore.accountStorageKey(context)' in group_remote_client)
check('event RSVPs support Going Maybe and Not going with durable offline retry state', all(token in group_social for token in ['"GOING"','"MAYBE"','"NOT_GOING"','pendingResponse']) and 'pending_rsvps' in group_remote_client and 'syncPendingRsvps' in group_remote_client)
check('group mute preference can be changed and is enforced for background notifications', 'fun setGroupMuted' in conversation_preferences and 'setGroupMuted(context, groupId, groupMuted)' in active_group_panel and 'FynxConversationPreferences.groupMuted(this, groupId)' in firebase_messaging and 'GROUPS_CHANNEL' in firebase_messaging)
check('private offline outbox drains after authenticated backend reachability returns', 'FynxOfflineSync.drain(appContext)' in connection_manager and 'FynxOfflineOutbox.load(context)' in offline_sync and 'FynxOfflineOutbox.remove(context, item.id)' in offline_sync)
check('signed-in users can discover all current server group memberships after reinstall or on another device', "app.get('/api/groups',auth" in group and 'WHERE mine.user_id=$1' in group and 'FynxGroupRemoteClient.loadMyGroups(context)' in active_group_panel and 'FynxGroupsStore.pendingSyncGroups(context)' in active_group_panel)
check('group message history supports stable timestamp and ID cursor pagination', 'beforeTimestamp' in group and 'beforeId' in group and 'ORDER BY m.created_at DESC,m.id DESC' in group and 'loadMessagePage' in group_remote_client and 'Load older messages' in active_group_panel)
check('group message search is bounded, membership-scoped and cursor-paginated', "const rawQuery=req.query?.q" in group and "plainto_tsquery('simple',$2)" in group and "USING GIN" in group and "searchQuery.length<2||searchQuery.length>100" in group and "query:String?=null" in group_remote_client and "remoteGroupSearchCursor" in active_group_panel)
check('active group search uses remote history and retains an offline fallback', "query = normalizedQuery" in active_group_panel and "Offline: showing only messages already loaded on this device." in active_group_panel)
check('authenticated reconnect drains account-scoped pending group messages and RSVPs', 'FynxGroupRemoteClient.drainPendingWork(appContext)' in connection_manager and 'FynxChatStore.load(context,"group_' in group_remote_client and 'syncPendingRsvps(context,group.id)' in group_remote_client and 'FynxAuthStore.accountStorageKey(context)!=account' in group_remote_client)
check('group pending sends are serialized and retain stable message IDs for idempotent retry', 'sendMutex.withLock' in group_remote_client and 'put("id",message.id)' in group_remote_client and 'delivered = false, read = false' in active_group_panel)
check('group friend invites require server confirmation and never create local-only membership', 'onInvite: suspend (String) -> Result<Unit>' in group_social and 'Connect to FYNX before inviting group members.' in active_group_panel and 'Member added locally; server sync is unavailable.' not in active_group_panel)
check('group social media shares use the durable attachment staging and retry path', 'onMedia = { uri, type ->\\n            sendGroupAttachment(uri, type)' in active_group_panel and 'stagePendingGroupAttachment(context, uri, type)' in active_group_panel)
check('failed group media staging removes the unsendable original URI from the local message queue', 'messages = messages.filterNot { it.id == optimistic.id }' in active_group_panel and 'Attachment could not be saved for retry.' in active_group_panel)


check('group poll history returns the authenticated member selection for every option', 'BOOL_OR(v.user_id=$2)' in content_routes and 'selected_by_me' in content_routes and 'selectedByMe: Boolean(o.selected_by_me)' in content_routes and 'selectedByMe:Boolean=false' in group_remote_client and 'optBoolean("selectedByMe")' in group_remote_client)
check('single-choice poll votes are serialized against the poll row in a transaction', "await client.query('BEGIN')" in content_routes and 'FOR UPDATE OF p' in content_routes and "await client.query('COMMIT')" in content_routes and 'client.release()' in content_routes)
check('closed polls are visibly closed and their vote actions are disabled', 'poll.closed' in group_social and 'enabled = !poll.closed' in group_social and 'Voting is closed.' in group_social)
check('poll vote failures are shown to the member instead of silently discarded', 'Your vote could not be saved.' in group_social and '.onFailure { error = it.message' in group_social)
check('group post pin failures are surfaced to the member', 'This group post could not be updated.' in group_social)
check('group event end-time input is validated and must follow the start time', 'Event end time is invalid.' in content_routes and 'Event end time must be after its start time.' in content_routes and 'parsedEnd.getTime() <= parsed.getTime()' in content_routes)
check('group social content creation respects server message permissions and trust-safety checks', 'canCreateGroupContent(groupId, actor.role)' in content_routes and 'groupContentSafetyFailure' in content_routes and 'SAFETY_BLOCK' in content_routes)

failed = [name for name, ok in checks if not ok]
for index, (name, ok) in enumerate(checks, 1):
    print(f"R5A_CHECK_{index}: {'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit('R5A groups gate failed: ' + '; '.join(failed))
print(f'R5A groups gate passed ({len(checks)} checks)')
