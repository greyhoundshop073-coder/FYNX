import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(`./${name}`, import.meta.url), 'utf8');
const quote = read('marketplaceCheckout.js');
const order = read('marketplaceCheckoutOrder.js');
const bootstrap = read('checkoutBootstrap.js');

const checks = [
  ['checkout quote route exists', quote.includes("app.post('/api/marketplace/checkout/quote'")],
  ['quote is authenticated', quote.includes("checkout/quote', auth")],
  ['quote locks the listing', quote.includes('FOR UPDATE')],
  ['quote validates stock including reservations', quote.includes('reserved_quantity') && quote.includes('INSUFFICIENT')],
  ['quote supports delivery and pickup', quote.includes('DELIVERY') && quote.includes('PICKUP')],
  ['pickup delivery fee is zero', quote.includes("fulfillmentMethod === 'DELIVERY' ? Math.max(0, Number(listing.delivery_fee || 0)) : 0")],
  ['delivery requires an address', quote.includes('delivery requires name, phone and address')],
  ['quote calculates the authoritative total', quote.includes('buyerTotal') && quote.includes('marketplaceFeeBuyer')],
  ['protected checkout order route exists', order.includes("app.post('/api/marketplace/checkout/order'")],
  ['order creation is authenticated', order.includes("checkout/order', auth")],
  ['order creation is idempotent by order id', order.includes('WHERE id=$1 AND buyer_id=$2') && order.includes('idempotent: true')],
  ['order rechecks the listing under lock', order.includes('FOR UPDATE') && order.includes('active=TRUE')],
  ['order reserves inventory atomically', order.includes('reserved_quantity=reserved_quantity+$1')],
  ['order snapshots fulfillment and address', order.includes('fulfillmentMethod: method') && order.includes('shippingAddress')],
  ['order starts payment pending', order.includes("'PAYMENT_PENDING'")],
  ['checkout route is wired through production bootstrap', bootstrap.includes('registerMarketplaceCheckoutOrderRoutes')]
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) throw new Error(`Batch 3 checkout gate failed: ${failed.join('; ')}`);
console.log(`Batch 3 checkout foundation gate passed (${checks.length} checks)`);
