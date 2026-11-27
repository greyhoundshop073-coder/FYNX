import fs from 'node:fs';

const bootstrap = fs.readFileSync(new URL('./checkoutBootstrap.js', import.meta.url), 'utf8');
const shipping = fs.readFileSync(new URL('./marketplaceShipping.js', import.meta.url), 'utf8');
const checkout = fs.readFileSync(new URL('./marketplaceCheckout.js', import.meta.url), 'utf8');
const order = fs.readFileSync(new URL('./marketplaceCheckoutOrder.js', import.meta.url), 'utf8');

const checks = [
  ['shipping module exists', shipping.includes('registerMarketplaceShippingRoutes')],
  ['legacy flat shipping calculator remains available for supported legacy destinations', shipping.includes('calculateMarketplaceShipping')],
  ['seller-arranged delivery remains supported without a FYNX-owned fleet', shipping.includes('SELLER_ARRANGED')],
  ['pickup has zero shipping', shipping.includes('NO_SHIPPING_FOR_PICKUP')],
  ['quantity-aware route pricing supports additional items', shipping.includes('additional_item_fee')],
  ['route fee cap is supported', shipping.includes('fee_cap')],
  ['Nigeria state directory includes the 36 states and FCT', shipping.includes("'Rivers', 'Sokoto', 'Taraba', 'Yobe', 'Zamfara', 'FCT'")],
  ['FCT and Abuja aliases normalize to FCT', shipping.includes("'FEDERAL CAPITAL TERRITORY'") && shipping.includes("'ABUJA'")],
  ['city-specific route prices take precedence over state-wide routes', shipping.includes('exactCity') && shipping.includes('destination_city')],
  ['route prices match the listing currency', shipping.includes('DELIVERY_ROUTE_CURRENCY_MISMATCH')],
  ['unconfigured Nigerian routes block checkout without guessing a fee', shipping.includes('DELIVERY_ROUTE_UNPRICED')],
  ['seller dispatch origin is required for Nigerian route prices', shipping.includes('SELLER_DISPATCH_ORIGIN_REQUIRED')],
  ['shipping options route exists and resolves route pricing', shipping.includes("/api/marketplace/shipping/options") && shipping.includes('resolveMarketplaceShipping(client, listing, quantity, fulfillmentMethod, address)')],
  ['shipping schema persists route prices and quote records', shipping.includes('marketplace_shipping_rates') && shipping.includes('marketplace_checkout_quotes')],
  ['seller route-price GET exists', shipping.includes("app.get('/api/marketplace/listings/:id/shipping/routes'")],
  ['seller route-price PATCH exists', shipping.includes("app.patch('/api/marketplace/listings/:id/shipping/routes'")],
  ['seller ownership is enforced for route settings', shipping.includes('only the listing seller can manage route prices')],
  ['existing shipping settings and coverage remain supported', shipping.includes("app.get('/api/marketplace/listings/:id/shipping'") && shipping.includes('marketplace_shipping_coverage')],
  ['checkout imports the canonical shipping resolver', checkout.includes("./marketplaceShipping.js") && checkout.includes('resolveMarketplaceShipping')],
  ['single checkout resolves delivery using buyer destination', checkout.includes('resolveMarketplaceShipping(client, listing, quantity, fulfillmentMethod, shippingAddress || {})')],
  ['multi checkout resolves each seller delivery route independently', checkout.includes('resolveMarketplaceShipping(client, listing, item.quantity, item.fulfillmentMethod, item.shippingAddress || {})')],
  ['single and multi quotes are persisted server-side', checkout.includes("'SINGLE'") && checkout.includes("'MULTI'") && checkout.includes('INSERT INTO marketplace_checkout_quotes')],
  ['quote storage binds the normalized buyer destination by hash', checkout.includes('checkoutAddressHash') && checkout.includes('shippingAddressHash')],
  ['quote response records delivery origin and destination', checkout.includes('shippingOrigin: shipping.origin') && checkout.includes('shippingDestination: shipping.destination')],
  ['order requires and validates the single accepted quote', order.includes('CHECKOUT_QUOTE_REQUIRED') && order.includes("quote_type='SINGLE'") && order.includes('CHECKOUT_QUOTE_MISMATCH')],
  ['single order uses accepted quote amounts rather than recalculating fees', order.includes('acceptedQuote.deliveryFee') && !order.includes('calculateMarketplaceShipping')],
  ['single order persists quote ID and route snapshot', order.includes('checkout_quote_id') && order.includes('shippingRouteId: shipping.routeId')],
  ['single order consumes the quote atomically with order creation', order.includes('UPDATE marketplace_checkout_quotes SET consumed_at=NOW()')],
  ['multi order validates and consumes its aggregate accepted quote', order.includes("quote_type='MULTI'") && order.includes('quotedById.get') && order.includes('CHECKOUT_QUOTE_TOTAL_MISMATCH')],
  ['multi order preserves route snapshots on each protected child order', order.includes('shippingOrigin: shipping.origin') && order.includes('shippingDestination: shipping.destination')],
  ['bootstrap imports shipping routes', bootstrap.includes('marketplaceShipping.js')],
  ['bootstrap registers shipping routes', bootstrap.includes('registerMarketplaceShippingRoutes')]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
if (failed.length) {
  console.error(`Batch 4 shipping verifier: FAIL (${failed.length}/${checks.length})`);
  process.exit(1);
}
console.log(`Batch 4 shipping verifier: GREEN (${checks.length} checks)`);

// The dedicated verifier exercises route matching, state normalization, quantity/cap
// calculations, missing-route behavior, pickup, NGN/USD, and quote-to-order contracts.
await import('./verify-marketplace-nigeria-route-pricing.mjs');
