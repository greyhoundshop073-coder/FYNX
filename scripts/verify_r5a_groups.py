from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

group = read('backend/groupRoutes.js')
membership = read('backend/groupMembershipRoutes.js')
bootstrap = read('backend/scalability.js')
client = read('app/src/main/java/com/fynx/app/ui/FynxGroupsBatch3.kt')

check('group routes authenticate requests', "const auth = (req,res,next)" in group and "jwt.verify(token,JWT_SECRET)" in group)
check('group membership is required to read messages', "group membership required" in group and "if(!(await member(groupId,req.user.sub)))" in group)
check('group message sender owns attachments', "message_media WHERE id=$1 AND owner_id=$2" in group)
check('group messages respect account safety state', "account_status" in group and "ACCOUNT_LOCKED" in group and "ACCOUNT_LIMITED" in group)
check('group messages run trust safety inspection', "inspectTrustSafetyText" in group and "SAFETY_BLOCK" in group)
check('group membership has unique database key', "PRIMARY KEY(group_id,user_id)" in group)
check('existing group sync never deletes members from a stale client snapshot', "DELETE FROM fynx_group_members WHERE group_id=$1 AND user_id<>$2 AND user_id<>ALL($3::bigint[])" not in group)
check('existing group sync does not implicitly rewrite membership', "if(existing){\n        await client.query(`UPDATE fynx_groups" in group and "if(existing){\n        for(const user of requested.values())" not in group)
check('group sync returns persisted database membership', "const persistedMembers=(await client.query(`SELECT u.username,m.role FROM fynx_group_members" in group and "members:persistedMembers.map" in group)
check('client defines remove and leave semantics', "FynxGroupMemberAction.REMOVE" in client and "fun leaveGroup" in client)
check('server exposes authenticated leave route', "app.post('/api/groups/:groupId/leave', auth" in membership)
check('group owner cannot silently leave', "group owner must transfer ownership before leaving" in membership)
check('server exposes authenticated member removal', "app.delete('/api/groups/:groupId/members/:username', auth" in membership)
check('owner cannot be removed', "group owner cannot be removed" in membership)
check('moderator cannot remove moderators/admins', "moderators can only remove members" in membership)
check('membership controls are wired into production bootstrap', "registerGroupMembershipRoutes({ app });" in bootstrap)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS: ' if ok else 'FAIL: ') + name)
if failed:
    raise SystemExit('R5A groups gate failed: ' + '; '.join(failed))
print(f'R5A groups gate passed ({len(checks)} checks)')
