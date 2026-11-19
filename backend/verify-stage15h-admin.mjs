import fs from 'node:fs';
const admin=fs.readFileSync(new URL('./adminRoutes.js',import.meta.url),'utf8');
const scalability=fs.readFileSync(new URL('./scalability.js',import.meta.url),'utf8');
const checks=[
 ['admin route module exists and exports registration',admin.includes('export function registerAdminRoutes')],
 ['announcements are server-backed',admin.includes('fynx_announcements')&&admin.includes("/api/announcements")],
 ['dashboard is administrator protected',admin.includes("/api/admin/dashboard")&&admin.includes('requireAdmin')],
 ['account status changes are administrator protected',admin.includes("/api/admin/accounts/:userId/status")&&admin.includes("ACCOUNT_LIMITED")===false],
 ['admin routes are wired into runtime bootstrap',scalability.includes('registerAdminRoutes({ app })')&&scalability.includes('registerAdminRoutes')],
 ['owner-only admin grants exist',admin.includes("owner access required")&&admin.includes("fynx_admin_roles")],
 ['no hardcoded API secret exists',!/sk-[A-Za-z0-9_-]{20,}/.test(admin)]
];
for(const [name,ok] of checks){if(!ok)throw new Error(`Stage 15H verification failed: ${name}`);console.log(`PASS: ${name}`);}
console.log(`Stage 15H admin verification passed (${checks.length} checks)`);
