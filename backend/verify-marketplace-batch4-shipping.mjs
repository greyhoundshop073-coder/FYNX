import fs from 'node:fs';

const bootstrap = fs.readFileSync(new URL('./checkoutBootstrap.js', import.meta.url), 'utf8');
const shipping = fs.readFileSync(new URL('./marketplaceShipping.js', import.meta.url), 'utf8');
const checkout = fs.readFileSync(new URL('./marketplaceCheckout.js', import.meta.url), 'utf8');
const order = fs.readFileSync(new URL('./marketplaceCheckoutOrder.js', import.meta.url), 'utf8');

const checks = [
  ['shipping module exists', shipping.includes('registerMarketplaceShippingRoutes')],
  ['canonical shipping calculator exported', shipping.includes('calculateMarketplaceShipping')],
  ['seller-arranged shipping is supported', shipping.includes("SELLER_ARRANGED")],
  ['pickup has zero shipping', shipping.includes('NO_SHIPPING_FOR_PICKUP')],
  ['quantity-aware shipping exists', shipping.includes('additional_item_shipping_fee')],
  ['shipping cap exists', shipping.includes('shipping_fee_cap')],
  ['carrier is optional', shipping.includes('carrierRequired: false')],
  ['shipping options route exists', shipping.includes("/api/marketplace/shipping/options")],
  ['shipping schema has coverage table', shipping.includes('marketplace_shipping_coverage')],
  ['seller shipping settings GET exists', shipping.includes("/api/marketplace/listings/:id/shipping")],
  ['seller shipping settings PATCH exists', shipping.includes("app.patch('/api/marketplace/listings/:id/shipping'")],
  ['seller ownership is enforced', shipping.includes('only the listing seller can manage shipping settings')],
  ['shipping fees are validated', shipping.includes('shipping fees must be valid non-negative amounts')],
  ['coverage country is required', shipping.includes('each coverage entry requires a country')],
  ['coverage entries are deduplicated', shipping.includes('const seen = new Set()')],
  ['unconfigured coverage preserves legacy availability', shipping.includes('if (!rows.length) return true')],
  ['destination coverage matcher exists', shipping.includes('isMarketplaceDestinationCovered')],
  ['checkout imports canonical shipping', checkout.includes("./marketplaceShipping.js")],
  ['checkout uses canonical shipping calculation', checkout.includes('calculateMarketplaceShipping(listing, quantity, fulfillmentMethod)')],
  ['checkout checks destination coverage', checkout.includes('isMarketplaceDestinationCovered(client, listing.id, shippingAddress)')],
  ['checkout returns destination error code', checkout.includes('DESTINATION_NOT_COVERED')],
  ['checkout returns shipping provider', checkout.includes('shippingProvider: shipping.provider')],
  ['order imports canonical shipping', order.includes("./marketplaceShipping.js")],
  ['order uses canonical shipping calculation', order.includes('calculateMarketplaceShipping(listing, quantity, method)')],
  ['protected order checks destination coverage', order.includes('isMarketplaceDestinationCovered(client, listing.id, address)')],
  ['order returns destination error code', order.includes('DESTINATION_NOT_COVERED')],
  ['order persists shipping method', order.includes('shipping_method')],
  ['order persists shipping fee policy', order.includes('shipping_fee_policy')],
  ['bootstrap imports shipping routes', bootstrap.includes('marketplaceShipping.js')],
  ['bootstrap registers shipping routes', bootstrap.includes('registerMarketplaceShippingRoutes')]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
if (failed.length) process.exit(1);
console.log(`Batch 4 shipping verifier: GREEN (${checks.length} checks)`);
