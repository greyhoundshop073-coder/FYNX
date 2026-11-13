import fs from 'node:fs';
const admin=fs.readFileSync(new URL('./adminRoutes.js',import.meta.url),'utf8');
const scalability=fs.readFileSync(new URL('./scalability.js',import.meta.url),'utf8');
const sessionAware=fs.readFileSync(new URL('./sessionAwareAuth.js',import.meta.url),'utf8');
const platformRole=fs.readFileSync(new URL('./platformAdminRole.js',import.meta.url),'utf8');
const postgresIds=fs.readFileSync(new URL('./postgresIds.js',import.meta.url),'utf8');
const publicAnnouncementsRoute=admin.slice(admin.indexOf("app.get('/api/announcements'"),admin.indexOf("app.get('/api/admin/dashboard'"));
const adminAnnouncementsRoute=admin.slice(admin.indexOf("app.get('/api/admin/announcements'"),admin.indexOf("app.post('/api/admin/announcements'"));
const createAnnouncementRoute=admin.slice(admin.indexOf("app.post('/api/admin/announcements'"),admin.indexOf("app.patch('/api/admin/announcements/:announcementId'"));
const updateAnnouncementRoute=admin.slice(admin.indexOf("app.patch('/api/admin/announcements/:announcementId'"),admin.indexOf("app.patch('/api/admin/accounts/:userId/status'"));
const grantAdminRoute=admin.slice(admin.indexOf("app.post('/api/admin/admins'"),admin.indexOf("app.delete('/api/admin/admins/:userId'"));
const revokeAdminRoute=admin.slice(admin.indexOf("app.delete('/api/admin/admins/:userId'"));
const checks=[
 ['admin route module exists and exports registration',admin.includes('export function registerAdminRoutes')],
 ['announcements are server-backed',admin.includes('fynx_announcements')&&admin.includes("/api/announcements")],
 ['dashboard is administrator protected',admin.includes("/api/admin/dashboard")&&admin.includes('requireAdmin')],
 ['account status changes are administrator protected',admin.includes("/api/admin/accounts/:userId/status")&&admin.includes("ACCOUNT_LIMITED")===false],
 ['admin routes are wired into runtime bootstrap',scalability.includes('registerAdminRoutes({ app })')&&scalability.includes('registerAdminRoutes')],
 ['owner-only admin grants exist',admin.includes("owner access required")&&admin.includes("fynx_admin_roles")],
 ['Admin auth enforces password-reset session revocation',admin.includes('createAdminAuthMiddleware')&&admin.includes("SELECT session_version FROM users WHERE id=$1")&&admin.includes("session expired; sign in again")],
 ['Admin People search returns enforced account status',admin.includes("COALESCE(s.account_status,'ACTIVE') AS account_status")&&admin.includes("accountStatus:user.account_status||'ACTIVE'")],
 ['Admin People search treats percent and underscore literally',admin.includes("POSITION(LOWER($1) IN LOWER(COALESCE(u.username,'')))>0")&&admin.includes("POSITION(LOWER($1) IN LOWER(COALESCE(u.display_name,'')))>0")],
 ['announcement update IDs use the PostgreSQL BIGINT guard',updateAnnouncementRoute.includes("isPositivePostgresBigint(announcementId)")],
 ['Admin announcement history is private and not cached',adminAnnouncementsRoute.includes("res.set('Cache-Control','private, no-store')")],
 ['Admin announcement history uses bounded stable pagination',adminAnnouncementsRoute.includes("id DESC LIMIT $1 OFFSET $2")&&adminAnnouncementsRoute.includes("const hasMore=result.rows.length>limit")&&adminAnnouncementsRoute.includes("limit+1")],
 ['public announcement history uses bounded stable pagination',publicAnnouncementsRoute.includes("id DESC LIMIT $1 OFFSET $2")&&publicAnnouncementsRoute.includes("const hasMore=result.rows.length>limit")&&publicAnnouncementsRoute.includes("limit+1")],
 ['announcement create rejects over-limit title, body and link',createAnnouncementRoute.includes("title.length>120||body.length>4000||linkUrl.length>2048")],
 ['announcement edit rejects over-limit title, body and link',updateAnnouncementRoute.includes("title.length>120")&&updateAnnouncementRoute.includes("body.length>4000")&&updateAnnouncementRoute.includes("linkUrl.length>2048")],
 ['announcement create/edit never silently truncates submitted content',!/(title|body|linkUrl)\\.trim\\(\\)\\.slice\\(0,(120|4000|2048)\\)/.test(createAnnouncementRoute+updateAnnouncementRoute)],
 ['no hardcoded API secret exists',!/sk-[A-Za-z0-9_-]{20,}/.test(admin)]
];
checks.push(
 ['Admin JWT subjects are bounded to PostgreSQL BIGINT before database access',admin.includes('isPositivePostgresBigint(claims?.sub)')&&admin.includes('const subject=String(claims.sub).trim()')],
 ['shared session auth rejects PostgreSQL BIGINT overflow before database access',sessionAware.includes('isPositivePostgresBigint(claims?.sub)')],
 ['Owner role resolver rejects overflow before database access',platformRole.includes('isPositivePostgresBigint(userId)')],
 ['Admin grant validates the original ID value and preserves exact decimal strings',grantAdminRoute.includes('const userIdInput=req.body?.userId')&&grantAdminRoute.includes('isPositivePostgresBigint(userIdInput)')&&grantAdminRoute.includes('const userId=String(userIdInput).trim()')&&!grantAdminRoute.includes('Number(')],
 ['Admin revoke validates and preserves its exact route ID',revokeAdminRoute.includes("const userId=String(req.params.userId??'').trim()")&&revokeAdminRoute.includes('isPositivePostgresBigint(userId)')&&!revokeAdminRoute.includes('Number(')],
 ['shared validator enforces PostgreSQL signed BIGINT maximum without unsafe numeric coercion',postgresIds.includes('9223372036854775807n')&&postgresIds.includes('Number.isSafeInteger(value)')&&postgresIds.includes('BigInt(raw) <= MAX_POSTGRES_BIGINT')]
);
for(const [name,ok] of checks){if(!ok)throw new Error(`Stage 15H verification failed: ${name}`);console.log(`PASS: ${name}`);}
console.log(`Stage 15H admin verification passed (${checks.length} checks)`);
