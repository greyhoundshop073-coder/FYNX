from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
checks = []

def read(path):
    p = ROOT / path
    return p.read_text(encoding='utf-8') if p.is_file() else ''

def check(name, ok):
    checks.append((name, bool(ok)))

settlement = read('backend/marketplaceSettlement.js')
market_seller = read('app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt')
groups = read('backend/groupRoutes.js')
group_membership = read('backend/groupMembershipRoutes.js')
group_client = read('app/src/main/java/com/fynx/app/ui/FynxGroupsBatch3.kt')
calls = read('app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt')
trust = read('backend/trustSafety.js')
admin = read('app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt')
r6g_routes = read('backend/r6gIntegrationRoutes.js')
r6g_client = read('app/src/main/java/com/fynx/app/ui/FynxR6GIntegrationClient.kt')
scalability = read('backend/scalability.js')

# Marketplace/payment protection remains server-authoritative.
check('marketplace settlement keeps escrow/ledger authority on the backend', all(x in settlement for x in ['marketplace_escrows', 'marketplace_ledger_entries', 'marketplace_financial_operations']))
check('seller settlement UI uses the backend settlement API', '/api/marketplace/settlement/order/' in market_seller and '/api/marketplace/settlement/release/' in market_seller)
check('seller payout requires provider verification before paid state', 'provider transfer is verified' in market_seller and 'mark paid' in market_seller.lower())

# Groups/social remain authenticated, server-authoritative and safety-aware.
check('group routes verify JWT authentication', 'jwt.verify(token,JWT_SECRET)' in groups)
check('group membership gates message access', "if(!(await member(groupId,req.user.sub)))" in groups and 'group membership required' in groups)
check('group messages enforce attachment ownership', 'message_media WHERE id=$1 AND owner_id=$2' in groups)
check('group messages enforce account safety and trust inspection', all(x in groups for x in ['ACCOUNT_LOCKED', 'ACCOUNT_LIMITED', 'inspectTrustSafetyText', 'SAFETY_BLOCK']))
check('group membership lifecycle protects owner/moderator roles', all(x in group_membership for x in ['group owner must transfer ownership before leaving', 'group owner cannot be removed', 'moderators can only remove members']))
check('group membership lifecycle is production-wired', 'registerGroupMembershipRoutes({ app });' in scalability)
check('group client exposes leave/remove semantics', 'FynxGroupMemberAction.REMOVE' in group_client and 'fun leaveGroup' in group_client)

# Voice/video call surface keeps realtime and media lifecycle together.
check('calls request the correct runtime permissions', 'RequestMultiplePermissions' in calls and 'permissionLauncher.launch(required)' in calls)
check('calls use realtime invite/accept signaling', all(x in calls for x in ['sendCallInvite', 'sendCallAccept', 'realtimeClient.connect()']))
check('calls connect and clean up the media engine', 'mediaEngine.connect(current)' in calls and 'mediaEngine.disconnect()' in calls)
check('call failure/permission paths persist call status', 'FynxCallsStore.updateStatus' in calls)

# Trust & Safety remains reusable and backend-authoritative.
check('trust safety exposes authenticated content checks', 'registerTrustSafetyRoutes' in trust and 'app.post("/api/safety/content-check", auth' in trust)
check('trust safety blocks credential/payment scam patterns', all(x in trust for x in ['OTP', 'private key', 'gift card', 'HARD_BLOCK_PATTERNS']))
check('trust safety is applied to group messages', 'inspectTrustSafetyText' in groups)

# Admin/business controls remain server-backed rather than local-only.
check('admin client exposes server dashboard and account controls', all(x in admin for x in ['dashboard', 'admins', 'setAccountStatus', 'grantAdmin', 'revokeAdmin']))
check('admin client uses authenticated backend transport', 'FynxBackendClient' in admin and '/api/admin/' in admin)

# R6-G canonical cross-feature links remain wired without moving authority into clients.
check('R6-G backend routes are registered', 'export function registerR6GIntegrationRoutes' in r6g_routes and 'registerR6GIntegrationRoutes({ app });' in scalability)
check('R6-G schema preserves canonical listing/home/business/message links', all(x in r6g_routes for x in ['marketplace_listings', 'social_posts', 'messages']))
check('R6-G client uses the authenticated backend client', 'FynxBackendClient' in r6g_client and '/api/r6g/' in r6g_client)
check('R6-G group share uses the canonical group route', '/api/r6g/groups/' in r6g_client and 'marketplace-posts' in r6g_client)

client_sources = '\n'.join(str(p.read_text(encoding='utf-8')) for p in (ROOT / 'app/src/main/java/com/fynx/app/ui').glob('*.kt'))
check('this workstream adds no obvious client API secrets', not re.search(r'sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}', client_sources))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS: ' if ok else 'FAIL: ') + name)
if failed:
    raise SystemExit('FYNX this-chat workstream gate failed: ' + '; '.join(failed))
print(f'FYNX this-chat workstream gate passed ({len(checks)} checks)')
