import fs from 'node:fs';
import { spawnSync } from 'node:child_process';
const admin=fs.readFileSync(new URL('./adminRoutes.js',import.meta.url),'utf8');
const scalability=fs.readFileSync(new URL('./scalability.js',import.meta.url),'utf8');
const support=fs.readFileSync(new URL('./supportRoutes.js',import.meta.url),'utf8');
const checks=[
 ['admin route module exists and exports registration',admin.includes('export function registerAdminRoutes')],
 ['announcements are server-backed',admin.includes('fynx_announcements')&&admin.includes("/api/announcements")],
 ['dashboard is administrator protected',admin.includes("/api/admin/dashboard")&&admin.includes('requireAdmin')],
 ['account status changes are administrator protected',admin.includes("/api/admin/accounts/:userId/status")&&admin.includes("ACCOUNT_LIMITED")===false],
 ['admin routes are wired into runtime bootstrap',scalability.includes('registerAdminRoutes({ app })')&&scalability.includes('registerAdminRoutes')],
 ['owner-only admin grants exist',admin.includes("owner access required")&&admin.includes("fynx_admin_roles")],
 ['no hardcoded API secret exists',!/sk-[A-Za-z0-9_-]{20,}/.test(admin)]
];

checks.push(
 ['support routes are registered in runtime',scalability.includes('registerSupportRoutes({ app })')&&scalability.includes('./supportRoutes.js')],
 ['support requests persist with retry-safe request keys',support.includes('fynx_support_cases')&&support.includes('client_request_id')&&support.includes('ON CONFLICT(user_id,client_request_id)')],
 ['support messages are deduplicated on network retries',support.includes('client_message_id')&&support.includes('fynx_support_messages_sender_request_unique')&&support.includes('ON CONFLICT(sender_id,client_message_id)')],
 ['public replies and internal notes have separate visibility',support.includes("visibility IN ('PUBLIC','INTERNAL')")&&support.includes("visibility='PUBLIC'")],
 ['support access is enforced for Owner and enabled Admins',support.includes('support_access=TRUE')&&support.includes('SUPPORT_ACCESS_REQUIRED')&&support.includes('OWNER_REQUIRED')],
 ['new Admin accounts default to support access while Owner can revoke it',support.includes('ADD COLUMN IF NOT EXISTS support_access BOOLEAN NOT NULL DEFAULT TRUE')&&support.includes('ALTER COLUMN support_access SET DEFAULT TRUE')&&support.includes('ADMIN_ACCESS_REVOKED_AND_CASE_UNASSIGNED')],
 ['Admin inbox is scoped to unassigned and self-assigned cases',support.includes('c.assigned_admin_id IS NULL OR c.assigned_admin_id=$2')],
 ['case assignment and status changes create audit events',support.includes('STATUS_CHANGED')&&support.includes('ASSIGNED')&&support.includes('ADMIN_ACCESS_REVOKED_AND_CASE_UNASSIGNED')],
 ['support notifications use the FYNX notification service',support.includes('queueFynxNotification')&&support.includes('type: "SUPPORT"')],
 ['support API contains no hardcoded provider secret',!/sk-[A-Za-z0-9_-]{20,}/.test(support)]
);

for(const [name,ok] of checks){if(!ok)throw new Error(`Stage 15H verification failed: ${name}`);console.log(`PASS: ${name}`);}
console.log(`Stage 15H admin verification passed (${checks.length} checks)`);
const supportIntegration = spawnSync(process.execPath, ['test-support-routes.mjs'], { stdio: 'inherit', env: process.env });
if (supportIntegration.error) throw supportIntegration.error;
if (supportIntegration.status !== 0) throw new Error(`Support database integration test failed (exit ${supportIntegration.status}).`);

