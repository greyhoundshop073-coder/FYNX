#!/usr/bin/env python3
"""Regression checks for Marketplace seller bounds, payment email and dispute actions."""
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
    ("public shop serializer never returns private contact phone",
     "const publicProfile = (row) => ({" in SHOP
     and "contactPhone:" not in SHOP[SHOP.index("const publicProfile"):SHOP.index("const privateProfile")]),
    ("private My Shop serializer returns the seller's saved contact phone",
     "const privateProfile = (row) => ({" in SHOP
     and "contactPhone: row.contact_phone || ''" in SHOP
     and "return res.json({ shop: privateProfile(result.rows[0]) })" in SHOP),
    ("public shop endpoint uses the redacted serializer",
     "return res.json({ shop: publicProfile(result.rows[0]) })" in SHOP),
    ("shop partial update preserves omitted values and supports explicit logo/cover removal",
     "shop_name = COALESCE($2, marketplace_shop_profiles.shop_name)" in SHOP
     and "description = COALESCE($3, marketplace_shop_profiles.description)" in SHOP
     and "location = COALESCE($4, marketplace_shop_profiles.location)" in SHOP
     and "contact_phone = COALESCE($5, marketplace_shop_profiles.contact_phone)" in SHOP
     and "CASE WHEN $8 THEN EXCLUDED.logo_media_id ELSE marketplace_shop_profiles.logo_media_id END" in SHOP
     and "CASE WHEN $9 THEN EXCLUDED.cover_media_id ELSE marketplace_shop_profiles.cover_media_id END" in SHOP),
    ("Seller Center identifies contact phone as private",
     'Text("Private shop contact (optional)")' in SELLER_UI
     and "It is not shown on your public storefront." in SELLER_UI),
    ("order lifecycle screen restricts disputes to paid and protected in-progress states",
     'status.trim().uppercase(Locale.US) in setOf("PAID", "SHIPPED", "DELIVERED", "INSPECTION")' in LIFECYCLE
     and "else if (marketplaceOrderAllowsProblemReport(order.status))" in LIFECYCLE),
    ("order detail dialog uses the same protected-state dispute gate",
     "val canReportProblem = marketplaceOrderAllowsProblemReport(order.status)" in PANEL
     and "dispute && canReportProblem -> Button(" in PANEL
     and "if (dispute || !canReportProblem) onClose() else dispute = true" in PANEL
     and 'Text(if (!dispute && canReportProblem) "Report problem" else "Close")' in PANEL
     and 'if (!dispute && order.status != "COMPLETED") dispute = true' not in PANEL),
    ("single and multi-order Paystack initialization use strict Android email validation",
     PAYMENTS.count("if (!isValidMarketplacePaymentEmail(email))") == 2
     and "android.util.Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()" in PAYMENTS
     and 'email.contains("@")' not in PAYMENTS),
    ("listing create validates title, quantity, currency and fulfillment bounds",
     "title.trim().length !in 2..FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH" in CLIENT
     and "quantity !in 1..FynxMarketplaceSellerFlowSupport.MAX_QUANTITY" in CLIENT
     and "if (!deliveryAvailable && !pickupAvailable)" in CLIENT),
    ("listing update validates title and stock against shared support limits",
     "cleanTitle.length !in 2..FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH" in CLIENT
     and "quantity !in 0..FynxMarketplaceSellerFlowSupport.MAX_QUANTITY" in CLIENT),
    ("seller publish validates bounds and requires a fulfillment option",
     "title.trim().length in 2..FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH" in REMOTE
     and "quantity in 1..FynxMarketplaceSellerFlowSupport.MAX_QUANTITY" in REMOTE
     and "require(deliveryAvailable || pickupAvailable)" in REMOTE),
    ("seller publish uses the saved My Shop identity when no shop name is supplied",
     "val persistedShopName = if (storeName.trim().isNotEmpty())" in REMOTE
     and 'put("storeName", persistedShopName)' in REMOTE
     and '"/api/marketplace/my-shop"' in REMOTE),
    ("seller editor enforces title and stock limits matching the backend",
     "editTitle = it.take(FynxMarketplaceSellerFlowSupport.MAX_TITLE_LENGTH)" in SELLER_UI
     and "it in 0..FynxMarketplaceSellerFlowSupport.MAX_QUANTITY" in SELLER_UI),
]
failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    print(f"Marketplace order-guard verification failed: {len(failed)}/{len(checks)}")
    sys.exit(1)
print(f"Marketplace order-guard verification passed: {len(checks)}/{len(checks)}")
