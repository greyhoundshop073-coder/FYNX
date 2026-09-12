import fs from 'node:fs';

const state = fs.readFileSync(new URL('./marketplaceFulfillmentState.js', import.meta.url), 'utf8');
const completion = fs.readFileSync(new URL('./marketplaceCompletion.js', import.meta.url), 'utf8');

const required = [
  'PREPARING',
  'DISPATCHED',
  'IN_TRANSIT',
  'DELIVERED',
  'READY_FOR_PICKUP',
  'PICKUP_COMPLETED',
  'FAILED_DELIVERY',
  'RETURNED',
  'canTransitionMarketplaceFulfillment',
  'fulfillment_status',
  '/api/marketplace/orders/:id/fulfillment-state'
];

for (const token of required) {
  if (!state.includes(token)) throw new Error(`fulfillment state guard missing: ${token}`);
}

if (!completion.includes("registerMarketplaceFulfillmentStateRoutes({ app, pool, auth });")) {
  throw new Error('fulfillment state routes are not registered by marketplace completion');
}

if (!completion.includes('fulfillmentStatus: row.fulfillment_status || null')) {
  throw new Error('seller order response does not expose fulfillment status');
}

if (state.includes("UPDATE marketplace_orders SET status='COMPLETED'") || state.includes("UPDATE marketplace_orders SET status='PAID'")) {
  throw new Error('fulfillment state module must not directly rewrite protected order/payment status');
}

console.log('Marketplace fulfillment state verification: GREEN');
