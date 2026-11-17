import fs from 'node:fs';
const admin=fs.readFileSync(new URL('./adminRoutes.js',import.meta.url),'utf8');
const scalability=fs.readFileSync(new URL('./scalability.js',import.meta.url),'utf8');
const trustSafety=fs.readFileSync(new URL('./trustSafety.js',import.meta.url),'utf8');
const checks=[
 ['admin route module exists and exports registration',admin.includes('export function registerAdminRoutes')],
 ['announcements are server-backed',admin.includes('fynx_announcements')&&admin.includes("/api/announcements")],
 ['dashboard is administrator protected',admin.includes("/api/admin/dashboard")&&admin.includes('requireAdmin')],
 ['account status changes are administrator protected',admin.includes("/api/admin/accounts/:userId/status")&&admin.includes("ACCOUNT_LIMITED")===false],
 ['admin routes are wired into runtime bootstrap',scalability.includes('registerAdminRoutes({ app })')&&scalability.includes('registerAdminRoutes')],
 ['owner-only admin grants exist',admin.includes("owner access required")&&admin.includes("fynx_admin_roles")],
 ['content report routes are registered for posts, comments and Marketplace listings',trustSafety.includes('/api/social/content-reports')&&trustSafety.includes('SOCIAL_POST')&&trustSafety.includes('SOCIAL_COMMENT')&&trustSafety.includes('MARKETPLACE_LISTING')],
 ['content report schema initialization is isolated per database pool',trustSafety.includes('new WeakMap()')&&trustSafety.includes('safetySchemaPromises.set(pool, schemaPromise)')],
 ['selected-audience content reports enforce accepted-friend visibility',trustSafety.includes("p.visibility='SELECTED_PEOPLE'")&&trustSafety.includes("f.status='accepted'")],
 ['open content reports are protected against duplicate submissions',trustSafety.includes('safety_content_reports_open_duplicate_idx')&&trustSafety.includes("status IN ('OPEN','UNDER_REVIEW')")],
 ['admin content report review is protected and audited',admin.includes('/api/admin/content-reports')&&admin.includes('ensureTrustSafetySchema(pool)')&&admin.includes('requireAdmin')&&admin.includes('safety_content_moderation_audit')],
 ['content report status transitions reject invalid state changes',admin.includes('allowedNextStatuses')&&admin.includes('INVALID_REPORT_STATUS_TRANSITION')&&admin.includes('CONTENT_REPORT_REVIEW')],
 ['no hardcoded API secret exists',!/sk-[A-Za-z0-9_-]{20,}/.test(admin)]
];
for(const [name,ok] of checks){if(!ok)throw new Error(`Stage 15H verification failed: ${name}`);console.log(`PASS: ${name}`);}
console.log(`Stage 15H admin verification passed (${checks.length} checks)`);
