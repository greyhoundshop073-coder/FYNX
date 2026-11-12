#!/usr/bin/env python3
"""Regression contract for the Owner/Admin Control Center safety and navigation UI."""
from pathlib import Path
import re
import sys

SOURCE = Path("app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt")
CLIENT = Path("app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt")
BACKEND = Path("backend/adminRoutes.js")
MEDIA_SERVER = Path("backend/server.js")
ADVERTISING = Path("backend/marketplaceAdvertising.js")
SOCIAL = Path("backend/socialRoutes.js")
GROUPS = Path("backend/groupRoutes.js")
MARKETPLACE_CHECKOUT = Path("backend/marketplaceCheckout.js")
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
require("FynxAdminClient.activity(context, activityQuery, activityOffset)" in source, "activity history is not connected to the backend")
require("activityHasMore" in source and "Text(\"Previous\")" in source and "Text(\"Next\")" in source,
        "activity history is missing bounded previous/next pagination")
require("OFFSET $4" in backend and "limit+1" in backend and "Math.min(10000" in backend,
        "activity history API must use bounded database pagination")
require("REPORT_REVIEWED" in backend and "APPEAL_DECIDED" in backend, "safety review actions are not audited")

# Report and appeal data must be fetched from the protected APIs and rendered as actionable queues.
require('"/api/admin/reports"' in client and "FynxAdminClient.reports(context)" in source,
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
require('FynxAdminClient.setAccountStatus(context, user.id, "LIMITED")' in source and
        "'LIMITED'" in backend,
        "People section must expose a server-supported limited-account action")
require("String(safety.account_status)==='LIMITED'" in groups and
        "String(safety.account_status) === 'LIMITED'" in marketplace_checkout,
        "LIMITED must enforce restrictions in group messaging and Marketplace checkout")
require("FOR UPDATE" in backend and "appeal has already received a final decision" in backend,
        "appeal decisions must lock the appeal and reject final-state rewrites")

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
for action in ("SEASONAL_PROMOTION_CREATED", "SEASONAL_PROMOTION_ACTIVATED",
               "SEASONAL_PROMOTION_DEACTIVATED", "SEASONAL_PROMOTION_DELETED"):
    require(action in advertising, f"Marketplace promotion action is not audited: {action}")
require("recordMusicAdminActivity" in social and "CREATE TABLE IF NOT EXISTS fynx_admin_activity" in social,
        "Music Library administration must initialize and write to admin activity history")
for action in ("MUSIC_TRACK_PUBLISHED", "MUSIC_TRACK_ACTIVATED", "MUSIC_TRACK_HIDDEN", "MUSIC_TRACK_REMOVED"):
    require(action in social, f"Music Library action is not audited: {action}")
require("if (role !== 'OWNER')" in social and social.count("if (role !== 'OWNER')") >= 3,
        "Music catalogue write endpoints must enforce OWNER role server-side")

print("ADMIN_CONTROL_CENTER_CONTRACT=GREEN")
print(f"CONFIRMED_SENSITIVE_ACTION_FLOWS={confirmations}")
print("SERVER_AUTHORITATIVE_ROLE_AND_SECTION_NAVIGATION=GREEN")
