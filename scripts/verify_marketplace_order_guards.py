#!/usr/bin/env python3
"""Static regressions for Marketplace seller bounds, payment email and order dispute rules."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"
SHOP = (ROOT / "backend/marketplaceShopProfiles.js").read_text(encoding="utf-8")
SELLER_UI = (UI / "FynxMarketplaceSellerCenterPanel.kt").read_text(encoding="utf-8")
LIFECYCLE = (UI / "FynxMarketplaceOrderLifecycle.kt").read_text(encoding="utf-8")
PANEL = (UI / "FynxMarketplacePanel.kt").read_text(encoding="utf-8")
PAYMENTS = (UI / "FynxMarketplacePayments.kt").read_text(encoding="utf-8")
CLIENT = (UI / "FynxMarketplaceClient.kt").read_text(encoding="utf-8")
REMOTE = (UI / "FynxRemoteSocialClient.kt").read_text(encoding="utf-8")

checks = [
    ("public shop serializer never returns private contact phone", "const publicProfile = (row) => ({" in SHOP and "contactPhone:" not in SHOP[SHOP.index("const publicProfile"):SHOP.index("const privateProfile")]),
    ("private My Shop serializer returns saved contact phone", "const privateProfile = (row) => ({" in SHOP and "contactPhone: row.contact_phone || ''" in SHOP and "return res.json({ shop: privateProfile(result.rows[0]) })" in SHOP),
    ("public shop route uses redacted profile serializer", "return res.json({ shop: publicProfile(result.rows[0]) })" in SHOP),
    ("shop partial patch preserves omitted values and separately handles explicit logo/cover removal", "shop_name = COALESCE($2, marketplace_shop_profiles.shop_name)" in SHOP and "description = COALESCE($3, marketplace_shop_profiles.description)" in SHOP and "location = COALESCE($4, marketplace_shop_profiles.location)" in SHOP and "contact_phone = COALESCE($5, marketplace_shop_profiles.contact_phone)" in SHOP and "CASE WHEN $8 THEN EXCLUDED.logo_media_id ELSE marketplace_shop_profiles.logo_media_id END" in SHOP and "CASE WHEN $9 THEN EXCLUDED.cover_media_id ELSE marketplace_shop_profiles.cover_media_id END" in SHOP),
    ("Seller Center labels shop contact private and explains public visibility", 'Text("Private shop contact (optional)")' in SELLER_UI and "It is not shown on your public storefront." in SELLER_UI),
    ("disputes are restricted to post-payment, active protected order states", 'status.trim().uppercase(Locale.US) in setOf("PAID", "SHIPPED", "DELIVERED", "INSPECTION")' in LIFECYCLE and "else if (marketplaceOrderAllowsProblemReport(order.status))" in LIFECYCLE),
    ("order detail actions apply the same dispute status gate and surface request errors", "val canReportProblem = marketplaceOrderAllowsProblemReport(order.status)" in PANEL and "canReportProblem -> { dispute = true" in PANEL and "The dispute could not be opened." in PANEL),
    ("Paystack payment email uses full Android email validation", "internal fun isValidMarketplacePaymentEmail(value: String)" in PAYMENTS and "Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()" in PAYMENTS),
    ("single and group order payment buttons use the strict email validator", PANEL.count("isValidMarketplacePaymentEmail(email)") >= 4 and "email.contains(\"@\")" not in PANEL),
    ("listing create entry validates title, quantity, currency and fulfillment bounds", "title.trim().length !in 2..FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH" in CLIENT and "quantity !in 1..FynxMarketplaceSellerFlowSupport.MAX_QUANTITY" in CLIENT and "if (!deliveryAvailable && !pickupAvailable)" in CLIENT),
    ("listing update entry validates title and stock against the shared support limits", "cleanTitle.length !in 2..FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH" in CLIENT and "quantity !in 0..FynxMarketplaceSellerFlowSupport.MAX_QUANTITY" in CLIENT),
    ("active seller publish entry validates bounds and requires a fulfillment option", "title.trim().length in 2..FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH" in REMOTE and "quantity in 1..FynxMarketplaceSellerFlowSupport.MAX_QUANTITY" in REMOTE and "require(deliveryAvailable || pickupAvailable)" in REMOTE),
    ("seller publish uses the saved My Shop identity when no shop name is supplied", "val persistedShopName = if (storeName.trim().isNotEmpty())" in REMOTE and 'put("storeName", persistedShopName)' in REMOTE and '"/api/marketplace/my-shop"' in REMOTE),
    ("seller editor enforces title and stock limits matching backend guards", "editTitle = it.take(FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH)" in SELLER_UI and "it in 0..FynxMarketplaceSellerFlowSupport.MAX_QUANTITY" in SELLER_UI),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    print(f"Marketplace order-guard verification failed: {len(failed)}/{len(checks)}")
    sys.exit(1)
print(f"Marketplace order-guard verification passed: {len(checks)}/{len(checks)}")
