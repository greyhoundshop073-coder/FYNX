import fs from 'node:fs';

const read = (path) => fs.readFileSync(new URL(path, import.meta.url), 'utf8');

const checkout = read('./marketplaceCheckout.js');
const checkoutOrder = read('./marketplaceCheckoutOrder.js');
const payment = read('./marketplaceReputation.js');
const webhook = read('./marketplacePaystackWebhook.js');
const completion = read('./marketplaceCompletion.js');
const protection = read('./marketplaceProtection.js');
const settlement = read('./marketplaceSettlement.js');
const worker = read('./marketplaceSettlementWorker.js');
const panel = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt');
const singleCheckoutUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt');
const paymentUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplacePayments.kt');
const lifecycle = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt');
const seller = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt');
const socialRoutes = read('./socialRoutes.js');

const checks = [
  ['Marketplace text search binds every search field as a PostgreSQL parameter', ['l.title','l.description',"COALESCE(l.store_name, '')", "COALESCE(l.location, '')", "COALESCE(l.category, '')",'u.username','u.display_name'].every(column => socialRoutes.includes(column + ' ILIKE 
  ['multi-product quote exists', checkout.includes("/api/marketplace/checkout/multi-quote")],
  ['multi-product order creation exists', checkoutOrder.includes("/api/marketplace/checkout/multi-order")],
  ['multi-product payment initializes one checkout-group transaction', payment.includes("/api/marketplace/checkout-groups/:id/payment") && payment.includes("FYNX-CHECKOUT-")],
  ['multi-product payment verification is present', payment.includes("/api/marketplace/checkout-groups/verify/:reference")],
  ['Paystack webhook reconciles checkout groups', webhook.includes("FYNX-CHECKOUT-") && webhook.includes("marketplace_checkout_groups")],
  ['payment verification marks child orders paid', payment.includes("PAYMENT_CONFIRMED") && payment.includes("status='PAID'")],
  ['multi-order reserves inventory atomically', checkoutOrder.includes("reserved_quantity") && checkoutOrder.includes("FOR UPDATE") && checkoutOrder.includes("COMMIT")],
  ['buyer fulfillment progression exists', completion.includes("/api/marketplace/orders/:id/fulfillment")],
  ['seller delivery handoff exists', completion.includes("/api/marketplace/orders/:id/ship")],
  ['seller pickup handoff exists', completion.includes("/api/marketplace/orders/:id/pickup-handover")],
  ['buyer delivery confirmation exists', completion.includes("/api/marketplace/orders/:id/confirm-delivery")],
  ['driver delivery photo is numeric, assigned-driver-owned, image-only, and stored by validated media ID', completion.includes('photo reference must be a valid uploaded media id') && completion.includes("SELECT mime_type FROM message_media WHERE id=$1 AND owner_id=$2 LIMIT 1") && completion.includes("new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])") && completion.includes('String(photoMediaId)') && completion.includes('hasPhotoReference: photoMediaId !== null')],
  ['assigned driver can upload optional photo proof while note-only delivery remains valid', seller.includes('Add delivery photo (optional)') && seller.includes('FynxProductionMessaging.uploadMedia(context, photoUri)') && seller.includes('recordMarketplaceDriverDelivery') && seller.includes('photoReference') && seller.includes('deliveryNotes[assignment.orderId].isNullOrBlank() || deliveryPhotoUris.containsKey(assignment.orderId)')],
  ['buyer inspection completion exists', completion.includes("/api/marketplace/orders/:id/complete") && completion.includes("48 * 60 * 60 * 1000")],
  ['protection can freeze the order before completion', protection.includes("status IN ('OPEN','UNDER_REVIEW')")],
  ['refund and dispute routes are available', protection.includes("/api/marketplace/protection/order/:id/dispute") && protection.includes("/api/marketplace/protection/order/:id/refund-request")],
  ['completion makes escrow payout-eligible only after protected completion', completion.includes("ORDER_COMPLETED") && completion.includes("payout: 'eligible_for_release'")],
  ['settlement release is completion-gated', settlement.includes("/api/marketplace/settlement/release/:id") && settlement.includes("order.status !== 'COMPLETED'")],
  ['payout worker verifies provider result', worker.includes("/transfer/verify/") && worker.includes("markPayoutSucceeded")],
  ['payout worker keeps operation idempotent', worker.includes("status !== 'PENDING'") && worker.includes("FOR UPDATE")],
  ['buyer UI performs server quote before multi-order creation', panel.includes("requestMultiQuote") && panel.includes("createMultiOrder")],
  ['buyer UI verifies the payment before treating checkout as paid', panel.includes("initializeMarketplaceCheckoutGroupPayment") && panel.includes("verifyMarketplaceCheckoutGroupPayment") && panel.includes("onPaid()")],
  ['unconfigured Paystack blocks new stock reservations and tells both checkout flows no charge started', paymentUi.includes('No payment was started, and your cart is unchanged') && panel.includes('marketplacePaymentFailureMessage(it)') && singleCheckoutUi.includes('marketplacePaymentFailureMessage(it)') && panel.includes('onPaid()')],
  ['buyer lifecycle remains server-authoritative', lifecycle.includes("FynxRemoteSocialClient") && !/UPDATE\s+marketplace_orders|INSERT\s+INTO\s+marketplace_orders/i.test(lifecycle)],
  ['seller lifecycle remains server-authoritative', seller.includes("FynxBackendClient") && !/UPDATE\s+marketplace_orders|INSERT\s+INTO\s+marketplace_orders/i.test(seller)]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace end-to-end verification FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}

console.log(`Marketplace end-to-end verification GREEN (${checks.length} checks)`);
 + '${params.length}')) && !socialRoutes.includes('l.title ILIKE ' + '${params.length}')],
  ['single-product checkout quote exists', checkout.includes("/api/marketplace/checkout/quote")],
  ['multi-product quote exists', checkout.includes("/api/marketplace/checkout/multi-quote")],
  ['multi-product order creation exists', checkoutOrder.includes("/api/marketplace/checkout/multi-order")],
  ['multi-product payment initializes one checkout-group transaction', payment.includes("/api/marketplace/checkout-groups/:id/payment") && payment.includes("FYNX-CHECKOUT-")],
  ['multi-product payment verification is present', payment.includes("/api/marketplace/checkout-groups/verify/:reference")],
  ['Paystack webhook reconciles checkout groups', webhook.includes("FYNX-CHECKOUT-") && webhook.includes("marketplace_checkout_groups")],
  ['payment verification marks child orders paid', payment.includes("PAYMENT_CONFIRMED") && payment.includes("status='PAID'")],
  ['multi-order reserves inventory atomically', checkoutOrder.includes("reserved_quantity") && checkoutOrder.includes("FOR UPDATE") && checkoutOrder.includes("COMMIT")],
  ['buyer fulfillment progression exists', completion.includes("/api/marketplace/orders/:id/fulfillment")],
  ['seller delivery handoff exists', completion.includes("/api/marketplace/orders/:id/ship")],
  ['seller pickup handoff exists', completion.includes("/api/marketplace/orders/:id/pickup-handover")],
  ['buyer delivery confirmation exists', completion.includes("/api/marketplace/orders/:id/confirm-delivery")],
  ['driver delivery photo is numeric, assigned-driver-owned, image-only, and stored by validated media ID', completion.includes('photo reference must be a valid uploaded media id') && completion.includes("SELECT mime_type FROM message_media WHERE id=$1 AND owner_id=$2 LIMIT 1") && completion.includes("new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])") && completion.includes('String(photoMediaId)') && completion.includes('hasPhotoReference: photoMediaId !== null')],
  ['assigned driver can upload optional photo proof while note-only delivery remains valid', seller.includes('Add delivery photo (optional)') && seller.includes('FynxProductionMessaging.uploadMedia(context, photoUri)') && seller.includes('recordMarketplaceDriverDelivery') && seller.includes('photoReference') && seller.includes('deliveryNotes[assignment.orderId].isNullOrBlank() || deliveryPhotoUris.containsKey(assignment.orderId)')],
  ['buyer inspection completion exists', completion.includes("/api/marketplace/orders/:id/complete") && completion.includes("48 * 60 * 60 * 1000")],
  ['protection can freeze the order before completion', protection.includes("status IN ('OPEN','UNDER_REVIEW')")],
  ['refund and dispute routes are available', protection.includes("/api/marketplace/protection/order/:id/dispute") && protection.includes("/api/marketplace/protection/order/:id/refund-request")],
  ['completion makes escrow payout-eligible only after protected completion', completion.includes("ORDER_COMPLETED") && completion.includes("payout: 'eligible_for_release'")],
  ['settlement release is completion-gated', settlement.includes("/api/marketplace/settlement/release/:id") && settlement.includes("order.status !== 'COMPLETED'")],
  ['payout worker verifies provider result', worker.includes("/transfer/verify/") && worker.includes("markPayoutSucceeded")],
  ['payout worker keeps operation idempotent', worker.includes("status !== 'PENDING'") && worker.includes("FOR UPDATE")],
  ['buyer UI performs server quote before multi-order creation', panel.includes("requestMultiQuote") && panel.includes("createMultiOrder")],
  ['buyer UI verifies the payment before treating checkout as paid', panel.includes("initializeMarketplaceCheckoutGroupPayment") && panel.includes("verifyMarketplaceCheckoutGroupPayment") && panel.includes("onPaid()")],
  ['unconfigured Paystack blocks new stock reservations and tells both checkout flows no charge started', paymentUi.includes('No payment was started, and your cart is unchanged') && panel.includes('marketplacePaymentFailureMessage(it)') && singleCheckoutUi.includes('marketplacePaymentFailureMessage(it)') && panel.includes('onPaid()')],
  ['buyer lifecycle remains server-authoritative', lifecycle.includes("FynxRemoteSocialClient") && !/UPDATE\s+marketplace_orders|INSERT\s+INTO\s+marketplace_orders/i.test(lifecycle)],
  ['seller lifecycle remains server-authoritative', seller.includes("FynxBackendClient") && !/UPDATE\s+marketplace_orders|INSERT\s+INTO\s+marketplace_orders/i.test(seller)]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace end-to-end verification FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}

console.log(`Marketplace end-to-end verification GREEN (${checks.length} checks)`);
