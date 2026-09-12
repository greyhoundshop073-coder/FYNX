import fs from 'node:fs';

const completion = fs.readFileSync(new URL('./marketplaceCompletion.js', import.meta.url), 'utf8');
const timeline = fs.readFileSync(new URL('./marketplaceShippingTimeline.js', import.meta.url), 'utf8');
const shipping = fs.readFileSync(new URL('./marketplaceShipping.js', import.meta.url), 'utf8');
const sellerOrders = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt', import.meta.url), 'utf8');
const buyerTimeline = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderTimeline.kt', import.meta.url), 'utf8');
const transactions = fs.readFileSync(new URL('./marketplaceTransactions.js', import.meta.url), 'utf8');
const paymentExpiry = fs.readFileSync(new URL('./marketplacePaymentExpiry.js', import.meta.url), 'utf8');

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
  ['delivery-only shipping rejects pickup orders', completion.includes("order.fulfillment_method === 'PICKUP'") && completion.includes('pickup orders require seller handover confirmation')],
  ['pickup-only handover rejects delivery orders', completion.includes("order.fulfillment_method !== 'PICKUP'") && completion.includes('pickup handover is only for pickup orders')],
  ['buyer receipt requires pickup handover for pickup orders', completion.includes("order.fulfillment_method === 'PICKUP' && !order.pickup_handover_at")],
  ['fulfillment progress uses method-specific transition maps', completion.includes('canAdvanceFulfillment(current, requested, order.fulfillment_method)')],
  ['fulfillment status is stored on the existing marketplace order', completion.includes('fulfillment_status') && completion.includes('fulfillment_status_updated_at')],
  ['existing shipping endpoint advances fulfillment state', completion.includes("fulfillment_status='DISPATCHED'")],
  ['existing pickup endpoint advances fulfillment state', completion.includes("fulfillment_status='PICKUP_COMPLETED'")],
  ['buyer receipt moves fulfillment into inspection', completion.includes("fulfillment_status='INSPECTION'")],
  ['completion moves fulfillment into completed', completion.includes("fulfillment_status='COMPLETED'")],
  ['active protection blocks exception transitions', completion.includes("FAILED_DELIVERY','RETURNED") && completion.includes('hasActiveProtectionCase')],
  ['shipping and pickup actions block active protection cases', completion.includes("if (await hasActiveProtectionCase(client, id)")],
  ['delivery destination is revalidated when fulfillment is selected', completion.includes('isMarketplaceDestinationCovered(client, order.listing_id, safeAddress)') && completion.includes('DESTINATION_NOT_COVERED')],
  ['duplicate standalone fulfillment state route is absent', !completion.includes('/api/marketplace/orders/:id/fulfillment-state')],
  ['seller shipping settings expose method and fee policy', shipping.includes('shipping_method') && shipping.includes('additional_item_shipping_fee') && shipping.includes('shipping_fee_cap')],
  ['seller shipping settings support country/state/city coverage', shipping.includes('marketplace_shipping_coverage') && shipping.includes('country,state,city')],
  ['checkout enforces destination coverage', completion.includes('isMarketplaceDestinationCovered')],
  ['seller fulfillment controls call the integrated progress route', sellerOrders.includes('/api/marketplace/orders/${order.id}/fulfillment-progress')],
  ['buyer timeline loads the existing protected timeline route', buyerTimeline.includes('/api/marketplace/orders/$orderId/timeline')],
  ['buyer timeline explains failed delivery protection', buyerTimeline.includes('protected payment is not released')],
  ['payment-pending cancellation releases reserved inventory', transactions.includes("order.status!=='PAYMENT_PENDING'") && paymentExpiry.includes('reserved_quantity=GREATEST(0,reserved_quantity-$1)')],
  ['no specific carrier API is embedded in the shipping foundation', !shipping.includes('fedex') && !shipping.includes('ups.com') && !shipping.includes('dhl.com') && !shipping.includes('shippo.com')]
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (checks.some(([, ok]) => !ok)) process.exit(1);
console.log(`Marketplace Batch 4 shipping verification passed: ${checks.length}/${checks.length}`);
