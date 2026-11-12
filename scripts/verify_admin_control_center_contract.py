#!/usr/bin/env python3
"""Regression contract for the Owner/Admin Control Center safety and navigation UI."""
from pathlib import Path
import re
import sys

SOURCE = Path("app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt")
CLIENT = Path("app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt")
BACKEND = Path("backend/adminRoutes.js")
if not SOURCE.is_file():
    sys.exit(f"Missing Admin Control Center source: {SOURCE}")
source = SOURCE.read_text(encoding="utf-8")
client = CLIENT.read_text(encoding="utf-8") if CLIENT.is_file() else ""
backend = BACKEND.read_text(encoding="utf-8") if BACKEND.is_file() else ""

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
require("FynxAdminClient.activity(context, activityQuery)" in source, "activity history is not connected to the backend")
require("REPORT_REVIEWED" in backend and "APPEAL_DECIDED" in backend, "safety review actions are not audited")

# Report and appeal data must be fetched from the protected APIs and rendered as actionable queues.
require('"/api/admin/reports"' in client and "FynxAdminClient.reports(context)" in source,
        "reported-account queue is not connected end to end")
require("reports.take(20).forEach" in source and "FynxAdminClient.reviewReport(context, report.id" in source,
        "reported-account review actions are not rendered")
require("FynxAdminClient.appeals(context)" in source and "FynxAdminClient.reviewAppeal(context, appeal.id" in source,
        "appeal review actions are not connected to the queue")

# The audit event must commit with the safety state change, not after a response-worthy mutation.
require("executor = pool" in backend, "audit helper cannot use a transaction-scoped executor")
require("recordAdminActivity(req,'REPORT_REVIEWED'" in backend and "recordAdminActivity(req,'APPEAL_DECIDED'" in backend,
        "report and appeal decisions must be audited")
require("recordAdminActivity(req,'ACCOUNT_STATUS_CHANGED'" in backend,
        "account status changes must be audited")
require("FOR UPDATE" in backend and "appeal has already received a final decision" in backend,
        "appeal decisions must lock the appeal and reject final-state rewrites")

print("ADMIN_CONTROL_CENTER_CONTRACT=GREEN")
print(f"CONFIRMED_SENSITIVE_ACTION_FLOWS={confirmations}")
print("SERVER_AUTHORITATIVE_ROLE_AND_SECTION_NAVIGATION=GREEN")
