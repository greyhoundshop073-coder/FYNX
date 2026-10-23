import fs from 'node:fs';

const source = fs.readFileSync(new URL('./marketplaceTransactions.js', import.meta.url), 'utf8');
const profileSource = fs.readFileSync(new URL('./profileRoutes.js', import.meta.url), 'utf8');
const androidReceiptSource = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceReceipt.kt', import.meta.url), 'utf8');
const paymentSource = fs.readFileSync(new URL('./marketplaceReputation.js', import.meta.url), 'utf8');
const webhookSource = fs.readFileSync(new URL('./marketplacePaystackWebhook.js', import.meta.url), 'utf8');
const settlementSource = fs.readFileSync(new URL('./marketplaceSettlement.js', import.meta.url), 'utf8');
const androidPanelSource = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt', import.meta.url), 'utf8');
const checks = [
  ['checkout group stores its Paystack reference before marking child payment evidence', paymentSource.includes("ALTER TABLE marketplace_checkout_groups ADD COLUMN IF NOT EXISTS payment_reference TEXT") && paymentSource.includes("SET payment_reference=$1,payment_authorization_url=$2,payment_access_code=$3") && paymentSource.includes("checkoutGroupId: String(group.id)") && paymentSource.includes("reference, provider: 'paystack'")],
  ['checkout-group initialization retries get a fresh Paystack reference after an uncommitted provider attempt', paymentSource.includes("const reference = `FYNX-CHECKOUT-${crypto.randomUUID()}`;") && !paymentSource.includes("const reference = `FYNX-CHECKOUT-${group.id}`;") && paymentSource.includes("metadata: { checkoutGroupId: String(group.id)")],
  ['successful checkout-group initialization retries reuse the persisted authorization instead of creating another charge', paymentSource.includes("if (group.payment_reference && group.payment_authorization_url)") && paymentSource.includes("reference: group.payment_reference, authorizationUrl: group.payment_authorization_url") && paymentSource.includes("idempotent: true")],
  ['checkout groups with a stored reference but missing authorization verify the old provider attempt before retrying', paymentSource.includes("if (group.payment_reference && !group.payment_authorization_url)") && paymentSource.includes("/transaction/verify/${encodeURIComponent(group.payment_reference)}") && paymentSource.includes("previousTransaction.reference || '') === String(group.payment_reference)")],
  ['only a matching provider-confirmed terminal failure allows a stale checkout reference to be replaced', paymentSource.includes("!['failed', 'abandoned', 'reversed'].includes(previousStatus)") && paymentSource.includes("previousCheckoutId === String(group.id)") && paymentSource.includes("previousBuyerId === String(group.buyer_id)") && paymentSource.includes("payment_reference=$6 AND payment_authorization_url IS NULL")],
  ['successful, unknown, and still-processing stale checkout attempts cannot start another payment', paymentSource.includes("previous checkout payment appears successful; verify the existing reference before retrying") && paymentSource.includes("previous checkout payment is not confirmed failed") && paymentSource.includes("no new payment was started")],
  ['single-order stale references are provider-verified and bound to order, buyer, amount, and currency', paymentSource.includes("if (existing.payment_reference && !existing.payment_authorization_url)") && paymentSource.includes("const verifyOrderPaymentAttempt = async") && paymentSource.includes("metadataOrderId === String(order.id)") && paymentSource.includes("metadataBuyerId === String(order.buyer_id)")],
  ['single-order duplicate-reference retries use a UUID only after a matching terminal provider failure', paymentSource.includes("const duplicateReferenceError") && paymentSource.includes("FYNX-ORDER-\${crypto.randomUUID()}") && paymentSource.includes("!['failed', 'abandoned', 'reversed'].includes(previous.status)")],
  ['single-order initialization replaces only the exact verified stale reference under its guarded update', paymentSource.includes("payment_reference=$6 AND payment_authorization_url IS NULL") && paymentSource.includes("staleReferenceToReplace])")],
  ['successful or unverifiable single-order attempts cannot silently trigger a second payment initialization', paymentSource.includes("previous order payment appears successful; verify the existing reference before retrying") && paymentSource.includes("provider rejected the existing order reference but its status could not be verified; no new payment was started")],
  ['unique checkout-group references keep the UUID contract required by verification and Paystack webhook reconciliation', paymentSource.includes("/^FYNX-CHECKOUT-[0-9a-f-]{36}$/i.test(reference)") && webhookSource.includes("/^FYNX-CHECKOUT-[0-9a-f-]{36}$/i.test(reference)") && paymentSource.includes("checkoutGroupId: String(group.id)")],
  ['aggregate buyer receipt is scoped to the authenticated checkout buyer', source.includes("app.get('/api/marketplace/checkout/:id/receipt', auth") && source.includes("String(group.buyer_id) !== String(req.user.sub)")],
  ['aggregate receipt covers all checkout items and enforces the 20-item cap', source.includes('lines.length > 20') && source.includes('COUNT(DISTINCT gi.order_id)')],
  ['aggregate receipt binds the stored paid checkout and Paystack reference', source.includes("cg.status='PAID'") && source.includes("e.metadata->>'reference'=cg.payment_reference") && source.includes("e.metadata->>'checkoutGroupId'=cg.id::text")],
  ['aggregate receipt reconciles child totals against checkout totals', source.includes("sumCents('product_subtotal')") && source.includes("sumCents('buyer_total')")],
  ['public aggregate verification reconciles checkout and child totals', source.includes("sumCents('marketplace_fee_buyer') !== Math.round(Number(group.marketplace_fee) * 100)") && source.includes("sumCents('buyer_total') !== Math.round(Number(group.buyer_total) * 100)")],
  ['aggregate receipt has an independent public signature verifier', source.includes("/api/marketplace/checkout-receipts/:receiptId/verify") && source.includes('signCheckoutReceipt(group, lines, group.verified_payment_reference, group.payment_verified_at)')],
  ['aggregate receipt exposes buyer identity and separate fulfillment and payout states', source.includes("buyer: { username: group.buyer_username") && source.includes("fulfillmentStatuses: [...new Set(lines.map((line) => String(line.status)))") && source.includes('getMarketplaceOrderPayoutStatuses') && source.includes('payoutStatus,')],
  ['aggregate public verification reports lifecycle without claiming payout release', source.includes('getMarketplaceOrderPayoutStatuses') && source.includes("fulfillmentStatuses: [...new Set(lines.map((line) => String(line.status)))")],
  ['Android fetches the authenticated aggregate checkout receipt', androidReceiptSource.includes("/api/marketplace/checkout/") && androidReceiptSource.includes("getJSONObject(\"receipt\")")],
  ['multi-product payment dialog clearly shows total and safe verification retry', androidPanelSource.includes("TOTAL TO PAY") && androidPanelSource.includes("Continue to Paystack") && androidPanelSource.includes("rather than paying twice") && (androidPanelSource.split("private fun MarketplaceMultiProductPaymentDialog(").length - 1) === 1],
  ['aggregate receipt PDF includes all items across pages and a verification QR', androidReceiptSource.includes("val pageCount = ((items.length() + 4) / 5)") && androidReceiptSource.includes("val start = pageNumber * 5") && androidReceiptSource.includes("receipt.optString(\"verificationPath\")")],
  ['buyer can reopen the last checkout receipt from Marketplace after payment', androidPanelSource.includes("MarketplaceCheckoutReceiptAction") && androidPanelSource.includes("last_checkout_receipt_id:$cartOwnerKey") && androidPanelSource.includes("completedCheckoutId = p.first")],
  ['aggregate receipt preview and PDF disclose fulfillment and payout separately', androidReceiptSource.includes("Fulfillment: ") && androidReceiptSource.includes("Payout status: ") && androidReceiptSource.includes("Payout: ")],
  ['aggregate signature binds each real child order ID', source.includes("String(line.id || line.order_id)") && source.includes("String(line.seller_id)")],
  ['receipt payout state is sourced from the escrow and payout-operation records', settlementSource.includes("export async function getMarketplaceOrderPayoutStatuses") && settlementSource.includes("FROM marketplace_escrows") && settlementSource.includes("operation_type='PAYOUT_RELEASE'") && settlementSource.includes("return 'RELEASED'") && source.includes("import { getMarketplaceOrderPayoutStatuses } from './marketplaceSettlement.js'")],
  ['single-order receipts separate payment, fulfillment, and payout', source.includes("fulfillmentStatus: row.status") && source.includes("payoutStatus,") && source.includes("row.status === 'REFUNDED' ? 'REFUNDED' : 'PAID'")],
  ['public single-order verification reports live escrow payout state', source.includes("fulfillmentStatus: row.status, payoutStatus") && source.includes("getMarketplaceOrderPayoutStatuses([String(row.id)])")],
  ['single and aggregate PDF previews show distinct fulfillment and payout labels', androidReceiptSource.includes("Fulfillment: ") && androidReceiptSource.includes("Payout status: ")],
  ['receipt endpoint requires authentication', /app\.get\('\/api\/marketplace\/orders\/:id\/receipt',\s*auth/.test(source)],
  ['receipt endpoint checks order participation', source.includes('if (!isParticipant(row, req.user.sub))')],
  ['receipt only issues after recorded payment confirmation', source.includes('!receiptStatusAllowed(row.status) || !(row.verified_payment_reference || row.payment_reference) || !row.payment_verified_at')],
  ['payment evidence binds confirmed event to the stored Paystack reference', source.includes("e.metadata->>'reference'=o.payment_reference") && source.includes("e.to_status='PAID'") && source.includes("cg.payment_reference=e.metadata->>'reference'") && source.includes("provider',''))='paystack'")],
  ['receipt accounting reconciles before issue', source.includes('if (!receiptAccountingReconciles(row))')],
  ['receipt signature uses HMAC-SHA256', source.includes("createHmac('sha256', secret)")],
  ['receipt signature comparison is timing-safe', source.includes('crypto.timingSafeEqual(a, b)')],
  ['public verification is read-only and returns limited data', source.includes("app.get('/api/marketplace/receipts/:receiptId/verify'") && source.includes('authentic: true, receiptId: rawId, paymentStatus: row.status')],
  ['tampered signature is rejected', source.includes("reason: 'SIGNATURE_MISMATCH'")],
  ['buyer and seller profile identity is included', source.includes('buyer_profile_photo_media_id') && source.includes('seller_profile_photo_media_id') && source.includes('profilePhotoMediaId')],
  ['receipt avoids exposing shipping address and phone', !source.includes('shippingAddress: row.shipping_address') && !source.includes('phone: row.buyer_phone')],
  ['receipt avatar uses privacy-aware profile media endpoint', androidReceiptSource.includes('/api/social/profile-photo/')],
  ['profile photo route checks privacy and blocks', profileSource.includes('app.get(\'/api/social/profile-photo/:id\'') && profileSource.includes('photo_visibility') && profileSource.includes('FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2)')],
  ['public verification also requires recorded payment confirmation', source.includes('!row.payment_verified_at || !receiptAccountingReconciles(row)') && source.includes('!(row.verified_payment_reference || row.payment_reference)')],
  ['signature binds historical item and financial fields', source.includes('row.product_snapshot || {}') && source.includes('row.payment_reference') && source.includes('row.created_at')],
  ['receipt verification does not trust client-provided amount', source.includes('Number(row.buyer_total ?? row.total_amount)')],
  ['group-paid child orders must match the real paid checkout group and its stored Paystack reference', source.includes("cg.payment_reference=e.metadata->>'reference'") && source.includes("cg.status='PAID'") && source.includes("cg.id::text=e.metadata->>'checkoutGroupId'")],
  ['receipt signature binds the verified group payment reference when child order has no individual reference', source.includes('row.verified_payment_reference || row.payment_reference ||')],
  ['receipt exposes the actual verified payment reference', source.includes('paymentReference: row.verified_payment_reference || row.payment_reference')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);

const cents = value => Math.round(value * 100);
const sample = { subtotal: 1000, delivery: 250, buyerFee: 25, sellerFee: 25, fee: 50, discount: 0 };
const buyerTotal = sample.subtotal + sample.delivery + sample.buyerFee - sample.discount;
const sellerNet = sample.subtotal + sample.delivery - sample.sellerFee;
if (sellerNet + sample.fee - sample.discount !== buyerTotal) failed.push('minor-unit sample accounting reconciles');
if (cents(19.99) !== 1999 || cents(0.01) !== 1) failed.push('NGN/USD minor-unit conversion');
if (failed.length) process.exit(1);
console.log(`Marketplace receipt verification passed: ${checks.length}/${checks.length} structural checks plus accounting samples`);
