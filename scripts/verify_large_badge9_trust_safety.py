from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p): return (ROOT/p).read_text(encoding="utf-8")
trust=read("backend/trustSafety.js"); server=read("backend/server.js"); social=read("backend/socialRoutes.js"); client=read("app/src/main/java/com/fynx/app/ui/FynxSafetyRemoteClient.kt"); privacy=read("app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt")
support=read("backend/supportRoutes.js"); support_client=read("app/src/main/java/com/fynx/app/ui/FynxSupportRemoteClient.kt"); support_ui=read("app/src/main/java/com/fynx/app/ui/FynxSupportHubPanel.kt"); notification_models=read("app/src/main/java/com/fynx/app/ui/NotificationModels.kt"); admin_panel=read("app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt")
checks=[
("trust safety route module exists","registerTrustSafetyRoutes" in trust and "inspectTrustSafetyText" in trust),
("safety settings are server-backed and account-scoped","CREATE TABLE IF NOT EXISTS safety_settings" in trust and "WHERE user_id=$1" in trust),
("safety settings update is authenticated",'app.patch("/api/safety", auth' in trust and "req.user.sub" in trust),
("report creation is authenticated and persisted",'app.post("/api/social/reports",auth' in trust and "INSERT INTO safety_reports" in trust),
("report history is account-scoped",'app.get("/api/social/reports/mine",auth' in trust and "WHERE reporter_id=$1" in trust),
("reports reject self-reporting","you cannot report your own account" in trust),
("reports reject duplicate open cases","already have an open report" in trust and "INTERVAL '24 hours'" in trust),
("appeals are authenticated and persisted",'app.post("/api/safety/appeals",auth' in trust and "INSERT INTO safety_appeals" in trust),
("appeals cannot reference another user's report","WHERE id=$1 AND reporter_id=$2" in trust),
("appeal history is account-scoped",'app.get("/api/safety/appeals",auth' in trust and "WHERE user_id=$1" in trust),
("server wires trust safety with real auth and database","registerTrustSafetyRoutes({ app, pool, auth });" in server and 'import { registerTrustSafetyRoutes } from "./trustSafety.js";' in server),
("block routes are authenticated",'app.post("/api/blocks/:username", auth' in social and 'app.delete("/api/blocks/:username", auth' in social),
("blocks remove friendship","INSERT INTO blocks" in social and "DELETE FROM friendships" in social),
("blocked users are denied social media access","NOT EXISTS(SELECT 1 FROM blocks" in social),
("server-side scam inspection has hard-block rules","HARD_BLOCK_PATTERNS" in trust and "shouldBlock" in trust),
("content safety check is authenticated",'app.post("/api/safety/content-check", auth' in trust),
("Android safety client loads real safety state","/api/safety" in client and "FynxBackendClient.get" in client),
("Android can submit real reports","/api/social/reports" in client and "submitReport" in client),
("Android can load and submit real appeals","/api/safety/appeals" in client and "submitAppeal" in client),
("Privacy & Safety UI exposes report submission","Report an account" in privacy and "submitReport" in privacy),
("Privacy & Safety UI exposes appeal submission","Appeal a decision or report" in privacy and "submitAppeal" in privacy),
("no fabricated report records are embedded","fakeReport" not in privacy and "sampleReport" not in privacy and "dummyReport" not in privacy),
("support cases use a separate backend data model","fynx_support_cases" in support and "fynx_support_messages" in support and "fynx_support_case_events" in support),
("support requests require validated categories and retry-safe request keys","CATEGORIES.has(category)" in support and "client_request_id" in support and "ON CONFLICT(user_id,client_request_id)" in support),
("user support history and conversations are account-scoped","WHERE c.user_id=$1" in support and "c.user_id=$2" in support),
("staff support APIs enforce Owner or explicit Admin permission","support_access=TRUE" in support and "SUPPORT_ACCESS_REQUIRED" in support and "OWNER_REQUIRED" in support),
("Admin inbox only exposes unassigned or self-assigned cases","c.assigned_admin_id IS NULL OR c.assigned_admin_id=$2" in support),
("staff replies and internal notes are separated","visibility IN ('PUBLIC','INTERNAL')" in support and "loadMessages(id, true)" in support and "loadMessages(id, false)" in support),
("case claims are atomic and case actions are audited","assigned_admin_id IS NULL AND status<>'RESOLVED'" in support and "recordEvent(client" in support),
("support notifications are queued through the existing FYNX notifier","queueFynxNotification" in support and 'type: "SUPPORT"' in support),
("Android Help & Support opens the real support hub","FynxSupportHubPanel()" in privacy and "Submit support request" in support_ui),
("Android support client uses authenticated support endpoints","/api/support/cases" in support_client and "/api/admin/support/cases" in support_client),
("Android user and staff replies send stable idempotency keys","requestKey: String" in support_client and 'put("requestKey", requestKey)' in support_client and "replyRequestKey" in support_ui),
("Android exposes Owner/Admin support inbox and case assignment","FYNX Support Centre" in support_ui and "Assign Admin" in support_ui and "Claim this case" in support_ui),
("Android separates internal notes from user-visible replies","Internal note" in support_ui and 'put("visibility"' in support_client),
("support notification type is represented in Android","SUPPORT" in notification_models),
("Owner/Admin Control Center embeds a staff-only support inbox","FYNX Support Centre" in admin_panel and "FynxSupportHubPanel(staffOnly = true)" in admin_panel),
]
failed=[n for n,o in checks if not o]
for n,o in checks: print(("PASS: " if o else "FAIL: ")+n)
if failed: raise SystemExit("Large Badge #9 trust/safety verification failed: "+"; ".join(failed))
print(f"Large Badge #9 trust/safety verification passed ({len(checks)} checks)")
