from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")
realtime = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt").read_text(encoding="utf-8")
calls = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt").read_text(encoding="utf-8")
active_call = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxActiveCallPanel.kt").read_text(encoding="utf-8")
backend = (ROOT / "backend/server.js").read_text(encoding="utf-8")
details = panel.split("private fun MarketplaceDetails", 1)[1]
files = {
    "trust": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceTrustPassport.kt",
    "buy_together": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceBuyTogether.kt",
    "live_proof": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceLiveProof.kt",
    "price_watch": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePriceWatch.kt",
    "assistant": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceBuyingAssistant.kt",
}

live_proof = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceLiveProof.kt").read_text(encoding="utf-8")

required_surfaces = {
    "trust": "FynxMarketplaceTrustPassport(",
    "buy_together": "FynxMarketplaceBuyTogether(",
    "live_proof": "FynxMarketplaceLiveProof(",
    "price_watch": "FynxMarketplacePriceWatch(",
    "assistant": "FynxMarketplaceBuyingAssistant(",
}

for name, path_obj in files.items():
    if not path_obj.is_file():
        raise SystemExit(f"MARKETPLACE BATCH 2 RED: missing {name} source")
    if required_surfaces[name] not in details:
        raise SystemExit(f"MARKETPLACE BATCH 2 RED: {name} is not wired into real listing details")

checks = [
    ("listing identity reaches every Batch 2 surface", "listing = l" in details),
    ("Trust Passport uses existing seller reputation", "sellerReputation = sellerReputations[sellerKey]" in panel and "sellerReputation:" in details),
    ("Buy Together keeps the canonical listing ID", "FynxShareActions.marketplacePayload(listing.id, listing.title)" in panel),
    ("Live Proof uses the shared CameraX engine with a Marketplace-specific mode", "liveProofMode = true" in live_proof and "FynxCameraCapturePanel(" in live_proof and "onLiveProofStart" in live_proof),
    ("Live Proof routes the seller and preserves product identity/title through the authenticated call invite", "onLiveProof = { onLiveProof(listing.id, listing.sellerUsername, listing.title) }" in panel and "marketplaceCallListingId = listingId" in app and "marketplaceCallProductTitle = productTitle" in app and "marketplaceListingId = call.marketplaceListingId" in backend and "marketplaceProductTitle = call.marketplaceProductTitle" in backend and "marketplaceListingId: String? = null" in realtime and "initialMarketplaceProductTitle = pendingIncomingCall?.marketplaceProductTitle" in app and "activeMarketplaceProductTitle = event.marketplaceProductTitle" in calls and "marketplaceProductTitle = activeMarketplaceProductTitle" in calls and 'Text("Live item: $it"' in active_call),
    ("Price Watch uses the authenticated Marketplace backend", "marketplacePriceWatchState(context, listingId)" in panel and "watchMarketplacePrice(context, listingId)" in panel and "price_watch_$listingId" not in panel),
    ("Buying Assistant opens FYNX AI with the selected listing context", "onOpenAssistant = { listingId -> onOpenAi(listingId) }" in panel and 'onOpenAi = { listingId -> marketplaceAiListingId = listingId; selected = "AI" }' in app and "initialMarketplaceListingId = marketplaceAiListingId" in app),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Marketplace Batch 2 integration verification failed")
print(f"Marketplace Batch 2 integration verification passed: {len(checks)}/{len(checks)}")
