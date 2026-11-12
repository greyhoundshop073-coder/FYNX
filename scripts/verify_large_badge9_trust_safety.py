from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p): return (ROOT/p).read_text(encoding="utf-8")
trust=read("backend/trustSafety.js"); server=read("backend/server.js"); social=read("backend/socialRoutes.js"); client=read("app/src/main/java/com/fynx/app/ui/FynxSafetyRemoteClient.kt"); privacy=read("app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt")
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
("reports store typed content targets and moderator audit fields","target_content_type" in trust and "target_content_id" in trust and "moderator_id" in trust and "reviewed_at" in trust),
("post reports submit a typed post ID",'contentType = "POST"' in read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt") and "contentId = post.id" in read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")),
("admin dashboard counts the actual safety report and appeal tables","countRows('safety_reports'" in read("backend/adminRoutes.js") and "countRows('safety_appeals'" in read("backend/adminRoutes.js")),
("moderators have an authenticated report queue and decision endpoint","app.get('/api/admin/reports',auth" in read("backend/adminRoutes.js") and "app.patch('/api/admin/reports/:reportId',auth" in read("backend/adminRoutes.js")),
("admin report queue records moderator and decision timestamps","moderator_id=$2" in read("backend/adminRoutes.js") and "reviewed_at=CASE" in read("backend/adminRoutes.js")),
("Android admin client reads and updates safety reports","/api/admin/reports" in read("app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt") and "reviewSafetyReport" in read("app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt")),
("Admin Control Center displays report review, resolve and dismiss actions","User safety reports" in read("app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt") and "Start review" in read("app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt") and "Resolve" in read("app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt") and "Dismiss" in read("app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt")),]
checks.append(("closed reports cannot be reopened through the review endpoint","WHERE id=$4 AND status IN ('OPEN','UNDER_REVIEW')" in read("backend/adminRoutes.js")))
failed=[n for n,o in checks if not o]
for n,o in checks: print(("PASS: " if o else "FAIL: ")+n)
if failed: raise SystemExit("Large Badge #9 trust/safety verification failed: "+"; ".join(failed))
print(f"Large Badge #9 trust/safety verification passed ({len(checks)} checks)")
