import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const settlement = read('./marketplaceSettlement.js');
const worker = read('./marketplaceSettlementWorker.js');
const webhook = read('./marketplacePaystackWebhook.js');

const checks = [
  ['hold ledger is idempotent', settlement.includes("'ESCROW-HOLD-' || NEW.id") && settlement.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['payout ledger is idempotent', worker.includes('PAYOUT-RELEASE-${operation.order_id}') && worker.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['refund ledger is idempotent', /entry_type[^\n]*['\"]REFUND['\"]/.test(webhook) && webhook.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['payout amount is tied to escrow', worker.includes('operationAmount !== escrowAmount')],
  ['payout currency is tied to escrow', worker.includes("String(operation.currency).toUpperCase() !== String(escrow.currency).toUpperCase()")],
  ['refund blocks payout', worker.includes("operation_type='REFUND'") && worker.includes("status IN ('PENDING','SUCCEEDED')")],
  ['payout is release-pending only', worker.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['release terminal state is escrow RELEASED', worker.includes("SET status='RELEASED'")],
  ['refund terminal state is escrow REFUNDED', webhook.includes("SET status='REFUNDED'")],
  ['financial operation provider references are unique', settlement.includes('marketplace_fin_ops_provider_ref_idx')],
  ['ledger idempotency keys are unique', settlement.includes('idempotency_key TEXT NOT NULL UNIQUE')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace ledger invariant guard FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}

console.log(`Marketplace ledger invariant guard GREEN (${checks.length} checks)`);
