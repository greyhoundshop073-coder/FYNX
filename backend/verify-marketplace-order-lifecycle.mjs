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
  ['buyer lifecycle UI validates fulfillment choice', lifecycle.includes('Choose delivery or pickup before continuing.')],
  ['buyer delivery UI validates required contact and address fields', lifecycle.includes('Complete all delivery address and contact fields.')],
  ['buyer lifecycle UI limits buyer note length', lifecycle.includes('note.trim().take(500)')],
  ['buyer lifecycle UI exposes delivery confirmation', lifecycle.includes('Confirm received')],
  ['buyer lifecycle UI exposes inspection completion', lifecycle.includes('Complete order')],
  ['buyer order UI exposes problem reporting', panel.includes('Report problem')],
  ['protection dispute endpoint exists', protection.includes('/api/marketplace/protection/order/:id/dispute')],
  ['existing Android dispute route is compatible', protection.includes('/api/marketplace/orders/:id/disputes')],
  ['Android dispute client targets compatibility route', client.includes('/api/marketplace/orders/$id/disputes')],
  ['marketplace orders remain protected before completion', completion.includes("order.status !== 'INSPECTION'") && completion.includes("status='COMPLETED'")],
  ['buyer lifecycle client stays server-authoritative', lifecycle.includes('FynxRemoteSocialClient') && !/marketplace_orders.*UPDATE|UPDATE.*marketplace_orders/i.test(lifecycle)],
  ['seller lifecycle client stays server-authoritative', seller.includes('FynxBackendClient') && !/marketplace_orders.*UPDATE|UPDATE.*marketplace_orders/i.test(seller)],
  ['checkout UI does not expose provider secret material', panel.includes('password or payment secret is never requested here')],
  ['payment completion remains backend-verified', panel.includes('verifyMarketplacePayment(context, payment?.reference.orEmpty())')],
  ['protected-order UI keeps funds gated until completion', panel.includes('funds remain protected until the order reaches the appropriate completion state')],
  ['failed delivery does not bypass protection', completion.includes("FAILED_DELIVERY") && completion.includes('protection')],
  ['returned orders do not bypass protection', completion.includes("RETURNED") && completion.includes('protection')],
  ['seller payout is gated by completed order state', completion.includes("order.status !== 'COMPLETED'") && seller.includes('/api/marketplace/settlement/release/${order.id}')],
  ['seller payout is provider-verified before paid state', seller.includes('verify') && seller.includes('marking it paid')],
  ['settlement details remain read-only in seller UI', seller.includes('/api/marketplace/settlement/order/${order.id}') && !/UPDATE\s+marketplace_orders|UPDATE\s+marketplace_listings/i.test(seller)],
  ['buyer and seller lifecycle use backend APIs rather than local order mutation', lifecycle.includes('FynxRemoteSocialClient') && seller.includes('FynxBackendClient') && !/marketplace_orders.*UPDATE|UPDATE.*marketplace_orders/i.test(lifecycle + seller)]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace order lifecycle guard FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}

console.log(`Marketplace order lifecycle guard GREEN (${checks.length} checks)`);
