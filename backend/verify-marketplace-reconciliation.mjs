import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const worker = read('./marketplaceSettlementWorker.js');
const webhook = read('./marketplacePaystackWebhook.js');
const settlement = read('./marketplaceSettlement.js');

const checks = [
  ['deterministic payout reference', worker.includes('FYNX-PAYOUT-${operation.order_id}')],
  ['provider-reference duplicate guard', worker.includes("if (operation.provider_reference)")],
  ['release-pending escrow gate', worker.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['pending payout recovery scan', worker.includes("fo.operation_type='PAYOUT_RELEASE'") && worker.includes("fo.status='PENDING'")],
  ['provider transfer verification', worker.includes('/transfer/verify/')],
  ['idempotent payout ledger', worker.includes('PAYOUT-RELEASE-${operation.order_id}') && worker.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['transfer success webhook', webhook.includes("eventName === 'transfer.success'")],
  ['transfer failure/reversal webhook', webhook.includes("eventName === 'transfer.failed'") && webhook.includes("eventName === 'transfer.reversed'")],
  ['webhook amount validation', webhook.includes('expectedAmount') && webhook.includes('providerAmount')],
  ['webhook currency validation', webhook.includes('providerCurrency') && webhook.includes('currencyMatches')],
  ['refund remains asynchronous', webhook.includes("eventName.startsWith('refund.')") && webhook.includes("eventName === 'refund.processed'") && webhook.includes("eventName === 'refund.failed'") && webhook.includes("SET status='PENDING'")],
  ['financial operation provider reference uniqueness', settlement.includes('marketplace_fin_ops_provider_ref_idx')],
  ['ledger idempotency uniqueness', settlement.includes('idempotency_key TEXT NOT NULL UNIQUE')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace reconciliation guard FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}

console.log(`Marketplace reconciliation guard GREEN (${checks.length} checks)`);
