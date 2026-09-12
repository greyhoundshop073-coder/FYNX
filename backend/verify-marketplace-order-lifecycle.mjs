import fs from 'node:fs';

const read = (path) => fs.readFileSync(new URL(path, import.meta.url), 'utf8');
const completion = read('./marketplaceCompletion.js');
const protection = read('./marketplaceProtection.js');
const client = read('../app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt');
const lifecycle = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt');
const seller = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt');
const panel = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt');

const checks = [
  ['buyer fulfillment route exists', completion.includes("/api/marketplace/orders/:id/fulfillment")],
  ['seller shipping route exists', completion.includes("/api/marketplace/orders/:id/ship")],
  ['seller pickup handover route exists', completion.includes("/api/marketplace/orders/:id/pickup-handover")],
  ['delivery shipping and pickup handover share protection gating', completion.includes("marketplace_order_disputes WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')") && completion.includes("marketplace_protection_cases WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')")],
  ['buyer delivery confirmation exists', completion.includes("/api/marketplace/orders/:id/confirm-delivery")],
  ['buyer pickup receipt requires seller handover', completion.includes("order.fulfillment_method === 'PICKUP' && !order.pickup_handover_at")],
  ['buyer inspection completion exists', completion.includes("/api/marketplace/orders/:id/complete")],
  ['inspection window is enforced', completion.includes('48 * 60 * 60 * 1000')],
  ['seller delivery tracking UI uses shipping route', seller.includes('/api/marketplace/orders/${order.id}/ship')],
  ['seller pickup UI uses dedicated handover route', seller.includes('/api/marketplace/orders/${order.id}/pickup-handover') && seller.includes('Confirm pickup handover')],
  ['buyer lifecycle UI exposes fulfillment', lifecycle.includes('Choose fulfillment')],
  ['buyer lifecycle UI exposes delivery confirmation', lifecycle.includes('Confirm received')],
  ['buyer lifecycle UI exposes inspection completion', lifecycle.includes('Complete order')],
  ['buyer order UI exposes problem reporting', panel.includes('Report problem')],
  ['protection dispute endpoint exists', protection.includes('/api/marketplace/protection/order/:id/dispute')],
  ['existing Android dispute route is compatible', protection.includes('/api/marketplace/orders/:id/disputes')],
  ['Android dispute client targets compatibility route', client.includes('/api/marketplace/orders/$id/disputes')],
  ['marketplace orders remain protected before completion', completion.includes("order.status !== 'INSPECTION'") && completion.includes("status='COMPLETED'")]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace order lifecycle guard FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}

console.log(`Marketplace order lifecycle guard GREEN (${checks.length} checks)`);
