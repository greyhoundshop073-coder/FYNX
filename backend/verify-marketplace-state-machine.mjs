import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const settlement = read('./marketplaceSettlement.js');
const payment = read('./marketplacePaymentState.js');
const webhook = read('./marketplacePaystackWebhook.js');
const resolution = read('./marketplaceProtectionResolution.js');
const inspection = read('./marketplaceInspectionExpiry.js');
const worker = read('./marketplaceSettlementWorker.js');

const checks = [
  ['PAID creates one escrow hold', settlement.includes("NEW.status = 'PAID'") && settlement.includes("ON CONFLICT (order_id) DO NOTHING") && settlement.includes("'ESCROW-HOLD-' || NEW.id")],
  ['DISPUTED moves protected escrow into dispute', settlement.includes("NEW.status = 'DISPUTED'") && settlement.includes("status='DISPUTED'")],
  ['COMPLETED only makes held escrow release eligible', settlement.includes("NEW.status = 'COMPLETED'") && settlement.includes("status='RELEASE_ELIGIBLE'") && settlement.includes("status='HELD'")],
  ['REFUNDED only finalizes supported escrow states', settlement.includes("NEW.status = 'REFUNDED'") && settlement.includes("status IN ('HELD','DISPUTED','REFUND_PENDING')")],
  ['CANCELLED cannot cancel release-pending escrow', settlement.includes("NEW.status = 'CANCELLED'") && settlement.includes("status IN ('HELD','DISPUTED')")],
  ['payment transition accepts only PAYMENT_PENDING or PAID', payment.includes("if (order.status === 'PAYMENT_PENDING')") && payment.includes("if (order.status === 'PAID')") && payment.includes('PAYMENT_STATE_CONFLICT')],
  ['payment transition locks order before state change', payment.includes('FOR UPDATE') && payment.includes("SET status='PAID'")],
  ['duplicate charge confirmation is idempotent', webhook.includes("source: 'webhook'") && payment.includes("idempotent: true")],
  ['refund completion finalizes only refund-pending or disputed escrow', webhook.includes("status IN ('REFUND_PENDING','DISPUTED')") && webhook.includes("status='REFUNDED'")],
  ['failed refund protects funds as disputed', webhook.includes("eventName === 'refund.failed'") && webhook.includes("status='DISPUTED'")],
  ['successful transfer requires release-pending escrow', webhook.includes("escrow?.status === 'RELEASE_PENDING'") && webhook.includes("status='RELEASED'")],
  ['failed transfer returns release eligibility', webhook.includes("eventName === 'transfer.failed' || eventName === 'transfer.reversed'") && webhook.includes("status='RELEASE_ELIGIBLE'")],
  ['payout worker requires release-pending escrow', worker.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['inspection expiry cannot complete disputed orders', inspection.includes('marketplace_order_disputes') && inspection.includes('marketplace_protection_cases')],
  ['admin resolution checks both financial conflicts', resolution.includes('const payoutConflict') && resolution.includes('const refundConflict')]
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
const failed = checks.filter(([, ok]) => !ok);
if (failed.length) process.exit(1);
console.log(`Marketplace state-machine verification passed: ${checks.length}/${checks.length}`);
