import fs from 'node:fs';
const timeline = fs.readFileSync(new URL('./marketplaceShippingTimeline.js', import.meta.url), 'utf8');
const completion = fs.readFileSync(new URL('./marketplaceCompletion.js', import.meta.url), 'utf8');
const checks = [
 ['timeline route exists', timeline.includes("/orders/:id/timeline")],
 ['authenticated route', timeline.includes('requireAuth')],
 ['buyer or seller authorization', timeline.includes('o.buyer_id = $2 OR o.seller_id = $2')],
 ['chronological events', timeline.includes('ORDER BY e.created_at ASC, e.id ASC')],
 ['shipping events are recorded', completion.includes('marketplace_order_events')],
 ['tracking is retained', completion.includes('trackingReference')],
 ['timeline has no payment mutation', !timeline.includes('UPDATE marketplace_orders')],
];
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (checks.some(([, ok]) => !ok)) process.exit(1);
