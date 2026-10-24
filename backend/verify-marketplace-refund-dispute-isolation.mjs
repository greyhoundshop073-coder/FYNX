import fs from 'node:fs';

const read = (file) => fs.readFileSync(new URL(file, import.meta.url), 'utf8');

const protection = read('./marketplaceProtection.js');
const resolution = read('./marketplaceProtectionResolution.js');
const webhook = read('./marketplacePaystackWebhook.js');
const server = read('./server.js');
const settlement = read('./marketplaceSettlementWorker.js');
const checkout = read('./marketplaceCheckoutOrder.js');

const checks = [
  ['protection cases are keyed to child order_id', protection.includes('order_id UUID NOT NULL REFERENCES marketplace_orders(id)') && protection.includes('CREATE INDEX IF NOT EXISTS marketplace_protection_cases_order_idx ON marketplace_protection_cases (order_id')],
  ['dispute creation locks only the requested child order', protection.includes('WHERE id=$1 FOR UPDATE') && protection.includes('getOrderAndEscrow(client, orderId)')],
  ['active protection lookup is child-order scoped', protection.includes("WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')")],
  ['refund requests are buyer-scoped to the child order', protection.includes("if (caseType === 'REFUND_REQUEST' && !isBuyer)")],
  ['refund operations use a child-order idempotency key', resolution.includes('REFUND-${row.order_id}')],
  ['refund resolution creates a child-order financial operation', resolution.includes('INSERT INTO marketplace_financial_operations') && resolution.includes('row.order_id') && resolution.includes("'REFUND'")],
  ['refund resolution can fall back to the verified parent checkout reference for a child order', resolution.includes('resolveOrderPaymentReference(client, row.order_id, row.payment_reference)') && resolution.includes('marketplace_checkout_group_items') && resolution.includes('g.payment_reference')],
  ['refund webhook maps a group transaction to the correct child financial operation', webhook.includes('async function findRefundOperation') && webhook.includes('resolved_payment_reference') && webhook.includes('f.provider_reference=ANY($1::text[])') && webhook.includes('marketplace_checkout_group_items')],
  ['Paystack webhook route is registered by the actual server entrypoint', server.includes('import { registerMarketplacePaystackWebhook } from "./marketplacePaystackWebhook.js";') && server.includes('registerMarketplacePaystackWebhook({ app, pool })') && webhook.includes("app.post('/api/marketplace/payments/paystack/webhook'")],
  ['raw Paystack JSON bytes are preserved before signature verification', server.includes('verify: (req, _res, buffer) =>') && server.includes('req.rawBody = Buffer.from(buffer)') && webhook.includes('Buffer.isBuffer(req.rawBody)')],
  ['shared-reference ambiguity counts succeeded refunds before selecting a child', (() => { const query = webhook.split('const candidates = await client.query', 1)[1]?.split('[transactionReference]);', 1)[0] || ''; return query.includes("f.operation_type='REFUND'") && query.includes('${effectiveReference}=$1') && !query.includes("f.status IN ('PENDING','FAILED')") && webhook.includes('candidates.rows.length === 1 && refundOperationCanApply(candidates.rows[0], eventName)'); })()],
  ['terminal succeeded refunds ignore stale non-processed webhook events', webhook.includes('function refundOperationCanApply') && webhook.includes("if (currentStatus === 'SUCCEEDED') return eventName === 'refund.processed'") && webhook.includes('findRefundOperation(client, transactionReference, refund, eventName)')],
  ['duplicate processed-refund webhooks do not repeat reservation release', webhook.includes("operation.status === 'SUCCEEDED' && eventName === 'refund.processed'")],
  ['refund inventory release only applies when the order still held a reservation', webhook.includes('inventoryWasStillReserved') && webhook.includes("['PAID', 'SHIPPED', 'DELIVERED', 'INSPECTION'].includes(previousOrderStatus)")],
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
