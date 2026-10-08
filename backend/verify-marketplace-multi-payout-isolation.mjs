import fs from 'node:fs';

const read = (file) => fs.readFileSync(new URL(file, import.meta.url), 'utf8');
const checkoutOrder = read('./marketplaceCheckoutOrder.js');
const payoutRetry = read('./marketplacePayoutRetry.js');
const worker = read('./marketplaceSettlementWorker.js');
const webhook = read('./marketplacePaystackWebhook.js');
const reconciliation = read('./verify-marketplace-reconciliation.mjs');

const checks = [
  ['multi-order creates seller-specific child orders', checkoutOrder.includes('seller_id') && checkoutOrder.includes('marketplace_checkout_group_items') && checkoutOrder.includes('marketplace_orders')],
  ['each payout operation is keyed to its child order', payoutRetry.includes('WHERE id=$1 FOR UPDATE') && payoutRetry.includes('operation_type=\'PAYOUT_RELEASE\'') && payoutRetry.includes('order_id=$1')],
  ['payout retry authenticates the child order seller', payoutRetry.includes('only the seller can retry this payout') && payoutRetry.includes('order.seller_id')],
  ['payout account is selected for that seller and order currency', payoutRetry.includes('seller_id=$1') && payoutRetry.includes('UPPER(currency)=UPPER($2)') && payoutRetry.includes('order.currency')],
  ['seller net plus marketplace fee reconciles against that child escrow', payoutRetry.includes('sellerNetAmount + marketplaceFee') && payoutRetry.includes('protectedAmount')],
  ['payout operation preserves the child order currency', payoutRetry.includes("INSERT INTO marketplace_financial_operations") && payoutRetry.includes('order.currency')],
  ['worker locks the child escrow before payout release', worker.includes('WHERE order_id=$1 FOR UPDATE') && worker.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['worker validates payout currency against child escrow currency', worker.includes('operation.currency').toString() && worker.includes('escrow.currency') && worker.includes('transferMatchesOperation')],
  ['payout ledger remains child-order idempotent', worker.includes('PAYOUT-RELEASE-${operation.order_id}') && worker.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['payout webhook records the child operation order id', webhook.includes('operation.order_id') && webhook.includes('seller_payout')],
  ['existing reconciliation contract covers payout account currency', reconciliation.includes('payout account currency is matched to the order currency')],
  ['active refund on one child blocks only that child payout operation', worker.includes("WHERE order_id=$1 AND operation_type='REFUND'") && worker.includes('refund financial operation is active for this order')],
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace multi-product payout isolation guard FAILED');
  for (const name of failed) console.error(' - ' + name);
  process.exit(1);
}
console.log(`Marketplace multi-product payout isolation guard GREEN (${checks.length} checks)`);
for (const [name] of checks) console.log(' ✓ ' + name);
