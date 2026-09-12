import fs from 'node:fs';

const completion = fs.readFileSync(new URL('./marketplaceCompletion.js', import.meta.url), 'utf8');
const timeline = fs.readFileSync(new URL('./marketplaceShippingTimeline.js', import.meta.url), 'utf8');
const server = fs.readFileSync(new URL('./server.js', import.meta.url), 'utf8');

const checks = [
  ['shipping completion records order events', completion.includes('marketplace_order_events')],
  ['seller shipping records tracking reference', completion.includes('trackingReference')],
  ['protected timeline route exists', timeline.includes("/api/marketplace/orders/:id/timeline")],
  ['timeline uses shared authenticated route contract', timeline.includes('auth, async (req, res)')],
  ['timeline authorizes buyer or seller', timeline.includes('(o.buyer_id = $2 OR o.seller_id = $2)')],
  ['timeline orders events chronologically', timeline.includes('ORDER BY e.created_at ASC, e.id ASC')],
  ['timeline does not alter payment state', !timeline.includes('UPDATE marketplace_orders')],
  ['timeline registration is wired into the backend composition root', server.includes('registerMarketplaceShippingTimeline')],
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (checks.some(([, ok]) => !ok)) process.exit(1);
