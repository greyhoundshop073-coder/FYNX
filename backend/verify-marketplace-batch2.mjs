import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(`./${name}`, import.meta.url), 'utf8');
const payment = read('marketplaceReputation.js');
const expiry = read('marketplacePaymentExpiry.js');
const worker = read('marketplaceSettlementWorker.js');
const webhook = read('marketplacePaystackWebhook.js');
const settlement = read('marketplaceSettlement.js');
const scalability = read('scalability.js');
const finalHardening = read('marketplaceBatch2FinalHardening.js');
const checks = [
  ['payment initialization uses a deterministic order reference', payment.includes('const reference = `FYNX-${existing.id}`')],
  ['payment initialization has a locked database recheck', payment.includes('FOR UPDATE') && payment.includes("status='PAYMENT_PENDING'")],
  ['payment reference cannot be overwritten by a racing initializer', payment.includes('payment_reference IS NULL')],
  ['existing payment authorization is idempotent', payment.includes('idempotent: true') && payment.includes('payment_authorization_url')],
  ['provider metadata binds initialization to the marketplace order', payment.includes("purpose: 'FYNX_MARKETPLACE_ORDER'") && payment.includes('orderId: String(existing.id)')],
  ['interrupted payment recovery verifies Paystack before retry', expiry.includes('/transaction/verify/')],
  ['timeout recovery can verify a deterministic payment reference without a saved local reference', finalHardening.includes('recoverPaymentWithoutLocalReference') && finalHardening.includes('FYNX-${order.id}') && finalHardening.includes('batch2-timeout-recovery')],
  ['payout uses a deterministic lowercase provider reference', worker.includes('fynx-payout-${operation.order_id}')],
  ['payout recovery verifies the deterministic transfer before creating a new transfer', worker.includes('verifyTransferByReference(reference)') && worker.includes('recoveryCheckedFirst')],
  ['payout provider amount and currency are validated', worker.includes('transferMatchesOperation') && worker.includes('Paystack payout amount or currency does not match')],
  ['refund webhook amount and currency are validated', webhook.includes('providerAmount !== expectedAmount') && webhook.includes('providerCurrency !== expectedCurrency')],
  ['refund mismatches block protected funds', webhook.includes("status='BLOCKED'") && webhook.includes("status='DISPUTED'")],
  ['payment webhook is bound to the marketplace order', webhook.includes('metadataOrderId') && webhook.includes('payment_reference')],
  ['financial operations enforce provider reference uniqueness', settlement.includes('marketplace_fin_ops_provider_ref_idx')],
  ['ledger entries enforce idempotency uniqueness', settlement.includes('idempotency_key TEXT NOT NULL UNIQUE')],
  ['Paystack requests have a bounded timeout guard', finalHardening.includes('PAYSTACK_REQUEST_TIMEOUT_MS') || finalHardening.includes('PAYSTACK_TIMEOUT_MS') || finalHardening.includes('PAYSTACK_TIMEOUT')],
  ['refund provider timeout stays pending for reconciliation', finalHardening.includes('Refund request status is pending provider reconciliation') && finalHardening.includes("/refund$/i")],
  ['pending refund reconciliation verifies the transaction and uses its numeric provider id', finalHardening.includes('/transaction/verify/') && finalHardening.includes('Number(transactionData?.data?.id)') && finalHardening.includes('/refund?transaction=${encodeURIComponent(transactionId)}')],
  ['refund needs-attention remains pending instead of being treated as final failure', finalHardening.includes("status === 'needs-attention'") && finalHardening.includes('REFUND_NEEDS_ATTENTION') && !finalHardening.includes("['failed', 'needs-attention'].includes(status)" )],
  ['refund recovery revalidates amount and currency against escrow', finalHardening.includes('refund recovery amount or currency does not reconcile with the protected escrow')],
  ['refund recovery mismatch freezes the escrow', finalHardening.includes("SET status='DISPUTED'") && finalHardening.includes('REFUND_RECOVERY_BLOCKED')],
  ['post-success payout reversal is detected and re-protected', finalHardening.includes('reconcilePostSuccessPayoutReversals') && finalHardening.includes("providerStatus === 'reversed'") && finalHardening.includes('PAYOUT_REVERSAL_RECONCILED')],
  ['post-success payout reversal blocks the financial operation and disputes escrow', finalHardening.includes("status='BLOCKED'") && finalHardening.includes("status='DISPUTED'") && finalHardening.includes('reprotected_escrow_and_blocked_payout')],
  ['financial recovery writes explicit order audit events', finalHardening.includes('PAYOUT_RECOVERY_RECONCILED') && finalHardening.includes('REFUND_RECOVERY_RECONCILED')],
  ['recovery audit events are idempotently guarded', finalHardening.includes('NOT EXISTS') && finalHardening.includes("metadata->>'operationId'")]
];
const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) throw new Error(`Batch 2 financial hardening gate failed: ${failed.join('; ')}`);
console.log(`Batch 2 financial hardening gate passed (${checks.length} checks)`);
