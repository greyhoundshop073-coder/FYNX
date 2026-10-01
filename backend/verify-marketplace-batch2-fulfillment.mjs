import fs from 'node:fs';

const read = (path) => fs.readFileSync(new URL(path, import.meta.url), 'utf8');
const completion = read('./marketplaceCompletion.js');
const transactions = read('./marketplaceTransactions.js');
const expiry = read('./marketplaceInspectionExpiry.js');
const settlement = read('./marketplaceSettlement.js');
const shipping = read('./marketplaceShipping.js');
const buyerClient = read('../app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt');
const buyerUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt');
const sellerUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt');

const checks = [
  ['seller fulfillment is authenticated and order-locked', completion.includes('only the seller can update fulfillment progress') && completion.includes('WHERE id=$1 FOR UPDATE')],
  ['delivery shipping is seller-only and paid-only', completion.includes('only the seller can ship this order') && completion.includes("order.status !== 'PAID'")],
  ['delivery shipping cannot be used for pickup', completion.includes('pickup orders require seller handover confirmation')],
  ['pickup handover is seller-only and pickup-only', completion.includes('only the seller can confirm pickup handover') && completion.includes("order.fulfillment_method !== 'PICKUP'")],
  ['pickup handover is serialized and paid-only', completion.includes("WHERE id=$2 AND status='PAID' RETURNING *")],
  ['fulfillment transitions are method-specific', completion.includes('canAdvanceFulfillment(current, requested, order.fulfillment_method)') && completion.includes('DELIVERY_TRANSITIONS') && completion.includes('PICKUP_TRANSITIONS')],
  ['failed delivery and return cannot bypass active protection', completion.includes("['FAILED_DELIVERY','RETURNED'].includes(requested)") && completion.includes('hasActiveProtectionCase')],
  ['buyer receipt is buyer-only', completion.includes('only the buyer can confirm delivery')],
  ['pickup receipt requires seller handover', completion.includes('pickup handover must be confirmed by the seller first')],
  ['receipt enters a bounded inspection window', completion.includes("status='INSPECTION'") && completion.includes('48 * 60 * 60 * 1000')],
  ['buyer completion is inspection-only and protected', completion.includes('only the buyer can complete this order') && completion.includes("order.status !== 'INSPECTION'") && completion.includes('active protection case or dispute')],
  ['buyer completion requires explicit product match confirmation', completion.includes('receivedItemMatchesOrder === true') && completion.includes('quantityMatchesOrder === true') && completion.includes('ORDER_MATCH_CONFIRMATION_REQUIRED')],
  ['completion records the buyer match confirmation in the audit event', completion.includes('receivedItemMatchesOrder: true') && completion.includes('quantityMatchesOrder: true')],
  ['completion consumes reserved inventory exactly once', completion.includes("WHERE id=$1 AND status='INSPECTION' RETURNING *") && completion.includes('reserved_quantity=GREATEST(0,reserved_quantity-$1)')],
  ['unpaid cancellation releases reserved inventory', transactions.includes("order.status!=='PAYMENT_PENDING'") && transactions.includes('reserved_quantity=GREATEST(0,reserved_quantity-$1)')],
  ['inspection expiry is isolated in its own worker', expiry.includes('autoCompleteExpiredMarketplaceInspections') && expiry.includes("o.status='INSPECTION'")],
  ['inspection expiry blocks active disputes/protection cases', expiry.includes("d.status IN ('OPEN','UNDER_REVIEW')") && expiry.includes("c.status IN ('OPEN','UNDER_REVIEW')")],
  ['settlement remains completion/escrow gated', settlement.includes("order.status !== 'COMPLETED'") && settlement.includes("escrow.status !== 'RELEASE_ELIGIBLE'")],
  ['seller Android uses shipping and pickup-handover APIs', sellerUi.includes('/api/marketplace/orders/${order.id}/ship') && sellerUi.includes('/api/marketplace/orders/${order.id}/pickup-handover')],
  ['buyer Android requires product/quantity confirmation before completion', buyerClient.includes('receivedItemMatchesOrder') && buyerClient.includes('quantityMatchesOrder') && buyerUi.includes('confirmOrderMatch') && buyerUi.includes('I confirm the received item and quantity match my order.')],
  ['buyer Android keeps completion server-authoritative', buyerClient.includes('/api/marketplace/orders/$id/complete')],
  ['shipping timeline remains integrated with marketplace completion', completion.includes('registerMarketplaceShippingTimeline({ app, pool, auth });') && shipping.includes('/api/marketplace')],
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) throw new Error(`Marketplace Batch 2 fulfillment gate failed: ${failed.join('; ')}`);
console.log(`Marketplace Batch 2 fulfillment gate GREEN (${checks.length} checks)`);