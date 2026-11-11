#!/usr/bin/env python3
"""Regression contract for the Owner/Admin Control Center safety and navigation UI."""
from pathlib import Path
import re
import sys

SOURCE = Path("app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt")
if not SOURCE.is_file():
    sys.exit(f"Missing Admin Control Center source: {SOURCE}")
source = SOURCE.read_text(encoding="utf-8")

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
require('listOf("Overview", "Safety", "Marketplace", "People", "Announcements")' in source,
        "expected Admin sections are missing")

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
    "announcement publishing": r"FynxAdminClient\.publishAnnouncement\(context,",
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

print("ADMIN_CONTROL_CENTER_CONTRACT=GREEN")
print(f"CONFIRMED_SENSITIVE_ACTION_FLOWS={confirmations}")
print("SERVER_AUTHORITATIVE_ROLE_AND_SECTION_NAVIGATION=GREEN")
