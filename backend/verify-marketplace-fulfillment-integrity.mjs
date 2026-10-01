import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const completion = read('./marketplaceCompletion.js');
const protection = read('./marketplaceProtection.js');
const expiry = read('./marketplaceInspectionExpiry.js');
const settlement = read('./marketplaceSettlement.js');
const sellerUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt');
const buyerUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt');

const checks = [
  ['seller fulfillment transitions lock the order row', completion.includes("SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE")],
  ['delivery shipping is seller-only and paid-only', completion.includes('only the seller can ship this order') && completion.includes("order.status !== 'PAID'")],
  ['pickup cannot fall through the delivery shipping transition', completion.includes("order.fulfillment_method === 'PICKUP'") && completion.includes('pickup orders require seller handover confirmation')],
  ['delivery shipping requires the buyer delivery details', completion.includes("order.fulfillment_method === 'DELIVERY' && !order.shipping_address")],
  ['pickup handover serializes on the paid state', completion.includes("WHERE id=$2 AND status='PAID' RETURNING *")],
  ['pickup handover blocks active protection conflicts', completion.includes("marketplace_order_disputes WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')") && completion.includes("marketplace_protection_cases WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')")],
  ['buyer receipt confirmation locks the order row', completion.includes("only the buyer can confirm delivery") && completion.includes("WHERE id=$2 RETURNING *")],
  ['pickup receipt cannot bypass seller handover', completion.includes("order.fulfillment_method === 'PICKUP' && !order.pickup_handover_at")],
  ['receipt confirmation enters a bounded 48-hour inspection state', completion.includes("status='INSPECTION'") && completion.includes('48 * 60 * 60 * 1000')],
  ['buyer completion is inspection-only', completion.includes("only the buyer can complete this order") && completion.includes("order.status !== 'INSPECTION'")],
  ['active protection blocks buyer completion', completion.includes("status IN ('OPEN','UNDER_REVIEW')") && completion.includes('marketplace_protection_cases')],
  ['inspection expiry has its own worker path', expiry.includes('inspection expiry') || expiry.includes('marketplace.inspection.expire')],
  ['settlement remains gated behind completed/release-pending state', settlement.includes("order.status !== 'COMPLETED'") || settlement.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['seller Android uses the dedicated pickup handover route', sellerUi.includes('/api/marketplace/orders/${order.id}/pickup-handover') && sellerUi.includes('Confirm pickup handover')],
  ['seller Android keeps delivery on the shipping route', sellerUi.includes('/api/marketplace/orders/${order.id}/ship') && sellerUi.includes('Mark dispatched')],
  ['buyer Android reaches the same receive/inspection lifecycle', buyerUi.includes('Confirm received') && buyerUi.includes('Complete order')],
  ['protection creation serializes on the order row', protection.includes('FROM marketplace_orders WHERE id=$1 FOR UPDATE')],
  ['protection changes the order to DISPUTED inside its transaction', protection.includes("UPDATE marketplace_orders SET status='DISPUTED'") && protection.includes('COMMIT')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) {
  console.error('R6-F fulfillment integrity verification FAILED');
  process.exit(1);
}
console.log(`R6-F fulfillment integrity verification GREEN (${checks.length} checks)`);
