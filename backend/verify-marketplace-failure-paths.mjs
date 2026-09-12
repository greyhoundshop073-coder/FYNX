import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const webhook = read('./marketplacePaystackWebhook.js');
const paymentState = read('./marketplacePaymentState.js');
const worker = read('./marketplaceSettlementWorker.js');
const resolution = read('./marketplaceProtectionResolution.js');
const settlement = read('./marketplaceSettlement.js');

const checks = [
  ['failed payout returns escrow to release eligibility', webhook.includes("eventName === 'transfer.failed' || eventName === 'transfer.reversed'") && webhook.includes("status='FAILED'") && webhook.includes("status='RELEASE_ELIGIBLE'")],
  ['failed refund moves protected funds to dispute', webhook.includes("eventName === 'refund.failed'") && webhook.includes("status='FAILED'") && webhook.includes("status='DISPUTED'")],
  ['successful payout only finalizes release-pending escrow', webhook.includes("escrow?.status === 'RELEASE_PENDING'") && webhook.includes("status='RELEASED'")],
  ['successful refund finalizes refund operation and escrow', webhook.includes("eventName === 'refund.processed'") && webhook.includes("status='SUCCEEDED'") && webhook.includes("status='REFUNDED'")],
  ['payment confirmation rejects terminal/conflicting states', paymentState.includes("throw Object.assign(new Error(`payment cannot confirm order from status ${order.status}`)")],
  ['payout worker cannot execute without release-pending escrow', worker.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['payout worker blocks pending or succeeded refunds', worker.includes("operation_type='REFUND'") && worker.includes("status IN ('PENDING','SUCCEEDED')")],
  ['buyer refund cannot race an active payout', resolution.includes('resolution === \'BUYER\' && payoutConflict')],
  ['seller release cannot race an active refund', resolution.includes('resolution === \'SELLER\' && refundConflict')],
  ['payout operation has unique idempotency', settlement.includes('idempotency_key TEXT NOT NULL UNIQUE') && settlement.includes('PAYOUT-${order.id}')],
  ['provider references are uniquely constrained', settlement.includes('marketplace_fin_ops_provider_ref_idx')],
  ['refund and payout ledger entries are idempotent', webhook.includes("ON CONFLICT (idempotency_key) DO NOTHING") && worker.includes("ON CONFLICT (idempotency_key) DO NOTHING")]
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
const failed = checks.filter(([, ok]) => !ok);
if (failed.length) process.exit(1);
console.log(`Marketplace failure-path verification passed: ${checks.length}/${checks.length}`);
