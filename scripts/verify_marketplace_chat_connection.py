#!/usr/bin/env python3
"""Static guard for the canonical Marketplace -> Chat integration surface."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKETPLACE = ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt"
INTEGRATION = ROOT / "app/src/main/java/com/fynx/app/ui/FynxR6GIntegrationClient.kt"
BACKEND = ROOT / "backend/r6gIntegrationRoutes.js"
MESSAGING = ROOT / "app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt"


def require(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"FAIL: {path}: missing {missing}")


require(
    MARKETPLACE,
    "fun contactSeller(username: String)",
    "FynxDeepLinkParser.chatAppLink(normalized)",
    "TextButton(onClick = onContact)",
    "Contact seller",
)
require(
    INTEGRATION,
    "suspend fun listingContext(context: Context, listingId: String)",
    "suspend fun attachListingToMessage(context: Context, messageId: String, listingId: String",
    "/api/r6g/messages/$message/context",
)
require(
    BACKEND,
    "/api/r6g/messages/:id/context",
    "message.listing_id",
    "message.business_id",
)
require(
    MESSAGING,
    "suspend fun sendText(context: Context, recipientUsername: String, text: String",
    "data class RemoteMessage(",
)

print("GREEN: Marketplace -> existing FYNX Chat path and canonical listing/message context hooks are present.")
