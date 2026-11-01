#!/usr/bin/env python3
"""Ensure the checkout quote keeps the full FYNX fee distinct from the buyer's share."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
client = (root / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt").read_text(encoding="utf-8")
panel = (root / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text(encoding="utf-8")
server = (root / "backend/marketplaceCheckout.js").read_text(encoding="utf-8")
checkout_order = (root / "backend/marketplaceCheckoutOrder.js").read_text(encoding="utf-8")
client_test_path = "app/src/test/java/com/fynx/app/ui/FynxMarketplaceCheckoutQuoteTest.kt"
client_test_content = (root / client_test_path).read_text(encoding="utf-8")

checks = [
    ("backend exposes total, buyer and seller fee fields",
     "marketplaceFee, marketplaceFeeBuyer, marketplaceFeeSeller" in server),
    ("Android quote parser requires the full marketplace fee field",
     'val marketplaceFee = amount("marketplaceFee")' in client and 'marketplaceFee = marketplaceFee' in client),
    ("Android quote parser requires the buyer-payable fee field",
     'val marketplaceFeeBuyer = amount("marketplaceFeeBuyer")' in client and 'marketplaceFeeBuyer = marketplaceFeeBuyer' in client),
    ("total fee is not accidentally mapped from the buyer share",
     'marketplaceFee = q.optDouble("marketplaceFeeBuyer")' not in client),
    ("checkout display uses the buyer-payable fee share",
     'q.marketplaceFeeBuyer' in client),
    ("quote parser rejects identity, listing/quantity/method/currency mismatches and malformed expiry",
     "internal fun parseMarketplaceCheckoutQuoteResponse" in client and 'requiredText("id")' in client and 'requiredText("expiresAt")' in client and "marketplaceQuoteExpired(expiresAt, now)" in client and 'listingId != expectedListingId.trim()' in client and 'quantity != expectedQuantity' in client and 'currency != expectedCurrency.trim().uppercase(Locale.US)' in client and 'method != expectedFulfillmentMethod.trim().uppercase(Locale.US)' in client),
    ("quote amounts reject missing non-finite or negative values rather than rendering zero totals",
     'quote.optDouble(field, Double.NaN)' in client and 'amounts.any { !it.isFinite() }' in client and 'deliveryFee < 0.0' in client and 'total <= 0.0' in client),
    ("quote subtotal and payable total reconcile with the server quantity and fee breakdown",
     'Math.round(unitPrice * quantity * 100.0) / 100.0' in client and 'subtotal + deliveryFee + marketplaceFeeBuyer - discountAmount' in client and 'kotlin.math.abs(total - expectedTotal) > 0.02' in client),
    ("single-order retry fingerprint scopes by account and intent without persisting raw token/address",
     'marketplaceCheckoutIntentFingerprint(' in client and 'marketplaceCheckoutAccountScope(accessToken.trim())' in client and 'JSONObject(String(payload, Charsets.UTF_8)).optString("sub").trim()' in client and '"subject:"' in client and '"token:"' in client and 'put("accountScope"' in client and 'put("address", value.address.trim().take(300))' in client and 'MessageDigest.getInstance("SHA-256")' in client),
    ("single-order ID persists for ambiguous retries and remains while server order is awaiting payment",
     'marketplaceCheckoutOrderId(context, it)' in client and 'onSuccess { order ->' in client and 'val stableOrderId = intentFingerprint?.let' in client and 'val stableOrderId = UUID.randomUUID().toString()' not in client and 'clearMarketplaceCheckoutOrderId(context, it)' not in client),
    ("single-order retry ID is cleared only after an authoritative Paystack or Wallet payment succeeds",
     'clearMarketplaceCheckoutOrderIdForOrder(context, order.id)' in panel and 'verifyMarketplacePayment(context, payment?.reference.orEmpty())' in panel and '.onSuccess { onPaid() }' in panel and 'FynxBuyerWalletClient.payMarketplaceOrder(' in panel and ').onSuccess {' in panel and 'only after the authoritative payment-verification path reports success for this order.' in client),
    ("stored checkout IDs are bounded and expire without storing raw delivery details",
     'MARKETPLACE_CHECKOUT_ATTEMPT_LIMIT = 50' in client and 'MARKETPLACE_CHECKOUT_ATTEMPT_MAX_AGE_MS' in client and 'preferences.all.keys.filter { it.startsWith(MARKETPLACE_CHECKOUT_ORDER_ID_PREFIX) }' in client and 'putString(idKey, created)' in client),
    ("checkout input fields and delivery/pickup controls are disabled while requests are active",
     client.count('enabled = !busy') >= 8),
    ("backend order ID remains idempotent and rejects an ID reused for a different request",
     'WHERE id=$1 AND buyer_id=$2' in checkout_order and 'idempotent: true' in checkout_order and 'ORDER_ID_REUSE_CONFLICT' in checkout_order),
    ("unit tests cover valid/malformed quote responses and checkout intent fingerprint stability",
     'validServerQuoteMapsAuthoritativeTotals' in client_test_content and 'productQuantityCurrencyAndFulfillmentMustMatchTheRequest' in client_test_content and 'subtotalAndTotalMustReconcileWithQuantityAndFeeBreakdown' in client_test_content and 'idempotencyFingerprintIsStableForTheSameAuthenticatedIntentAcrossTokenRotation' in client_test_content and 'missingSellerDisplayNameFallsBackToAuthoritativeSellerUsername' in client_test_content),
    ("valid quote seller identity survives a missing optional display name via the server-provided username",
     'sellerDisplayName = optionalText("sellerDisplayName").ifBlank { requiredText("sellerUsername") }' in client and 'requiredText("sellerUsername")' in client),
    ("Wallet checkout uses the authenticated Wallet payment endpoint only when server capability and cleared balance allow it",
     'walletSummary?.canSpend == true' in panel and 'walletSummary?.availableBalance' in panel and 'FynxBuyerWalletClient.payMarketplaceOrder(' in panel and 'Pay with FYNX Wallet' in panel),

]
failed = False
for label, passed in checks:
    print(f'{"PASS" if passed else "FAIL"}: {label}')
    failed |= not passed
raise SystemExit(1 if failed else 0)
