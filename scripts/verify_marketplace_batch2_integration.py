from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text(encoding="utf-8")
details = panel.split("private fun MarketplaceDetails", 1)[1]
files = {
    "trust": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceTrustPassport.kt",
    "buy_together": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceBuyTogether.kt",
    "live_proof": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceLiveProof.kt",
    "price_watch": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePriceWatch.kt",
    "assistant": ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceBuyingAssistant.kt",
}

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
    ("Live Proof stays on the existing seller chat path", "onLiveProof = { contactSeller(listing.sellerUsername, listing.id) }" in panel),
    ("Price Watch persists the real listing ID", "price_watch_$listingId" in panel),
    ("Buying Assistant opens the existing FYNX AI destination", 'onOpenAi = { onOpenAi() }' in panel),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit("Marketplace Batch 2 integration verification failed")
print(f"Marketplace Batch 2 integration verification passed: {len(checks)}/{len(checks)}")
