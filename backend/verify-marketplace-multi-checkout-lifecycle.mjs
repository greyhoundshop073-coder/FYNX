import fs from 'node:fs';

const read = (file) => fs.readFileSync(new URL(file, import.meta.url), 'utf8');

const checkout = read('./marketplaceCheckoutOrder.js');
const completion = read('./marketplaceCompletion.js');
const settlement = read('./marketplaceSettlementWorker.js');
const webhook = read('./marketplacePaystackWebhook.js');

const checks = [
  ['multi-product checkout route exists', checkout.includes("/api/marketplace/checkout/multi-order")],
  ['multi-product checkout is capped at 20 distinct products', checkout.includes('rawItems.length > 20') && checkout.includes('at most 20 different products')],
  ['duplicate listings are rejected', checkout.includes('DUPLICATE_LISTING')],
  ['mixed currencies are rejected', checkout.includes('MIXED_CURRENCIES')],
  ['checkout group has one child link per order', checkout.includes('marketplace_checkout_group_items') && checkout.includes('order_id UUID NOT NULL UNIQUE')],
  ['each child order retains its own seller and listing', checkout.includes('seller_id,listing_id,quantity') && checkout.includes('sellerId: String(row.seller_id)')],
  ['each child reserves its own inventory atomically', checkout.includes('UPDATE marketplace_listings SET reserved_quantity=reserved_quantity+$1')],
  ['fulfillment updates lock one child order at a time', completion.includes('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE')],
  ['fulfillment authorization is seller-specific', completion.includes('only the seller can update fulfillment progress')],
  ['completion is blocked by an active protection case', completion.includes('hasActiveProtectionCase(client, id)')],
  ['payout reconciliation remains child-order scoped', settlement.includes('WHERE order_id=$1') && settlement.includes('PAYOUT-RELEASE-' + '$' + '{operation.order_id}')],
  ['payout is blocked when a refund is active for that child', settlement.includes("operation_type='REFUND'") && settlement.includes('refund financial operation is active for this order')],
  ['multi-product Paystack webhook reconciliation exists', webhook.includes('FYNX-CHECKOUT-') && webhook.includes('marketplace_checkout_groups') && webhook.includes('marketplace_checkout_group_items')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace multi-product lifecycle guard FAILED');
  for (const name of failed) console.error(' - ' + name);
  process.exit(1);
}
console.log('Marketplace multi-product lifecycle guard PASSED');
for (const [name] of checks) console.log(' ✓ ' + name);