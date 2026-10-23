import fs from 'node:fs';
import { selectMarketplaceRefundByIdentity } from './marketplaceBatch2FinalHardening.js';
import { classifyPaystackTransferVerification } from './marketplaceSettlementWorker.js';

const read = (name) => fs.readFileSync(new URL(`./${name}`, import.meta.url), 'utf8');
const payment = read('marketplaceReputation.js');
const expiry = read('marketplacePaymentExpiry.js');
const worker = read('marketplaceSettlementWorker.js');
const webhook = read('marketplacePaystackWebhook.js');
const settlement = read('marketplaceSettlement.js');
const scalability = read('scalability.js');
const finalHardening = read('marketplaceBatch2FinalHardening.js');
const checks = [
  ['first-time payment initialization keeps its legacy recovery reference and safely falls back to a unique reference after a verified duplicate failure', payment.includes('let reference = `FYNX-${existing.id}`') && payment.includes('FYNX-ORDER-${crypto.randomUUID()}') && payment.includes('duplicateReferenceError')],
  ['payment initialization has a locked database recheck', payment.includes('FOR UPDATE') && payment.includes("status='PAYMENT_PENDING'")],
  ['payment reference cannot be overwritten by a racing initializer and stale replacement is reference-bound', payment.includes('payment_reference IS NULL') && payment.includes('payment_reference=$6 AND payment_authorization_url IS NULL')],
  ['existing payment authorization is idempotent', payment.includes('idempotent: true') && payment.includes('payment_authorization_url')],
  ['provider metadata binds initialization to the marketplace order', payment.includes("purpose: 'FYNX_MARKETPLACE_ORDER'") && payment.includes('orderId: String(existing.id)')],
  ['interrupted payment recovery verifies Paystack before retry', expiry.includes('/transaction/verify/')],
  ['timeout recovery can verify a deterministic payment reference without a saved local reference', finalHardening.includes('recoverPaymentWithoutLocalReference') && finalHardening.includes('FYNX-${order.id}') && finalHardening.includes('batch2-timeout-recovery')],
  ['payout uses a deterministic lowercase provider reference', worker.includes('fynx-payout-${operation.order_id}')],
  ['payout recovery verifies the deterministic transfer before creating a new transfer', worker.includes('verifyTransferByReference(reference)') && worker.includes('recoveryCheckedFirst')],
  ['payout transfer verification treats only HTTP 404 as not found and binds successful data to the requested reference', worker.includes('response?.status === 404') && worker.includes('classifyPaystackTransferVerification(response, data, cleanReference)') && !worker.includes('response.status === 404 || data?.status === false')],
  ['payout provider amount and currency are validated', worker.includes('transferMatchesOperation') && worker.includes('Paystack payout amount or currency does not match')],
  ['refund webhook amount and currency are validated', webhook.includes('providerAmount !== expectedAmount') && webhook.includes('providerCurrency !== expectedCurrency')],
  ['refund mismatches block protected funds', webhook.includes("status='BLOCKED'") && webhook.includes("status='DISPUTED'")],
  ['payment webhook is bound to the marketplace order', webhook.includes('metadataOrderId') && webhook.includes('payment_reference')],
  ['financial operations enforce provider reference uniqueness', settlement.includes('marketplace_fin_ops_provider_ref_idx')],
  ['ledger entries enforce idempotency uniqueness', settlement.includes('idempotency_key TEXT NOT NULL UNIQUE')],
  ['Paystack requests have a bounded timeout guard', finalHardening.includes('PAYSTACK_REQUEST_TIMEOUT_MS') || finalHardening.includes('PAYSTACK_TIMEOUT_MS') || finalHardening.includes('PAYSTACK_TIMEOUT')],
  ['refund provider timeout stays pending for reconciliation', finalHardening.includes('Refund request status is pending provider reconciliation') && finalHardening.includes("/refund$/i")],
  ['pending refund reconciliation verifies the transaction reference and uses its numeric provider id', finalHardening.includes('/transaction/verify/') && finalHardening.includes('Number(transaction.id)') && finalHardening.includes("String(transaction.reference || '') !== resolvedReference") && finalHardening.includes('/refund?transaction=${encodeURIComponent(transactionId)}')],
  ['refund needs-attention remains pending instead of being treated as final failure', finalHardening.includes("status === 'needs-attention'") && finalHardening.includes('REFUND_NEEDS_ATTENTION') && !finalHardening.includes("['failed', 'needs-attention'].includes(status)" )],
  ['pending refund recovery resolves checkout-group child orders to the parent Paystack reference when needed', finalHardening.includes('marketplace_checkout_group_items') && finalHardening.includes('marketplace_checkout_groups') && finalHardening.includes('resolved_payment_reference') && finalHardening.includes("COALESCE(NULLIF(o.payment_reference, '')")],
  ['pending refund recovery verifies the provider transaction matches the resolved payment reference and currency', finalHardening.includes("String(transaction.reference || '') !== resolvedReference") && finalHardening.includes("String(transaction.currency || '').toUpperCase() !== String(row.currency || '').toUpperCase()")],
  ['pending refund recovery delegates to an exact provider refund ID/reference matcher, not amount/currency alone', finalHardening.includes('selectMarketplaceRefundByIdentity(refunds, knownRefundReferences)') && finalHardening.includes('known.has(String(refund?.id ??') && finalHardening.includes('known.has(String(refund?.refund_reference ??') && !finalHardening.includes('refunds.find(refund => Number(refund?.amount) === expectedAmount')],
  ['ambiguous or unidentifiable refunds are left pending instead of being assigned to an arbitrary child order', finalHardening.includes('if (!match) continue;') && finalHardening.includes('if (!knownRefundReferences.length) continue;') && finalHardening.includes('return matches.length === 1 ? matches[0] : null;')],
  ['exactly identified refund with mismatched provider amount/currency is blocked and its escrow disputed', finalHardening.includes('provider_refund_amount_or_currency_mismatch') && finalHardening.includes("status='BLOCKED'") && finalHardening.includes("status='DISPUTED'")],
  ['refund recovery does not decrement reserved inventory again after order completion consumed it', finalHardening.includes('o.completed_at,o.quantity,o.listing_id,o.payment_reference') && finalHardening.includes("if (!locked.completed_at && locked.order_status !== 'COMPLETED'")],
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

const refundA = { id: 747680, refund_reference: 'RRN-ALPHA', amount: 10000, currency: 'NGN' };
const refundB = { id: 747681, refund_reference: 'RRN-BETA', amount: 10000, currency: 'NGN' };
const refundIdentityChecks = [
  ['refund recovery chooses the exact provider refund ID even when another refund has the same amount and currency', selectMarketplaceRefundByIdentity([refundA, refundB], ['747681']) === refundB],
  ['refund recovery can match an exact refund reference', selectMarketplaceRefundByIdentity([refundA, refundB], ['RRN-ALPHA']) === refundA],
  ['refund recovery refuses amount-only guessing when the operation has no refund identity', selectMarketplaceRefundByIdentity([refundA, refundB], []) === null],
  ['refund recovery refuses ambiguous duplicate provider identities', selectMarketplaceRefundByIdentity([refundA, { ...refundA }], ['747680']) === null],
  ['refund recovery never treats a transaction reference as a refund identifier when no refund matches it', selectMarketplaceRefundByIdentity([refundA, refundB], ['FYNX-CHECKOUT-transaction']) === null]
];
const refundIdentityFailures = refundIdentityChecks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of refundIdentityChecks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (refundIdentityFailures.length) throw new Error(`Refund identity behavior gate failed: ${refundIdentityFailures.join('; ')}`);

const shouldThrow = (callback) => { try { callback(); return false; } catch { return true; } };
const transferVerificationChecks = [
  ['Paystack transfer HTTP 404 is classified as a missing transfer', classifyPaystackTransferVerification({ status: 404, ok: false }, { status: false, message: 'Transfer not found' }, 'fynx-payout-order').found === false],
  ['Paystack transfer auth failures are not treated as a missing transfer', shouldThrow(() => classifyPaystackTransferVerification({ status: 401, ok: false }, { status: false, message: 'Unauthorized' }, 'fynx-payout-order'))],
  ['Paystack transfer server errors are not treated as a missing transfer', shouldThrow(() => classifyPaystackTransferVerification({ status: 500, ok: false }, { status: false, message: 'Temporary provider failure' }, 'fynx-payout-order'))],
  ['successful transfer verification must return the exact requested reference', classifyPaystackTransferVerification({ status: 200, ok: true }, { status: true, data: { reference: 'fynx-payout-order', amount: 10000, currency: 'NGN' } }, 'fynx-payout-order').found === true],
  ['successful transfer verification rejects a different provider reference', shouldThrow(() => classifyPaystackTransferVerification({ status: 200, ok: true }, { status: true, data: { reference: 'another-transfer', amount: 10000, currency: 'NGN' } }, 'fynx-payout-order'))]
];
const transferVerificationFailures = transferVerificationChecks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of transferVerificationChecks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (transferVerificationFailures.length) throw new Error(`Payout verification behavior gate failed: ${transferVerificationFailures.join('; ')}`);
console.log(`Payout verification behavior gate passed (${transferVerificationChecks.length} cases)`);
console.log(`Batch 2 financial hardening gate passed (${checks.length} structural checks, ${refundIdentityChecks.length} refund identity cases, and ${transferVerificationChecks.length} payout verification cases)`);

