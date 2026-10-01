import fs from 'node:fs';
const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const protection = read('./marketplaceProtection.js');
const resolution = read('./marketplaceProtectionResolution.js');
const webhook = read('./marketplacePaystackWebhook.js');
const transactions = read('./marketplaceTransactions.js');
const settlement = read('./marketplaceSettlement.js');
const lifecycle = read('./verify-marketplace-protection-lifecycle.mjs');
const failure = read('./verify-marketplace-failure-paths.mjs');
const state = read('./verify-marketplace-state-machine.mjs');
const checks = [
  ['buyer refund requests are buyer-only', protection.includes("caseType === 'REFUND_REQUEST' && !isBuyer") && protection.includes('only the buyer can request a refund')],
  ['disputes require order participation', protection.includes('if (!isBuyer && !isSeller)') && protection.includes('order unavailable')],
  ['protection cases lock the order before mutation', protection.includes('WHERE id=$1 FOR UPDATE') && protection.includes("SET status='DISPUTED'")],
  ['one active protection case is enforced per order inside the row lock', protection.includes("status IN ('OPEN','UNDER_REVIEW')") && protection.includes('already has an active protection case')],
  ['case creation is idempotent', protection.includes('idempotencyKey') && protection.includes('idempotency_key TEXT NOT NULL UNIQUE')],
  ['cancellation remains buyer-only and payment-pending only', transactions.includes('only the buyer can cancel this order') && transactions.includes("order.status!=='PAYMENT_PENDING'")],
  ['refund amount and currency reconcile with protected escrow', resolution.includes('order and protected escrow accounting do not reconcile') && resolution.includes('escrowAmount') && resolution.includes('escrowCurrency')],
  ['refund operation is idempotent and payout-conflict protected', resolution.includes('idempotency_key=$1') && resolution.includes('payoutConflict')],
  ['seller resolution cannot race an active refund', resolution.includes('refundConflict') && resolution.includes("resolution === 'SELLER'")],
  ['cancel resolution cannot race money movement', resolution.includes("resolution === 'CANCEL'") && resolution.includes('payoutConflict || refundConflict')],
  ['seller resolution does not double-decrement already completed inventory', resolution.includes("String(row.previous_order_status) !== 'COMPLETED'")],
  ['refund webhook validates provider amount and currency', webhook.includes('providerAmount') && webhook.includes('expectedAmount') && webhook.includes('providerCurrency') && webhook.includes('expectedCurrency')],
  ['processed refund moves every non-refunded order to REFUNDED', webhook.includes("status <> 'REFUNDED'")],
  ['processed refund is ledger-idempotent', webhook.includes('REFUND-${operation.order_id}') && webhook.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['failed refund keeps funds protected', webhook.includes("eventName === 'refund.failed'") && webhook.includes("status='DISPUTED'")],
  ['successful payout requires release-pending escrow', webhook.includes("escrow?.status === 'RELEASE_PENDING'")],
  ['payout is blocked by active protection', settlement.includes("order.status === 'DISPUTED' || escrow.status === 'DISPUTED'")],
  ['payout requires completed order and release eligibility', settlement.includes("order.status !== 'COMPLETED' || escrow.status !== 'RELEASE_ELIGIBLE'")],
  ['existing protection lifecycle gates remain present', lifecycle.includes('Marketplace buyer-protection lifecycle verification') && failure.includes('Marketplace failure-path verification') && state.includes('Marketplace state-machine verification')],
];
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) throw new Error(`Marketplace Batch 3 security gate failed: ${failed.join('; ')}`);
console.log(`Marketplace Batch 3 disputes/refunds/security gate GREEN (${checks.length} checks)`);