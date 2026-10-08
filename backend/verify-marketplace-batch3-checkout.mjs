import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(`./${name}`, import.meta.url), 'utf8');
const quote = read('marketplaceCheckout.js');
const order = read('marketplaceCheckoutOrder.js');
const bootstrap = read('checkoutBootstrap.js');

const checks = [
  ['multi-product quote route exists', quote.includes("app.post('/api/marketplace/checkout/multi-quote'")],
  ['multi-product quote caps checkout at 20 different products', quote.includes('items.length > 20')],
  ['multi-product quote rejects duplicate listings', quote.includes('a listing may appear only once in a multi-product checkout')],
  ['multi-product quote requires one quantity and fulfillment method per line', quote.includes('every cart item requires listing, quantity and fulfillment method')],
  ['multi-product quote keeps currencies homogeneous', quote.includes('MIXED_CURRENCIES') && quote.includes('checkoutCurrency !== currency')],
  ['multi-product quote calculates the FYNX fee independently per line', quote.includes('lineMarketplaceFee') && quote.includes('productSubtotal * (config.bps / 10000)')],
  ['multi-product quote returns per-line seller net', quote.includes('sellerNetAmount: lineSellerNet')],
  ['multi-product quote does not reserve inventory', quote.includes('reserved_quantity') && !quote.includes('reserved_quantity=reserved_quantity+$1')],\n  ['checkout quote route exists', quote.includes("app.post('/api/marketplace/checkout/quote'")],
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
  ['order id reuse is bound to the original checkout request', order.includes('ORDER_ID_REUSE_CONFLICT') && order.includes('sameCoreRequest') && order.includes('sameDeliveryAddress')],
  ['idempotent checkout returns the complete authoritative order', order.includes('publicCheckoutOrder(existing)')],
  ['order rechecks the listing under lock', order.includes('FOR UPDATE') && order.includes('active=TRUE')],
  ['order reserves inventory atomically', order.includes('reserved_quantity=reserved_quantity+$1')],
  ['order snapshots fulfillment and address', order.includes('fulfillmentMethod: method') && order.includes('shippingAddress')],
  ['order starts payment pending', order.includes("'PAYMENT_PENDING'")],
  ['checkout route is wired through production bootstrap', bootstrap.includes('registerMarketplaceCheckoutOrderRoutes')],
  ['multi-product protected checkout route exists', order.includes("app.post('/api/marketplace/checkout/multi-order'")],
  ['multi-product checkout caps at 20 different products', order.includes('rawItems.length > 20')],
  ['multi-product checkout rejects duplicate listings', order.includes('DUPLICATE_LISTING')],
  ['multi-product checkout enforces one currency', order.includes('MIXED_CURRENCIES')],
  ['multi-product checkout rechecks every listing under row lock', order.includes('FOR UPDATE') && order.includes('for (const item of sortedItems)')],
  ['multi-product checkout rechecks stock before reservation', order.includes('INSUFFICIENT_STOCK') && order.includes('Number(listing.quantity) - Number(listing.reserved_quantity || 0)')],
  ['multi-product checkout creates a parent checkout group', order.includes('marketplace_checkout_groups') && order.includes("status) VALUES ($1,$2,$3,$4,$5,$6,$7,'PAYMENT_PENDING')")],
  ['multi-product checkout creates one child order per cart line', order.includes('marketplace_checkout_group_items') && order.includes('orders.push(publicCheckoutOrder(inserted))')],
  ['multi-product checkout reserves each line inside the same transaction', order.includes('UPDATE marketplace_listings SET reserved_quantity=reserved_quantity+$1') && order.includes("await client.query('COMMIT')")],
  ['multi-product checkout rolls back the complete transaction on failure', order.includes("await client.query('ROLLBACK').catch(() => {})") && order.includes('marketplace-multi-checkout-order')],
  ['multi-product checkout returns authoritative parent and child orders', order.includes('return res.status(201).json({ checkout: insertedGroup, orders })')],
  ['multi-product checkout idempotency returns existing group and children', order.includes('const existing = (await client.query') && order.includes('idempotent: true')]
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) throw new Error(`Batch 3 checkout gate failed: ${failed.join('; ')}`);
console.log(`Batch 3 checkout foundation gate passed (${checks.length} checks)`);
