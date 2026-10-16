import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const protection = read('./marketplaceProtection.js');
const resolution = read('./marketplaceProtectionResolution.js');
const completion = read('./marketplaceCompletion.js');
const transactions = read('./marketplaceTransactions.js');
const settlement = read('./marketplaceSettlement.js');
const inspection = read('./marketplaceInspectionExpiry.js');
const paymentExpiry = read('./marketplacePaymentExpiry.js');
const socialClient = read('../app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt');
const scalability = read('./scalability.js');

const checks = [
  ['buyer can open a refund request', protection.includes("app.post('/api/marketplace/protection/order/:id/refund-request'") && protection.includes("only the buyer can request a refund")],
  ['participants can open disputes', protection.includes("app.post('/api/marketplace/protection/order/:id/dispute'") && protection.includes("const isBuyer") && protection.includes("const isSeller")],
  ['dispute reasons are server-validated against the legacy database enum', protection.includes("const allowedDisputeReasons = new Set(['ITEM_NOT_RECEIVED', 'WRONG_ITEM', 'DAMAGED', 'NOT_AS_DESCRIBED', 'SUSPECTED_SCAM', 'OTHER'])") && protection.includes("caseType === 'DISPUTE' && !allowedDisputeReasons.has(reason.toUpperCase())") && protection.includes("error: 'valid dispute reason is required'")],
  ['protection endpoint preserves the Android dispute-details limit', protection.includes("details.trim().slice(0, 4000)")],
  ['Android dispute action targets the protected dispute endpoint', socialClient.includes('FynxBackendClient.postJson(context, "/api/marketplace/protection/order/$id/dispute"') && !socialClient.includes('FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/disputes"')],
  ['legacy dispute route is preserved only in transaction routes', transactions.includes("app.post('/api/marketplace/orders/:id/disputes'") && !protection.includes("app.post('/api/marketplace/orders/:id/disputes'")],
  ['legacy open disputes reconcile into protected cases', resolution.includes('async function reconcileOpenDisputes') && resolution.includes('marketplace_order_disputes') && resolution.includes('marketplace_protection_cases') && resolution.includes('dispute_id')],
  ['protection and reconciliation route modules are registered in scalability bootstrap', scalability.includes('registerMarketplaceProtectionRoutes({ app })') && scalability.includes('registerMarketplaceProtectionResolutionRoutes({ app })')],
  ['protected requests lock the order before state change', protection.includes('SELECT id,buyer_id,seller_id,total_amount,currency,status FROM marketplace_orders WHERE id=$1 FOR UPDATE') && protection.includes("SET status='DISPUTED'")],
  ['protected requests move escrow to DISPUTED', protection.includes("UPDATE marketplace_escrows SET status='DISPUTED'")],
  ['open protection cases are idempotent', protection.includes('idempotencyKey') && protection.includes('WHERE idempotency_key=$1') && protection.includes('idempotency_key TEXT NOT NULL UNIQUE')],
  ['evidence is stored against the order', transactions.includes('marketplace_order_evidence') && transactions.includes("app.post('/api/marketplace/orders/:id/evidence'")],
  ['seller alone can ship', completion.includes('only the seller can ship this order') && completion.includes("order.status !== 'PAID'")],
  ['buyer alone selects fulfillment', completion.includes('only the buyer can choose fulfillment')],
  ['delivery enters a protected inspection period', completion.includes("status='INSPECTION'") && completion.includes('inspection_deadline') && completion.includes('48 * 60 * 60 * 1000')],
  ['buyer alone can complete after inspection begins', completion.includes('only the buyer can complete this order') && completion.includes("order.status !== 'INSPECTION'")],
  ['manual completion is blocked by active disputes and protection cases', completion.includes("marketplace_order_disputes") && completion.includes("marketplace_protection_cases") && completion.includes("status IN ('OPEN','UNDER_REVIEW')") && completion.includes('active protection case or dispute')],
  ['completion consumes reserved inventory once', completion.includes("reserved_quantity=GREATEST(0,reserved_quantity-$1)") && completion.includes("status='COMPLETED'")],
  ['inspection expiry is blocked by active disputes', inspection.includes("marketplace_order_disputes") && inspection.includes("status IN ('OPEN','UNDER_REVIEW')")],
  ['inspection expiry is blocked by protection cases', inspection.includes("marketplace_protection_cases") && inspection.includes("status IN ('OPEN','UNDER_REVIEW')")],
  ['delivery exceptions use the integrated fulfillment state machine', completion.includes("['DISPATCHED->FAILED_DELIVERY', true]") && completion.includes("['IN_TRANSIT->FAILED_DELIVERY', true]") && completion.includes("['FAILED_DELIVERY->IN_TRANSIT', true]") && completion.includes("['FAILED_DELIVERY->RETURNED', true]")],
  ['failed delivery and return remain protected from active cases', completion.includes("['FAILED_DELIVERY','RETURNED'].includes(requested)") && completion.includes('active protection case or dispute')],
  ['fulfillment exceptions are written to the existing order timeline', completion.includes('recordFulfillmentEvent') && completion.includes('FULFILLMENT_${requested}')],
  ['manual cancellation is payment-pending only', transactions.includes("order.status!=='PAYMENT_PENDING'") || paymentExpiry.includes("status='PAYMENT_PENDING'")],
  ['payment-pending cancellation releases the exact reserved quantity once', paymentExpiry.includes("status='CANCELLED'") && paymentExpiry.includes('reserved_quantity=GREATEST(0,reserved_quantity-$1)') && paymentExpiry.includes('ORDER_PAYMENT_EXPIRED')],
  ['admin refund cannot race payout', resolution.includes('payoutConflict') && resolution.includes("resolution === 'BUYER'")],
  ['admin seller resolution cannot race refund', resolution.includes('refundConflict') && resolution.includes("resolution === 'SELLER'")],
  ['admin cancellation cannot cancel active money movement', resolution.includes("resolution === 'CANCEL'") && resolution.includes('payoutConflict || refundConflict')],
  ['admin authorization is explicit', resolution.includes("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1") && !resolution.includes('ORDER BY id ASC LIMIT 1')],
  ['payout remains buyer-completion gated', settlement.includes("order.status !== 'COMPLETED'") && settlement.includes('buyer completion is required')]
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
const failed = checks.filter(([, ok]) => !ok);
if (failed.length) process.exit(1);
console.log(`Marketplace buyer-protection lifecycle verification passed: ${checks.length}/${checks.length}`);
