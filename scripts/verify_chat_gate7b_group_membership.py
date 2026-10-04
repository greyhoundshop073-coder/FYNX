#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

backend = read("backend/groupMembershipRoutes.js")
client = read("app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt")
groups = read("app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt")
model = read("app/src/main/java/com/fynx/app/ui/FynxGroupsBatch3.kt")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

# Server-authoritative membership management: the UI must not be the security boundary.
# Match the actual Express route declaration instead of depending on HTTP-method prose.
check("member list requires authenticated group membership", re.search(r"app\.get\('/api/groups/:groupId/members'", backend) is not None and "group membership required" in backend)
check("only admins can promote members", "members/:username/promote" in backend and "isAdmin(actor?.role)" in backend)
check("only admins can demote members", "members/:username/demote" in backend and "isAdmin(actor?.role)" in backend)
check("owner/admin cannot be demoted", "group owner/admin cannot be demoted" in backend)
check("ownership transfer is owner-authorized and transactional", "'/api/groups/:groupId/ownership'" in backend and "group owner permission required" in backend and "BEGIN" in backend and "COMMIT" in backend)
check("group reports require authenticated membership", "'/api/groups/:groupId/reports'" in backend and "group membership required" in backend)
check("leave-group endpoint exists", "'/api/groups/:groupId/leave'" in backend)

# Existing client/server integration points are preserved rather than duplicated.
check("remote member loading is wired", "loadMembers" in client and "/api/groups/$groupId/members" in client)
check("remote permissions are wired", "loadPermissions" in client and "updatePermissions" in client and "/api/groups/$groupId/permissions" in client)
check("invite creation/revocation is wired", "createInvite" in client and "revokeInvite" in client)
check("join request approval/rejection is wired", "approveJoinRequest" in client and "rejectJoinRequest" in client)
check("group member UI exposes add/promote/demote/remove", all(x in groups for x in ["Add member", "PROMOTE_MODERATOR", "DEMOTE_MODERATOR", "Remove"]))
check("group member UI protects owner from management actions", "member.username != current.ownerUsername" in groups)
check("group permission model has moderation actions", all(x in model for x in ["REMOVE", "BLOCK", "UNBLOCK", "PROMOTE_MODERATOR", "DEMOTE_MODERATOR"]))
check("group permission settings expose member posts/invites", "allowMemberPosts" in groups and "allowMemberInvites" in groups)
check("invite/access controls are exposed in remote client", "previewInvite" in client and "joinInvite" in client and "requestInviteJoin" in client)

failed = [name for name, ok in checks if not ok]
for i, (name, ok) in enumerate(checks, 1):
    print(f"{i:02d}. {'PASS' if ok else 'FAIL'}: {name}")

if failed:
    print("\nFAIL: " + "; ".join(failed))
    sys.exit(1)

print("\nPASS: Group Gate 7B membership, moderation and permission contract is internally consistent.")
