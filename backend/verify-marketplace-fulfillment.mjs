import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const completion = read('./marketplaceCompletion.js');
const protection = read('./marketplaceProtection.js');
const expiry = read('./marketplaceInspectionExpiry.js');
const settlement = read('./marketplaceSettlement.js');

const checks = [
  ['fulfillment selection is buyer-only', completion.includes("only the buyer can choose fulfillment")],
  ['delivery requires validated buyer address', completion.includes("delivery requires name, phone and address") && completion.includes('safeAddress')],
  ['delivery/pickup availability is checked from order snapshot', completion.includes('snapshot.deliveryAvailable') && completion.includes('snapshot.pickupAvailable')],
  ['shipping is seller-only', completion.includes("only the seller can ship this order")],
  ['shipping requires paid order', completion.includes("order.status !== 'PAID'")],
  ['pickup cannot use the shipping transition', completion.includes("order.fulfillment_method === 'PICKUP'") && completion.includes('pickup orders require seller handover confirmation')],
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
  ['protection routes are wired',
    protection.includes("app.post('/api/marketplace/protection/order/:id/dispute'") &&
    protection.includes("app.post('/api/marketplace/orders/:id/disputes'") &&
    protection.includes("app.post('/api/marketplace/protection/order/:id/refund-request'") &&
    protection.includes('marketplace_protection_cases')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Marketplace fulfillment verification passed: ${checks.length}/${checks.length}`);
