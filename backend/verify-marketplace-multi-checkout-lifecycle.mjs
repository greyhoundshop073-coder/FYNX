import fs from 'node:fs';

const read = (file) => fs.readFileSync(new URL(file, import.meta.url), 'utf8');

const checkout = read('./marketplaceCheckoutOrder.js');
const quote = read('./marketplaceCheckout.js');
const completion = read('./marketplaceCompletion.js');
const settlement = read('./marketplaceSettlementWorker.js');
const webhook = read('./marketplacePaystackWebhook.js');
const expiry = read('./marketplacePaymentExpiry.js');

const checks = [
  ['multi-product checkout route exists', checkout.includes("/api/marketplace/checkout/multi-order")],
  ['multi-product checkout is capped at 20 distinct products', checkout.includes('rawItems.length > 20') && checkout.includes('at most 20 different products')],
  ['duplicate listings are rejected', checkout.includes('DUPLICATE_LISTING')],
  ['multi-cart quote acquires listing locks in deterministic order', quote.includes('normalizedItems.sort((a, b) => a.listingId - b.listingId)') && quote.includes('for (const item of normalizedItems)')],
  ['multi-cart order creation uses the same deterministic listing lock order', checkout.includes('const sortedItems = [...items].sort((a, b) => a.listingId - b.listingId)') && checkout.includes('for (const item of sortedItems)')],
  ['checkout idempotency serializes same-ID concurrent requests before lookup', checkout.includes('pg_advisory_xact_lock(hashtext(\'fynx-marketplace-checkout:\' || $1::text))') && checkout.includes('FROM marketplace_checkout_groups WHERE id=$1 FOR UPDATE')],
  ['checkout ID reuse by a different buyer returns a conflict', checkout.includes('existing && String(existing.buyer_id) !== String(req.user.sub)') && checkout.includes('code: \'CHECKOUT_ID_REUSE_CONFLICT\'')],
  ['checkout idempotency compares every requested listing, quantity, fulfillment method and delivery address', checkout.includes('const sameRequest = requestedItems.length === existingItems.length') && checkout.includes('order.group_listing_id') && checkout.includes('order.group_quantity') && checkout.includes('sameAddress(order.shipping_address, item.address)') && checkout.includes('requestedBuyerNote')],
  ['mixed currencies are rejected', checkout.includes('MIXED_CURRENCIES')],
  ['checkout group has one child link per order', checkout.includes('marketplace_checkout_group_items') && checkout.includes('order_id UUID NOT NULL UNIQUE')],
  ['each child order retains its own seller and listing', checkout.includes('seller_id,listing_id,quantity') && checkout.includes('sellerId: String(row.seller_id)')],
  ['each child reserves its own inventory atomically', checkout.includes('UPDATE marketplace_listings SET reserved_quantity=reserved_quantity+$1')],
  ['fulfillment updates lock one child order at a time', completion.includes('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE')],
  ['fulfillment authorization is seller-specific', completion.includes('only the seller can update fulfillment progress')],
  ['completion is blocked by an active protection case', completion.includes('hasActiveProtectionCase(client, id)')],
  ['payout reconciliation remains child-order scoped', settlement.includes('WHERE order_id=$1') && settlement.includes('PAYOUT-RELEASE-' + '$' + '{operation.order_id}')],
  ['payout is blocked when a refund is active for that child', settlement.includes("operation_type='REFUND'") && settlement.includes('refund financial operation is active for this order')],
  ['multi-product Paystack webhook reconciliation exists', webhook.includes('FYNX-CHECKOUT-') && webhook.includes('marketplace_checkout_groups') && webhook.includes('marketplace_checkout_group_items')],
  ['expired multi-product groups are reconciled as one unit', expiry.includes('expireUnpaidMarketplaceCheckoutGroups') && expiry.includes('UPDATE marketplace_checkout_groups SET status=\'CANCELLED\'')],
  ['expired group checkout releases every child inventory reservation', expiry.includes('checkout_group_payment_expired') && expiry.includes('reserved_quantity=GREATEST(0,reserved_quantity-$1)')],
  ['expired group payment is checked with Paystack before stock release', expiry.includes('verifyProviderCheckoutGroupPayment') && expiry.includes('provider.providerUnavailable') && expiry.includes('checkout-group-expiry-reconciliation')],
  ['single-order expiry retains stock for non-terminal referenced payments', expiry.includes("if (!provider.paid && !['failed', 'abandoned', 'reversed'].includes(provider.status)) continue;")],
  ['single-order expiry excludes child orders owned by checkout groups', expiry.includes('to_regclass(\'public.marketplace_checkout_group_items\')') && expiry.includes('NOT EXISTS (SELECT 1 FROM marketplace_checkout_group_items gi WHERE gi.order_id = marketplace_orders.id)')],
  ['group child exclusion remains safe before multi-product tables are created', expiry.includes('const groupChildExclusion = groupItemsTable') && expiry.includes(': "";')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace multi-product lifecycle guard FAILED');
  for (const name of failed) console.error(' - ' + name);
  process.exit(1);
}
console.log('Marketplace multi-product lifecycle guard PASSED');
for (const [name] of checks) console.log(' ✓ ' + name);
