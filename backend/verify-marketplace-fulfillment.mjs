import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const completion = read('./marketplaceCompletion.js');
const protection = read('./marketplaceProtection.js');
const transactions = read('./marketplaceTransactions.js');
const resolution = read('./marketplaceProtectionResolution.js');
const scalability = read('./scalability.js');
const socialClient = read('../app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt');
const expiry = read('./marketplaceInspectionExpiry.js');
const settlement = read('./marketplaceSettlement.js');

const checks = [
  ['fulfillment selection is buyer-only', completion.includes("only the buyer can choose fulfillment")],
  ['delivery requires validated buyer address', completion.includes("delivery requires name, phone and address") && completion.includes('safeAddress')],
  ['delivery/pickup availability is checked from order snapshot', completion.includes('snapshot.deliveryAvailable') && completion.includes('snapshot.pickupAvailable')],
  ['shipping is seller-only', completion.includes("only the seller can ship this order")],
  ['shipping requires paid order', completion.includes("order.status !== 'PAID'")],
  ['pickup cannot use the shipping transition', completion.includes("order.fulfillment_method === 'PICKUP'") && completion.includes('pickup orders require seller handover confirmation')],
  ['delivery shipping blocks active protection conflicts', completion.includes("app.post('/api/marketplace/orders/:id/ship'") && completion.includes("marketplace_order_disputes WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')") && completion.includes("marketplace_protection_cases WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')")],
  ['pickup handover route is seller-only and pickup-only', completion.includes("/api/marketplace/orders/:id/pickup-handover") && completion.includes('only the seller can confirm pickup handover') && completion.includes("order.fulfillment_method !== 'PICKUP'")],
  ['pickup handover requires paid order and blocks active protection conflicts', completion.includes('only paid orders can be handed over for pickup') && completion.includes('PICKUP_HANDOVER_CONFIRMED') && completion.includes('marketplace_protection_cases')],
  ['delivery confirmation is buyer-only', completion.includes("only the buyer can confirm delivery")],
  ['pickup delivery confirmation requires seller handover', completion.includes("order.fulfillment_method === 'PICKUP' && !order.pickup_handover_at")],
  ['delivery confirmation creates inspection window', completion.includes("status='INSPECTION'") && completion.includes('48 * 60 * 60 * 1000')],
  ['completion is buyer-only', completion.includes("only the buyer can complete this order")],
  ['completion requires inspection', completion.includes("order.status !== 'INSPECTION'")],
  ['active disputes/protection cases block completion', completion.includes("status IN ('OPEN','UNDER_REVIEW')") && completion.includes('marketplace_protection_cases')],
  ['completion consumes reserved inventory once', completion.includes('reserved_quantity=GREATEST(0,reserved_quantity-$1)') && completion.includes("status='INSPECTION'")],
  ['inspection expiry has a dedicated worker', expiry.includes('marketplace.inspection.expire') || expiry.includes('inspection expiry')],
  ['settlement requires completed/release-pending protection', settlement.includes("order.status !== 'COMPLETED'") || settlement.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['protection routes are wired without a competing legacy dispute handler',
    protection.includes("app.post('/api/marketplace/protection/order/:id/dispute'") &&
    !protection.includes("app.post('/api/marketplace/orders/:id/disputes'") &&
    protection.includes("app.post('/api/marketplace/protection/order/:id/refund-request'") &&
    protection.includes('marketplace_protection_cases') &&
    scalability.includes('registerMarketplaceProtectionRoutes({ app })') &&
    scalability.includes('registerMarketplaceProtectionResolutionRoutes({ app })')],
  ['Android dispute requests use the dedicated protected endpoint',
    socialClient.includes('FynxBackendClient.postJson(context, "/api/marketplace/protection/order/$id/dispute"') &&
    !socialClient.includes('FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/disputes"')],
  ['legacy dispute endpoint remains registered for older clients',
    transactions.includes("app.post('/api/marketplace/orders/:id/disputes'")],
  ['legacy open disputes reconcile into protected cases',
    resolution.includes('async function reconcileOpenDisputes') &&
    resolution.includes('marketplace_order_disputes') &&
    resolution.includes('marketplace_protection_cases') &&
    resolution.includes('dispute_id')]
];

  ['delivery driver assignment is seller-only and limited to a real delivery user', completion.includes("app.post('/api/marketplace/orders/:id/driver-assignment'") && completion.includes('only the seller can assign a delivery driver') && completion.includes('driver account not found') && completion.includes('buyer and seller cannot be assigned as the delivery driver')],
  ['assigned driver must accept before seller custody handover', completion.includes("app.post('/api/marketplace/orders/:id/driver-acceptance'") && completion.includes('only the assigned driver can accept this delivery') && completion.includes('driver must accept the assignment before handover')],
  ['seller handover records custody evidence and blocks active protection cases', completion.includes("app.post('/api/marketplace/orders/:id/driver-handover'") && completion.includes('DELIVERY_CUSTODY_HANDED_OVER') && completion.includes('HANDOVER_NOTE') && completion.includes('hasActiveProtectionCase(client, id)')],
  ['only the assigned driver can record delivery with recipient and proof evidence', completion.includes("app.post('/api/marketplace/orders/:id/driver-delivery'") && completion.includes('only the assigned driver can confirm delivery') && completion.includes('recipient name and delivery note or photo reference are required') && completion.includes('RECIPIENT_NAME') && completion.includes('PHOTO_REFERENCE')],
  ['buyer delivery confirmation waits for assigned driver evidence', completion.includes('assigned driver has not recorded delivery evidence yet') && completion.includes('SELECT delivered_at FROM marketplace_delivery_assignments')],
  ['driver assignment and evidence tables are indexed and linked to order/user records', completion.includes('CREATE TABLE IF NOT EXISTS marketplace_delivery_assignments') && completion.includes('marketplace_delivery_assignments_driver_idx') && completion.includes('CREATE TABLE IF NOT EXISTS marketplace_delivery_evidence') && completion.includes('marketplace_delivery_evidence_order_idx')],
const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Marketplace fulfillment verification passed: ${checks.length}/${checks.length}`);
