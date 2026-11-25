#!/usr/bin/env python3
"""Regression contract for the Owner/Admin Control Center safety and navigation UI."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt"
CLIENT = ROOT / "app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt"
BACKEND = ROOT / "backend/adminRoutes.js"
MEDIA_SERVER = ROOT / "backend/server.js"
ADVERTISING = ROOT / "backend/marketplaceAdvertising.js"
SOCIAL = ROOT / "backend/socialRoutes.js"
GROUPS = ROOT / "backend/groupRoutes.js"
MARKETPLACE_CHECKOUT = ROOT / "backend/marketplaceCheckout.js"
LOCAL_CAMPAIGN_STORE = ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceLocalCampaignStore.kt"
LOCAL_CAMPAIGN_CLIENT = ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionClient.kt"
LOCAL_CAMPAIGN_ADMIN = ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt"
MARKETPLACE_BANNER = ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePromotionalBanner.kt"
MARKETPLACE_PANEL = ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt"
SAMPLE_BANNER_ARTWORK = ROOT / "app/src/main/res/drawable-nodpi/fynx_marketplace_showcase.webp"
if not SOURCE.is_file():
    sys.exit(f"Missing Admin Control Center source: {SOURCE}")
source = SOURCE.read_text(encoding="utf-8")
client = CLIENT.read_text(encoding="utf-8") if CLIENT.is_file() else ""
backend = BACKEND.read_text(encoding="utf-8") if BACKEND.is_file() else ""
media_server = MEDIA_SERVER.read_text(encoding="utf-8") if MEDIA_SERVER.is_file() else ""
advertising = ADVERTISING.read_text(encoding="utf-8") if ADVERTISING.is_file() else ""
social = SOCIAL.read_text(encoding="utf-8") if SOCIAL.is_file() else ""
groups = GROUPS.read_text(encoding="utf-8") if GROUPS.is_file() else ""
marketplace_checkout = MARKETPLACE_CHECKOUT.read_text(encoding="utf-8") if MARKETPLACE_CHECKOUT.is_file() else ""

def require(condition: bool, message: str) -> None:
    if not condition:
        sys.exit(f"ADMIN_CONTROL_CENTER_CONTRACT=FAIL: {message}")
require('if (activeSection == "Music Library" && d.role == "OWNER")' in source and
        'add("Music Library")' in source,
        "the Music Library must have a dedicated Owner-only Admin section")
require('if (activeSection == "Marketplace")' in source and
        "Marketplace banner campaigns" in source and
        "FynxMarketplaceSeasonalPromotionAdminPanel()" in source,
        "Marketplace campaign manager must be reachable from the Marketplace section")
visual_test = ROOT / "app/src/androidTest/java/com/fynx/app/ui/FynxAdminPublishingSectionsVisualTest.kt"
require(visual_test.is_file(), "Admin publishing section emulator screenshot tests are missing")
visual = visual_test.read_text(encoding="utf-8")
for screenshot in ("FYNX-admin-owner-music-library.png", "FYNX-admin-owner-marketplace-campaigns.png", "FYNX-admin-admin-marketplace-campaigns.png", "FYNX-marketplace-offline-banner.png"):
    require(screenshot in visual, f"Admin visual evidence test is missing {screenshot}")
require('"GET"' in visual and "requestExecutorForTests" in visual,
        "Admin screenshot fixtures must remain GET-only and must not perform publishing mutations")
require('check(compose.onAllNodesWithText("Music Library").fetchSemanticsNodes().isEmpty())' in visual,
        "Admin emulator fixture must assert that the Owner-only Music Library tab is hidden from ADMIN")

require("fun FynxAdminControlCenterPanel()" in source, "control center composable is missing")
require("FynxAdminClient.dashboard(context)" in source, "role must be fetched from the server")
require("ensureTrustSafetySchema(routePool)" in backend and "openReports:counts[1]+counts[2]" in backend,
        "Overview open-report total must combine the account-report and content-moderation tables")
require("countRows('fynx_admin_activity',\"action IN ('REPORT_REVIEWED','CONTENT_REPORT_REVIEW','APPEAL_DECIDED','ACCOUNT_STATUS_CHANGED') AND created_at >= NOW()-INTERVAL '24 hours'\")" in backend and
        "safetyActions24h:counts[4]" in backend and "safetyActions24h" in client and "counts.optInt(\"safetyActions24h\")" in client,
        "24-hour safety dashboard metric must count real recorded administrative safety decisions")
require('d.role == "OWNER" || d.role == "ADMIN"' in source, "owner/admin dashboard gate is missing")
require('if (d.role == "OWNER")' in source, "owner-only admin management gate is missing")
require("fun requestAdminConfirmation(" in source, "shared confirmation helper is missing")
require("if (confirmationAction != null)" in source and "AlertDialog(" in source,
        "confirmation dialog is not rendered")
required_sections = ("Overview", "Safety", "Marketplace", "People", "Announcements", "Activity")
require(all(f'"{section}"' in source for section in required_sections),
        "one or more required Admin sections are missing")
require("listOf(" in source and "FilterChip(" in source,
        "Admin sections are not exposed through the section selector")

for section in ("Safety", "Marketplace", "People", "Announcements"):
    require(f'activeSection == "{section}"' in source,
            f"{section} content is not isolated behind its section selection")

sensitive_actions = {
    "appeal review": r"FynxAdminClient\.reviewAppeal\(context,",
    "Marketplace case resolution": r"FynxAdminClient\.resolveMarketplaceProtectionCase\(context,",
    "driver approval": r"FynxAdminClient\.setMarketplaceDriverApproval\(context,",
    "admin revocation": r"FynxAdminClient\.revokeAdmin\(context,",
    "account status change": r"FynxAdminClient\.setAccountStatus\(context,",
    "admin grant": r"FynxAdminClient\.grantAdmin\(context,",
    "announcement publishing": r"FynxAdminClient\.publishAnnouncement\(\s*context,",
}
for label, pattern in sensitive_actions.items():
    require(re.search(pattern, source) is not None, f"server action for {label} is missing")

confirmations = source.count("requestAdminConfirmation(") - 1  # exclude helper declaration
require(confirmations >= 10,
        f"expected confirmation prompts for sensitive actions; found {confirmations}")

# Guard against accidental local privilege assignment replacing server-authoritative access.
require(not re.search(r"dashboard\s*=\s*FynxAdminClient\.Dashboard\(", source),
        "dashboard role must not be constructed locally")
require("confirmationAction = null" in source,
        "confirmation must be cleared when dismissed or accepted")
require("fun FynxAnnouncementsPanel(ownerAccess: Boolean = false)" in source,
        "existing owner announcement route parameter must remain compatible")
require('if (activeSection == "Music Library" && d.role == "OWNER")' in source,
        "music administration must be visible only to the verified owner in its dedicated section")
require('"/api/admin/accounts/${encode(userId)}/status"' in client,
        "account status client must call the backend status route")
require("app.patch('/api/admin/accounts/:userId/status'" in backend,
        "server account status route is missing")


require('"/api/admin/activity?q=' in client, "searchable activity client is missing")
require("app.get('/api/admin/activity'" in backend, "searchable activity endpoint is missing")
require("CREATE TABLE IF NOT EXISTS fynx_admin_activity" in backend, "admin activity audit table is missing")
require('activeSection == "Activity"' in source, "activity history section is missing")
require("FynxAdminClient.activity(context, activityQuery, activityOffset, activityAction)" in source and "activityActionMenuExpanded" in source,
        "activity history is not connected to the backend with action filters")
require('"/api/admin/activity?q=${encode(query)}&action=${encode(action)}&limit=50&offset=' in client and
        "const action=typeof req.query?.action==='string'?req.query.action.trim().slice(0,80):''" in backend and
        "AND ($2='' OR a.action=$2)" in backend and
        "POSITION(LOWER($1) IN LOWER(COALESCE(a.summary,'')))" in backend and
        "ILIKE '%'||$1||'%'" not in backend,
        "activity action filtering must be URL-encoded and parameterized on the server")
require("activityHasMore" in source and "Text(\"Previous\")" in source and "Text(\"Next\")" in source,
        "activity history is missing bounded previous/next pagination")
require("OFFSET $4" in backend and "limit+1" in backend and "Math.min(10000" in backend,
        "activity history API must use bounded database pagination")
activity_route = backend[backend.index("app.get('/api/admin/activity'"):backend.index("app.get('/api/admin/revenue'")]
require("POSITION(LOWER($1) IN LOWER(COALESCE(u.display_name,'')))>0" in activity_route,
        "activity search must include actor display names as promised by the UI")
require("res.set('Cache-Control','private, no-store')" in activity_route and "a.metadata" not in activity_route and "metadata:a.metadata" not in activity_route,
        "activity audit responses must be private/non-cacheable and must not expose unused raw metadata")
activity_db_test = (ROOT / "backend/test-admin-announcements-db.mjs").read_text(encoding="utf-8")
require('q: "Admin Two"' in activity_db_test and 'Object.hasOwn(activityByDisplayName.body.activities[0], "metadata")' in activity_db_test and 'activitySecondPage' in activity_db_test,
        "activity history needs PostgreSQL coverage for display-name search, metadata minimization and stable paging")
require("REPORT_REVIEWED" in backend and "APPEAL_DECIDED" in backend, "safety review actions are not audited")
require("if(actorRole!=='OWNER')return res.status(403)" in backend and "req.fynxAdminRole=actorRole" in backend,
        "admin roster and role changes must verify and record the true OWNER actor")
require("recordAdminActivity(req,'ADMIN_GRANTED'" in backend and "recordAdminActivity(req,'ADMIN_REVOKED'" in backend and
        "RETURNING user_id" in backend and "await client.query('COMMIT')" in backend,
        "admin grant/revoke and their audit records must be committed atomically")
require("owner already has owner access" in backend and "u.id<>(SELECT owner_user_id FROM fynx_platform_owner WHERE singleton=TRUE)" in backend and "CREATE TABLE IF NOT EXISTS fynx_platform_owner" in backend,
        "OWNER must not receive a duplicate ADMIN grant or appear in the admin roster")

# Report and appeal data must be fetched from the protected APIs and rendered as actionable queues.
require('"/api/admin/reports?limit=' in client and "FynxAdminClient.reports(context, reportsOffset)" in source,
        "reported-account queue is not connected end to end")
require("reports.forEach" in source and "FynxAdminClient.reviewReport(context, report.id" in source,
        "reported-account review actions are not rendered")
require("FynxAdminClient.appeals(context, appealsOffset)" in source and "FynxAdminClient.reviewAppeal(context, appeal.id" in source and
        "decision.restored" in source and "appeal.reportId == null" in source and
        "optBoolean(\"restored\", false)" in client,
        "appeal review actions must report actual restoration and warn when no report is linked")
require("FynxAdminClient.reports(context, reportsOffset)" in source and "reportsHasMore" in source and
        "appealsHasMore" in source and "Text(\"Next\")" in source,
        "report and appeal review queues must provide Previous/Next pagination")
require("app.get('/api/admin/reports'" in backend and "app.get('/api/admin/appeals'" in backend and
        backend.count("LIMIT $1 OFFSET $2") >= 2 and backend.count("limit+1") >= 2 and
        backend.count("Math.min(10000" ) >= 2,
        "report and appeal APIs must enforce bounded database pagination")
require('Text("Reported accounts"' in source and 'Text("Content reports"' in source and
        "Review account reports and content reports in separate queues" in source and
        "FynxAdminClient.contentReports(context)" in source and
        "FynxAdminClient.reviewContentReport(context" in source,
        "account reports and content reports must remain in distinct, separately actionable moderation queues")

# The audit event must commit with the safety state change, not after a response-worthy mutation.
require("executor = routePool" in backend, "audit helper cannot use a transaction-scoped executor")
require("recordAdminActivity(req,'REPORT_REVIEWED'" in backend and "recordAdminActivity(req,'APPEAL_DECIDED'" in backend,
        "report and appeal decisions must be audited")
require("recordAdminActivity(req,'ACCOUNT_STATUS_CHANGED'" in backend,
        "account status changes must be audited")
require("SELECT id FROM users WHERE id=$1 FOR UPDATE" in backend and "if(!target){await client.query('ROLLBACK');return res.status(404)" in backend,
        "account status changes must lock and validate the target user before writing safety state")
require('FynxAdminClient.setAccountStatus(context, user.id, "LIMITED")' in source and
        "'LIMITED'" in backend,
        "People section must expose a server-supported limited-account action")
require("String(safety.account_status)==='LIMITED'" in groups and
        "String(safety.account_status) === 'LIMITED'" in marketplace_checkout,
        "LIMITED must enforce restrictions in group messaging and Marketplace checkout")
require("FOR UPDATE" in backend and "appeal has already received a final decision" in backend,
        "appeal decisions must lock the appeal and reject final-state rewrites")
require("pg_advisory_xact_lock(hashtext('fynx_safety_appeals_report_fk'))" in backend and "pg_advisory_xact_lock(hashtext('fynx_safety_appeals_report_fk'))" in (ROOT / "backend/trustSafety.js").read_text(encoding="utf-8") and
        "DO $fynx$ BEGIN" in backend and "END $fynx$;" in backend and
        "DO $fynx$ BEGIN" in (ROOT / "backend/trustSafety.js").read_text(encoding="utf-8") and
        "END $fynx$;" in (ROOT / "backend/trustSafety.js").read_text(encoding="utf-8") and
        "to_regclass('safety_appeals')" in backend and "to_regclass('safety_appeals')" in (ROOT / "backend/trustSafety.js").read_text(encoding="utf-8") and
        "safety_appeals_report_id_fkey" in backend and "safety_appeals_report_id_fkey" in (ROOT / "backend/trustSafety.js").read_text(encoding="utf-8") and
        "FOREIGN KEY(report_id) REFERENCES safety_reports(id) ON DELETE SET NULL" in backend and
        "NOT EXISTS(SELECT 1 FROM safety_reports r WHERE r.id=a.report_id)" in backend,
        "appeal report links must enforce a consistent nullable foreign key in either schema initialization order")
require("target_user_id=$2" in (ROOT / "backend/trustSafety.js").read_text(encoding="utf-8") and
        "appeal.report_id!=null" in backend and "String(linkedReport.target_user_id)===String(appeal.user_id)" in backend and
        "restored=false" in backend,
        "appeals must belong to the restricted target account and may only restore that account for its linked report")
require("SELECT id FROM users WHERE id=$1 FOR UPDATE" in backend and "Serialize appeal restoration with manual account-status changes" in backend,
        "approved appeal restoration must serialize on the user row with manual account-status changes")

# Announcement publishing reuses authenticated FYNX media, validates links and persists schedules.
for column in (
    "ADD COLUMN IF NOT EXISTS media_id BIGINT REFERENCES message_media(id)",
    "ADD COLUMN IF NOT EXISTS link_url TEXT NOT NULL DEFAULT ''",
    "ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMPTZ",
    "ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ",
):
    require(column in backend, f"announcement schema is missing {column}")
require("app.get('/api/admin/announcements'" in backend and "app.post('/api/admin/announcements'" in backend,
        "protected announcement management/list and create endpoints are missing")
require("app.patch('/api/admin/announcements/:announcementId'" in backend,
        "announcement publish/unpublish/schedule endpoint is missing")
require("hasContent=['title','body','priority','mediaId','linkUrl']" in backend and
        "ANNOUNCEMENT_UPDATED" in backend and "UPDATE fynx_announcements SET title=$1,body=$2,priority=$3,media_id=$4,link_url=$5" in backend,
        "announcement edits must update validated content, image and link in the same audited transaction")
require("message_media WHERE id=$1 AND owner_id=$2" in backend and "startsWith('image/')" in backend,
        "announcement images must be uploaded by the current admin and must be images")
require("String(raw)!==String(existing.media_id??'')" in backend,
        "editing an announcement must retain its already-authorized image when another admin edits the text")
require("parsed.protocol!=='https:'" in backend and "parsed.username" in backend and "parsed.password" in backend,
        "announcement links must require HTTPS and reject embedded credentials")
require("parseFutureAnnouncementSchedule" in backend and "day > daysInMonth" in backend and "offsetHour > 14" in backend and "date.getTime() <= Date.now()" in backend and "ANNOUNCEMENT_SCHEDULED" in backend,
        "announcement schedules must be future-dated and audited")
require("scheduled_at IS NULL OR scheduled_at<=NOW()" in backend,
        "public announcements must not expose future scheduled items")
require("effective_published_at" in backend and "CASE WHEN scheduled_at IS NOT NULL AND scheduled_at<=NOW() THEN scheduled_at ELSE published_at END" in backend,
        "a due announcement must transition to a published display state without a timer")
require("a.media_id = mm.id" in media_server and "a.published = TRUE" in media_server and
        "a.scheduled_at IS NULL OR a.scheduled_at <= NOW()" in media_server and
        "has_media_id" in media_server and "announcementMediaTable.rows[0]?.has_media_id" in media_server,
        "published announcement images must be authorized by the protected media route")
require("ActivityResultContracts.GetContent()" in source and "FynxProductionMessaging.uploadMedia(context, uri, mimeType)" in source,
        "announcement image attachment must use the existing authenticated media uploader")
require("FynxAdminClient.adminAnnouncements(context)" in source and "FynxAdminClient.setAnnouncementPublishing(context" in source and
        "FynxAdminClient.updateAnnouncement(context" in source and "editingAnnouncementId = item.id" in source,
        "announcement admin queue must support listing, editing, publishing and cancellation")
require("FynxAdminClient.publishAnnouncement(" in source and "announcementScheduleAt" in source and "announcementLinkUrl" in source,
        "announcement composer is missing image/link/scheduling controls")
require("uriHandler.openUri(link)" in source and "Text(\"Open link\")" in source and "FynxRemoteMedia(" in source,
        "public announcements must render uploaded images and open validated links")
require('Text("YYYY-MM-DDTHH:mm:ss+01:00")' in source,
        "announcement schedule hint must remain timezone-aware without a stale fixed date")
require("does not sign the user out or disable other FYNX features" in source,
        "account lock confirmation must accurately describe its enforced scope")
require(re.search(r"^\s*app\.post\('/api/social/posts'", social, re.MULTILINE) is not None,
        "social post route must remain registered outside the preceding comment")

# Marketplace promotions and the OWNER-only Music Library must appear in the same searchable audit history.
require("recordMarketplaceAdminActivity" in advertising and "CREATE TABLE IF NOT EXISTS fynx_admin_activity" in advertising,
        "Marketplace promotion administration must initialize and write to admin activity history")
require("LOWER(COALESCE(mime_type,'')) LIKE 'image/%'" in advertising and
        "p.artwork_media_id=m.id" in advertising,
        "seasonal campaign artwork must be an image and existing attached artwork can be retained safely by another admin")
for action in ("SEASONAL_PROMOTION_CREATED", "SEASONAL_PROMOTION_UPDATED", "SEASONAL_PROMOTION_ACTIVATED",
               "SEASONAL_PROMOTION_DEACTIVATED", "SEASONAL_PROMOTION_DELETED"):
    require(action in advertising, f"Marketplace promotion action is not audited: {action}")
require('app.patch("/api/admin/marketplace/seasonal-promotions/:id/edit"' in advertising and
        "FynxMarketplaceSeasonalPromotionClient.update(" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt").read_text(encoding="utf-8") and
        "editingCampaignId" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt").read_text(encoding="utf-8"),
        "seasonal campaign details and dates must be editable from the Owner/Admin panel")
require('"SEASONAL_PROMOTION_UPDATED" -> "Campaigns updated"' in source and
        '"SEASONAL_PROMOTION_UPDATED" to "Campaigns updated"' in source,
        "admin activity history must provide a filter for edited Marketplace campaigns")
require('"ANNOUNCEMENT_UPDATED" -> "Announcements edited"' in source and
        '"ANNOUNCEMENT_UPDATED" to "Announcements edited"' in source,
        "admin activity history must expose edited announcements as a searchable action type")
require("fynx-seasonal-promo:" in advertising and "starts_at < $3 AND ends_at > $2" in advertising and
        "active campaign with this key already overlaps" in advertising,
        "active seasonal campaigns with the same key must reject overlapping time windows")
require(advertising.count("pg_advisory_xact_lock(hashtext($1))") >= 3 and
        "SELECT id,occasion_key,starts_at,ends_at,headline FROM marketplace_seasonal_promotions WHERE id=$1 FOR UPDATE" in advertising and
        "another active campaign with this key already overlaps the schedule" in advertising,
        "resuming a paused campaign must also enforce active campaign schedule overlap protection")
panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt").read_text(encoding="utf-8")
banner = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePromotionalBanner.kt").read_text(encoding="utf-8")
require('Switch(checked = destinationLinkEnabled' in panel and 'destinationType = "NONE"' in panel and
        'destination_type IN (\'NONE\', \'MARKETPLACE\', \'CATEGORY\', \'PRODUCT\', \'SHOP\')' in advertising and
        'slide.destinationType == "NONE" -> Unit' in banner and 'if (slide.destinationType != "NONE") Button(' in banner,
        "campaign destination switch must hide the CTA and suppress navigation when disabled, while preserving enabled destinations")
require("OffsetDateTime.parse(value.trim()).toInstant()" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt").read_text(encoding="utf-8"),
        "campaign editing must parse fractional-second UTC timestamps and WAT offsets without shifting their time")
require("recordMusicAdminActivity" in social and "CREATE TABLE IF NOT EXISTS fynx_admin_activity" in social,
        "Music Library administration must initialize and write to admin activity history")
require("metadata = {}, executor = pool" in advertising and advertising.count("recordMarketplaceAdminActivity(") >= 5 and
        advertising.count('await client.query("BEGIN")') >= 3 and advertising.count('await client.query("COMMIT")') >= 3 and
        "await executor.query(" in advertising,
        "Marketplace promotion mutations and their audit records must commit atomically")
require("metadata = {}, executor = pool" in social and social.count("recordMusicAdminActivity(") >= 4 and
        social.count("await client.query('BEGIN')") >= 3 and social.count("await client.query('COMMIT')") >= 3 and
        "await executor.query(" in social,
        "Music Library mutations and their audit records must commit atomically")
for action in ("MUSIC_TRACK_PUBLISHED", "MUSIC_TRACK_ACTIVATED", "MUSIC_TRACK_HIDDEN", "MUSIC_TRACK_REMOVED"):
    require(action in social, f"Music Library action is not audited: {action}")
require("if (role !== 'OWNER')" in social and social.count("if (role !== 'OWNER')") >= 4 and
        "app.get('/api/admin/social/music/catalogue', auth" in social,
        "Music catalogue management endpoints, including the admin roster/list, must enforce OWNER role server-side")
require("ensureFynxMusicAdminSchema" in social and "fynxMusicAdminSchemaPromise" in social and
        "fynxMusicAdminSchemaPromise = null" in social,
        "Music Library role checks must initialize admin schema once per process and retry after initialization failure")
require("!Number.isSafeInteger(durationMs) || durationMs < 1 || durationMs > 86400000" in social,
        "Music Library must reject missing, fractional, zero, negative and excessive track durations")
require(r"(?:\.(\d{1,3}))?" in advertising,
        "campaign timestamp parser must accept ISO-8601 fractional seconds using a literal decimal point")
require("function parseCampaignTimestamp(value)" in advertising and
        "parseCampaignTimestamp(req.body?.startsAt)" in advertising and
        "parseCampaignTimestamp(req.body?.endsAt)" in advertising and
        "(Z|([+-])(\\d{2}):(\\d{2}))" in advertising,
        "seasonal promotion create/edit must require explicit timezone and validate calendar timestamps")
postgres_ids = (ROOT / "backend/postgresIds.js").read_text(encoding="utf-8")
require("MAX_POSTGRES_BIGINT" in postgres_ids and "export function isPositivePostgresBigint(value)" in postgres_ids and
        "isPositivePostgresBigint(mediaIdRaw)" in backend and "isPositivePostgresBigint(raw)" in backend,
        "announcement image IDs must use the shared positive PostgreSQL BIGINT validator before database access")

# The Admin PR must not inherit Marketplace/Wallet visual failures when its live
# Offline role-fixture UI remains testable without the backend; live publishing
# must stay clearly unverified when no healthy authenticated backend is present.
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
runtime_runner = (ROOT / "scripts/run_android_runtime_certification.sh").read_text(encoding="utf-8")
require("FYNX_ADMIN_VISUALS_ONLY" in workflow and "admin/control-center-complete-20261112" in workflow,
        "Admin PR must select its own runtime certification scope")
emulator_workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
emulator_runner = (ROOT / "scripts/run_android_runtime_emulator_certification.sh").read_text(encoding="utf-8")
admin_branch = "workstream4/admin-trust-support-music-20261123"
emulator_step_start = emulator_workflow.index("- name: Run Android instrumentation tests and capture the real FYNX UI")
emulator_step_end = emulator_workflow.index("timeout-minutes:", emulator_step_start)
emulator_step = emulator_workflow[emulator_step_start:emulator_step_end]
require("github.event.pull_request.head.ref == 'workstream4/admin-trust-support-music-20261123'" in emulator_step,
        "Admin PR must actually select the Android emulator/instrumentation step")
kvm_step_start = emulator_workflow.index("- name: Enable KVM permissions for Android emulator")
kvm_step_end = emulator_workflow.index("run: |", kvm_step_start)
kvm_step = emulator_workflow[kvm_step_start:kvm_step_end]
require("github.event.pull_request.head.ref == 'workstream4/admin-trust-support-music-20261123'" in kvm_step,
        "Admin PR must select the KVM permission setup before starting the hardware-accelerated emulator")
for screenshot in ("FYNX-admin-owner-music-library.png", "FYNX-admin-owner-marketplace-campaigns.png", "FYNX-admin-admin-marketplace-campaigns.png", "FYNX-marketplace-offline-banner.png"):
    require(screenshot in emulator_runner, f"emulator runner must require collection of {screenshot}")
require("ADMIN_SCREENSHOT_EVIDENCE=GREEN" in emulator_runner and "missing_admin_screenshots" in emulator_runner,
        "Admin emulator certification must fail when any required screenshot is absent")
chat_final_workflow = (ROOT / ".github/workflows/fynx-chat-final-closure.yml").read_text(encoding="utf-8")
chat_parity_workflow = (ROOT / ".github/workflows/fynx-chat-group-parity-layout.yml").read_text(encoding="utf-8")
require(admin_branch not in chat_final_workflow and admin_branch not in chat_parity_workflow,
        "Admin branch must not create unrelated Chat workflow push triggers")
require('if [ "${FYNX_ADMIN_VISUALS_ONLY:-0}" = "1" ]; then' in runtime_runner and
        "FYNX-admin-runtime-certification.md" in runtime_runner and
        "LIVE_BACKEND_PUBLISHING_NOT_TESTED" in runtime_runner and
        'if [ "${TEST_EXIT}" -ne 0 ]; then' in runtime_runner,
        "Admin runtime must separate offline role-fixture UI from untested live publishing while keeping Android instrumentation failures blocking")


require(LOCAL_CAMPAIGN_STORE.is_file(), "device-local Marketplace campaign storage is present")
require(SAMPLE_BANNER_ARTWORK.is_file(), "bundled FYNX sample Marketplace banner artwork is present")
local_store_source = LOCAL_CAMPAIGN_STORE.read_text(encoding="utf-8")
local_admin_source = LOCAL_CAMPAIGN_ADMIN.read_text(encoding="utf-8")
local_client_source = LOCAL_CAMPAIGN_CLIENT.read_text(encoding="utf-8")
banner_source = MARKETPLACE_BANNER.read_text(encoding="utf-8")
marketplace_source = MARKETPLACE_PANEL.read_text(encoding="utf-8")
require("marketplace-campaign-artwork" in local_store_source and "filesDir" in local_store_source,
        "local campaign photos must be copied into app-private storage")
require("MAX_ARTWORK_BYTES" in local_store_source and "MAX_CAMPAIGNS" in local_store_source,
        "local campaign uploads and count must have explicit limits")
require("Publish on this phone" in local_admin_source and "localTestMode" in local_admin_source,
        "Admin campaign manager must expose explicit device-only offline publishing")
require("localArtworkUri" in local_client_source and "FynxMarketplaceLocalCampaignStore" in local_client_source,
        "campaign client must merge device-local previews with the remote campaign feed")
require("MarketplaceLocalCampaignArtwork" in banner_source and "localArtworkUri" in banner_source,
        "Marketplace banner must render local artwork without the media API")
require("FynxMarketplaceLocalCampaignStore.active(context)" in marketplace_source,
        "Marketplace must load local banner campaigns before waiting for backend")
require("FynxMarketplaceLocalCampaignStore.observe(context).collectAsState()" in marketplace_source,
        "Marketplace must react immediately when Admin changes a local campaign")
require("activeCampaigns = MutableStateFlow" in local_store_source and "publishActive(context.applicationContext)" in local_store_source,
        "local campaign storage must publish state changes to the Marketplace screen")
require("MAX_CAMPAIGNS = 20" in local_store_source and "seasonalPromotions.take(20).forEach" in banner_source,
        "the Marketplace banner must support the full twenty-campaign local testing allowance, not a two- or four-slide hard cap")

print("ADMIN_CONTROL_CENTER_CONTRACT=GREEN")
print(f"CONFIRMED_SENSITIVE_ACTION_FLOWS={confirmations}")
print("SERVER_AUTHORITATIVE_ROLE_AND_SECTION_NAVIGATION=GREEN")
