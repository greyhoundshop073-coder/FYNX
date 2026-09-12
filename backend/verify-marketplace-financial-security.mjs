import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const reputation = read('./marketplaceReputation.js');
const paymentState = read('./marketplacePaymentState.js');
const webhook = read('./marketplacePaystackWebhook.js');
const settlement = read('./marketplaceSettlement.js');
const worker = read('./marketplaceSettlementWorker.js');
const resolution = read('./marketplaceProtectionResolution.js');
const inspection = read('./marketplaceInspectionExpiry.js');

const checks = [
  ['verification and webhook share one payment transition', reputation.includes('confirmMarketplacePayment') && webhook.includes("confirmMarketplacePayment(client")],
  ['payment transition locks the order row', paymentState.includes('WHERE id=$1') && paymentState.includes('FOR UPDATE')],
  ['payment transition is idempotent for PAID', paymentState.includes("if (order.status === 'PAID') return { status: 'PAID', idempotent: true }")],
  ['payout request locks order and escrow', settlement.includes("WHERE id=$1 FOR UPDATE") && settlement.includes('marketplace_escrows WHERE order_id=$1 FOR UPDATE')],
  ['payout blocks disputed funds', settlement.includes("order.status === 'DISPUTED' || escrow.status === 'DISPUTED'")],
  ['payout requires release eligibility', settlement.includes("escrow.status !== 'RELEASE_ELIGIBLE'") && settlement.includes("order.status !== 'COMPLETED'")],
  ['payout worker blocks active refunds', worker.includes("operation_type='REFUND'") && worker.includes("status IN ('PENDING','SUCCEEDED')")],
  ['payout worker requires release-pending escrow', worker.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['admin resolution locks protection case/order rows', resolution.includes('WHERE c.id=$1 FOR UPDATE')],
  ['admin resolution checks payout/refund conflicts', resolution.includes('const payoutConflict') && resolution.includes('const refundConflict')],
  ['buyer refund is blocked by active payout', resolution.includes('resolution === \'BUYER\' && payoutConflict')],
  ['seller release is blocked by active refund', resolution.includes('resolution === \'SELLER\' && refundConflict')],
  ['refund webhook locks the refund operation', webhook.includes("WHERE f.operation_type='REFUND'") && webhook.includes('FOR UPDATE')],
  ['payout webhook locks payout operation', webhook.includes("WHERE f.operation_type='PAYOUT_RELEASE'") && webhook.includes('FOR UPDATE')],
  ['successful payout requires release-pending escrow', webhook.includes("escrow?.status === 'RELEASE_PENDING'")],
  ['refund ledger is idempotent', webhook.includes("entry_type,amount,currency,idempotency_key") && webhook.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['payout ledger is idempotent', worker.includes('PAYOUT-RELEASE-${operation.order_id}') && worker.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['inspection auto-completion blocks disputes', inspection.includes('marketplace_order_disputes') && inspection.includes('marketplace_protection_cases')],
  ['explicit admin authorization is enforced', resolution.includes('fynx_admin_roles') && !resolution.includes('SELECT id FROM users ORDER BY id ASC LIMIT 1')]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Marketplace financial race/security verification passed: ${checks.length}/${checks.length}`);
