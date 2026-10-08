import fs from 'node:fs';

const read = (file) => fs.readFileSync(new URL(file, import.meta.url), 'utf8');

const protection = read('./marketplaceProtection.js');
const resolution = read('./marketplaceProtectionResolution.js');
const webhook = read('./marketplacePaystackWebhook.js');
const settlement = read('./marketplaceSettlementWorker.js');
const checkout = read('./marketplaceCheckoutOrder.js');

const checks = [
  ['protection cases are keyed to child order_id', protection.includes('order_id UUID NOT NULL REFERENCES marketplace_orders(id)') && protection.includes('CREATE INDEX IF NOT EXISTS marketplace_protection_cases_order_idx ON marketplace_protection_cases (order_id')],
  ['dispute creation locks only the requested child order', protection.includes('WHERE id=$1 FOR UPDATE') && protection.includes('getOrderAndEscrow(client, orderId)')],
  ['active protection lookup is child-order scoped', protection.includes("WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')")],
  ['refund requests are buyer-scoped to the child order', protection.includes("if (caseType === 'REFUND_REQUEST' && !isBuyer)")],
  ['refund operations use a child-order idempotency key', resolution.includes('const key = `REFUND-' + '${row.order_id}`' + '`')],
  ['refund resolution creates a child-order financial operation', resolution.includes("INSERT INTO marketplace_financial_operations") && resolution.includes("row.order_id") && resolution.includes("operation_type,'REFUND'")],
  ['refund webhook reconciliation resolves the child financial operation', webhook.includes("WHERE f.operation_type='REFUND' AND o.payment_reference=$1") && webhook.includes('operation.order_id')],
  ['refund webhook releases only the refunded child inventory', webhook.includes('operation.quantity') && webhook.includes('operation.listing_id') && webhook.includes('operation.order_id')],
  ['payout refund conflict is child-order scoped', settlement.includes("operation_type='REFUND'") && settlement.includes('WHERE order_id=$1')],
  ['multi-product checkout maps each child order exactly once', checkout.includes('order_id UUID NOT NULL UNIQUE REFERENCES marketplace_orders(id)')],
  ['individual refund/dispute paths do not operate on checkout-group status', !/UPDATE\s+marketplace_checkout_groups[\s\S]{0,500}(REFUND|DISPUT|order_id)/i.test(resolution + webhook)],
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace refund/dispute isolation guard FAILED');
  for (const name of failed) console.error(' - ' + name);
  process.exit(1);
}
console.log('Marketplace refund/dispute isolation guard PASSED');
for (const [name] of checks) console.log(' ✓ ' + name);
