import fs from 'node:fs';

const completion = fs.readFileSync(new URL('./marketplaceCompletion.js', import.meta.url), 'utf8');
const timeline = fs.readFileSync(new URL('./marketplaceShippingTimeline.js', import.meta.url), 'utf8');

const checks = [
  ['shipping completion records order events', completion.includes('marketplace_order_events')],
  ['seller shipping records tracking reference', completion.includes('trackingReference')],
  ['protected timeline route exists', timeline.includes("/api/marketplace/orders/:id/timeline")],
  ['timeline is authenticated', timeline.includes("app.get('/api/marketplace/orders/:id/timeline', auth")],
  ['timeline authorizes buyer or seller', timeline.includes('(o.buyer_id = $2 OR o.seller_id = $2)')],
  ['timeline orders events chronologically', timeline.includes('ORDER BY e.created_at ASC, e.id ASC')],
  ['timeline does not alter payment state', !timeline.includes('UPDATE marketplace_orders')],
  ['timeline is registered by the existing marketplace completion route', completion.includes("registerMarketplaceShippingTimeline({ app, pool, auth });")],
  ['fulfillment progression is integrated in marketplace completion', completion.includes("/api/marketplace/orders/:id/fulfillment-progress")],
  ['delivery states are server-authoritative', ['PREPARING','DISPATCHED','IN_TRANSIT','DELIVERED','FAILED_DELIVERY','RETURNED'].every((state) => completion.includes(state))],
  ['pickup states are server-authoritative', ['READY_FOR_PICKUP','PICKUP_COMPLETED'].every((state) => completion.includes(state))],
  ['fulfillment status is stored on the existing marketplace order', completion.includes('fulfillment_status') && completion.includes('fulfillment_status_updated_at')],
  ['existing shipping endpoint advances fulfillment state', completion.includes("fulfillment_status='DISPATCHED'")],
  ['existing pickup endpoint advances fulfillment state', completion.includes("fulfillment_status='PICKUP_COMPLETED'")],
  ['buyer receipt moves fulfillment into inspection', completion.includes("fulfillment_status='INSPECTION'")],
  ['completion moves fulfillment into completed', completion.includes("fulfillment_status='COMPLETED'")],
  ['active protection blocks exception transitions', completion.includes("FAILED_DELIVERY','RETURNED") && completion.includes('hasActiveProtectionCase')],
  ['duplicate standalone fulfillment state route is absent', !completion.includes('/api/marketplace/orders/:id/fulfillment-state')],
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (checks.some(([, ok]) => !ok)) process.exit(1);
