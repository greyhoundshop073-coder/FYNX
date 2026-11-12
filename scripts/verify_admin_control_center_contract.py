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

require("fun FynxAdminControlCenterPanel()" in source, "control center composable is missing")
require("FynxAdminClient.dashboard(context)" in source, "role must be fetched from the server")
require("countRows('fynx_admin_activity',\"action IN ('REPORT_REVIEWED','APPEAL_DECIDED','ACCOUNT_STATUS_CHANGED') AND created_at >= NOW()-INTERVAL '24 hours'\")" in backend and
        "safetyActions24h:counts[3]" in backend and "safetyActions24h" in client and "counts.optInt(\"safetyActions24h\")" in client,
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
require('if (activeSection == "Overview" && d.role == "OWNER")' in source,
        "music administration must be visible only to the verified owner")
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
        "AND ($2='' OR a.action=$2)" in backend,
        "activity action filtering must be URL-encoded and parameterized on the server")
require("activityHasMore" in source and "Text(\"Previous\")" in source and "Text(\"Next\")" in source,
        "activity history is missing bounded previous/next pagination")
require("OFFSET $4" in backend and "limit+1" in backend and "Math.min(10000" in backend,
        "activity history API must use bounded database pagination")
require("REPORT_REVIEWED" in backend and "APPEAL_DECIDED" in backend, "safety review actions are not audited")
require("if(actorRole!=='OWNER')return res.status(403)" in backend and "req.fynxAdminRole=actorRole" in backend,
        "admin roster and role changes must verify and record the true OWNER actor")
require("recordAdminActivity(req,'ADMIN_GRANTED'" in backend and "recordAdminActivity(req,'ADMIN_REVOKED'" in backend and
        "RETURNING user_id" in backend and "await client.query('COMMIT')" in backend,
        "admin grant/revoke and their audit records must be committed atomically")
require("owner already has owner access" in backend and "u.id<>(SELECT id FROM users ORDER BY id ASC LIMIT 1)" in backend,
        "OWNER must not receive a duplicate ADMIN grant or appear in the admin roster")

# Report and appeal data must be fetched from the protected APIs and rendered as actionable queues.
require('"/api/admin/reports?limit=' in client and "FynxAdminClient.reports(context, reportsOffset)" in source,
        "reported-account queue is not connected end to end")
require("reports.forEach" in source and "FynxAdminClient.reviewReport(context, report.id" in source,
        "reported-account review actions are not rendered")
require("FynxAdminClient.appeals(context, appealsOffset)" in source and "FynxAdminClient.reviewAppeal(context, appeal.id" in source,
        "appeal review actions are not connected to the queue")
require("FynxAdminClient.reports(context, reportsOffset)" in source and "reportsHasMore" in source and
        "appealsHasMore" in source and "Text(\"Next\")" in source,
        "report and appeal review queues must provide Previous/Next pagination")
require("app.get('/api/admin/reports'" in backend and "app.get('/api/admin/appeals'" in backend and
        backend.count("LIMIT $1 OFFSET $2") >= 2 and backend.count("limit+1") >= 2 and
        backend.count("Math.min(10000" ) >= 2,
        "report and appeal APIs must enforce bounded database pagination")
require("account reports; content-report types are not presented as account reports" in source,
        "moderation queue must not misrepresent account reports as content reports")

# The audit event must commit with the safety state change, not after a response-worthy mutation.
require("executor = pool" in backend, "audit helper cannot use a transaction-scoped executor")
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
require("message_media WHERE id=$1 AND owner_id=$2" in backend and "startsWith('image/')" in backend,
        "announcement images must be uploaded by the current admin and must be images")
require("parsed.protocol!=='https:'" in backend and "parsed.username" in backend and "parsed.password" in backend,
        "announcement links must require HTTPS and reject embedded credentials")
require("scheduledAt.getTime()<=Date.now()" in backend and "ANNOUNCEMENT_SCHEDULED" in backend,
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
require("FynxAdminClient.adminAnnouncements(context)" in source and "FynxAdminClient.setAnnouncementPublishing(context" in source,
        "announcement admin queue must support listing and publishing/cancellation")
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
for action in ("SEASONAL_PROMOTION_CREATED", "SEASONAL_PROMOTION_UPDATED", "SEASONAL_PROMOTION_ACTIVATED",
               "SEASONAL_PROMOTION_DEACTIVATED", "SEASONAL_PROMOTION_DELETED"):
    require(action in advertising, f"Marketplace promotion action is not audited: {action}")
require('app.patch("/api/admin/marketplace/seasonal-promotions/:id/edit"' in advertising and
        "FynxMarketplaceSeasonalPromotionClient.update(" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt").read_text(encoding="utf-8") and
        "editingCampaignId" in (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt").read_text(encoding="utf-8"),
        "seasonal campaign details and dates must be editable from the Owner/Admin panel")
require("fynx-seasonal-promo:" in advertising and "starts_at < $3 AND ends_at > $2" in advertising and
        "active campaign with this key already overlaps" in advertising,
        "active seasonal campaigns with the same key must reject overlapping time windows")
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
require("if (role !== 'OWNER')" in social and social.count("if (role !== 'OWNER')") >= 3,
        "Music catalogue write endpoints must enforce OWNER role server-side")

print("ADMIN_CONTROL_CENTER_CONTRACT=GREEN")
print(f"CONFIRMED_SENSITIVE_ACTION_FLOWS={confirmations}")
print("SERVER_AUTHORITATIVE_ROLE_AND_SECTION_NAVIGATION=GREEN")
