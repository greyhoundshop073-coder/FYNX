import fs from 'node:fs';
import { selectMarketplaceRefundByIdentity, payoutTransferMatchesStoredIdentity } from './marketplaceBatch2FinalHardening.js';
import { classifyPaystackTransferVerification, classifyPaystackTransferFetch, payoutInitializationOutcomeIsUncertain, nextPayoutVerificationProgress, payoutSweepIdempotencyKey, payoutVerificationJobIdempotencyKey, payoutReferenceForOperation } from './marketplaceSettlementWorker.js';

const read = (name) => fs.readFileSync(new URL(`./${name}`, import.meta.url), 'utf8');
const payment = read('marketplaceReputation.js');
const expiry = read('marketplacePaymentExpiry.js');
const worker = read('marketplaceSettlementWorker.js');
const webhook = read('marketplacePaystackWebhook.js');
const settlement = read('marketplaceSettlement.js');
const payoutRetry = read('marketplacePayoutRetry.js');
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
  ['first payout attempts preserve the legacy provider reference while retry operations use their own stable operation ID', worker.includes('payoutReferenceForOperation(operation)') && worker.includes('`fynx-payout-${orderId}`') && worker.includes('`fynx-payout-${operationId}`')],
  ['payout recovery verifies the deterministic transfer before creating a new transfer', worker.includes('verifyTransferByReference(reference)') && worker.includes('recoveryCheckedFirst')],
  ['payout transfer verification treats only HTTP 404 as not found and binds successful data to the requested reference', worker.includes('response?.status === 404') && worker.includes('classifyPaystackTransferVerification(verifyResult.response, verifyResult.data, cleanReference)') && !worker.includes('response.status === 404 || data?.status === false')],
  ['ambiguous payout create responses are followed by exact-reference verification before state changes', worker.includes('const afterCreateCheck = await verifyTransferByReference(reference);') && worker.includes('if (afterCreateCheck.found)') && worker.includes('payoutInitializationOutcomeIsUncertain(response, data)') && worker.includes('Paystack payout initialization outcome is unresolved')],
  ['unknown provider transfer states remain pending instead of being treated as payout failure', worker.includes('const unresolvedProviderState = response.ok') && worker.includes('accepted || unresolvedProviderState') && worker.includes("providerStatus: providerStatus || 'unknown'")],
  ['every payout polling result uses exact-reference verification or exact transfer-code recovery before updating settlement', worker.includes('const verification = await verifyTransferByReference(operation.provider_reference);') && worker.includes('const verifiedTransfer = verification.found ? verification.data.data : null;') && worker.includes('if (verification.found && !transferMatchesOperation(verifiedTransfer, current))')],
  ['payout polling stores the canonical transfer reference rather than a transfer code', worker.includes("const providerReference = String(data?.data?.reference || reference);") && !worker.includes('data?.data?.transfer_code || reference')],
  ['legacy payout rows stored with transfer codes can fetch the exact transfer and replace the stored value with its canonical reference', worker.includes("const fetchUrl = 'https://api.paystack.co/transfer/'") && worker.includes('classifyPaystackTransferFetch(fetchResult.response, fetchResult.data, cleanReference)') && worker.includes('providerReferenceRecovered: true')],
  ['dedicated seller payout retry route only retries a failed attempt when order, escrow, and account are eligible', payoutRetry.includes("app.post('/api/marketplace/settlement/retry/:id'") && payoutRetry.includes("escrow.status !== 'RELEASE_ELIGIBLE'") && payoutRetry.includes("status='FAILED'") && payoutRetry.includes('payoutAccount.currency') && payoutRetry.includes('order.currency') && payoutRetry.includes("order.status !== 'COMPLETED'") && payoutRetry.includes('order and escrow currency do not match') && payoutRetry.includes('PAYOUT-RETRY-${failed.id}')],
  ['payout scanner creates idempotent time-bucketed sweeps for stale pending payout operations', worker.includes("fo.updated_at <= NOW() - INTERVAL '2 minutes'") && worker.includes("payoutSweepIdempotencyKey('VERIFY', operationId, bucketTime)") && worker.includes("payoutSweepIdempotencyKey('RELEASE', operationId, bucketTime)")],
  ['verification scan resets the bounded attempt counter after a cycle while recording completed cycles', worker.includes('verificationAttempts: progress.verificationAttempts') && worker.includes('verificationCycles: progress.verificationCycles') && worker.includes('return progress.shouldContinue')],
  ['every payout verification cycle uses unique chained job keys so completed prior jobs cannot block later sweeps', worker.includes('payoutVerificationJobIdempotencyKey(operationId, verificationCycle, attempt)') && worker.includes('payoutVerificationJobIdempotencyKey(operationId, verificationCycle, 0)') && worker.includes('verificationCycle }, { idempotencyKey: verificationCycle')],
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
  ['post-success payout reversal recovery binds the returned reference or exact legacy transfer code before checking amount/status', finalHardening.includes('payoutTransferMatchesStoredIdentity(transfer, row.provider_reference)') && finalHardening.includes('payoutTransferMatchesStoredIdentity(transfer, locked.provider_reference)') && finalHardening.includes('providerReferenceRecovered: true') && finalHardening.includes('AND provider_reference=$4')],
  ['post-success payout reversal scan rotates through all eligible records instead of repeatedly selecting the oldest 25', finalHardening.includes("ORDER BY f.metadata->>'lastProviderReconciledAt' ASC NULLS FIRST, f.updated_at ASC") && finalHardening.includes("metadata=COALESCE(metadata,'{}'::jsonb) || $1::jsonb WHERE id=$2 AND status='SUCCEEDED' AND provider_reference=$3") && finalHardening.includes('lastProviderReconciledAt: reconciliationAttemptedAt') && finalHardening.indexOf('lastProviderReconciledAt: reconciliationAttemptedAt') < finalHardening.indexOf('const response = await fetch(`https://api.paystack.co/transfer/verify/')],
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

const payoutIdentityChecks = [
  ['post-success payout recognizes its exact canonical transfer reference', payoutTransferMatchesStoredIdentity({ reference: 'fynx-payout-order-1', transfer_code: 'TRF_1' }, 'fynx-payout-order-1')],
  ['post-success payout can recognize a legacy row by its exact transfer code', payoutTransferMatchesStoredIdentity({ reference: 'fynx-payout-order-1', transfer_code: 'TRF_1' }, 'TRF_1')],
  ['post-success payout rejects a response with neither the stored reference nor stored transfer code', !payoutTransferMatchesStoredIdentity({ reference: 'different-ref', transfer_code: 'TRF_OTHER' }, 'fynx-payout-order-1')],
  ['empty stored payout identity never matches a provider transfer', !payoutTransferMatchesStoredIdentity({ reference: 'fynx-payout-order-1', transfer_code: 'TRF_1' }, '')]
 ];
const payoutIdentityFailures = payoutIdentityChecks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of payoutIdentityChecks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (payoutIdentityFailures.length) throw new Error(`Post-success payout identity behavior gate failed: ${payoutIdentityFailures.join('; ')}`);
console.log(`Post-success payout identity behavior gate passed (${payoutIdentityChecks.length} cases)`);

const shouldThrow = (callback) => { try { callback(); return false; } catch { return true; } };
const payoutProgressChecks = [
  ['first payout attempt keeps its historical reference for recovery during deploys', payoutReferenceForOperation({ id: 'operation-1', order_id: 'order-1', idempotency_key: 'PAYOUT-order-1' }) === 'fynx-payout-order-1'],
  ['retry payout attempt receives a new reference distinct from the original attempt', payoutReferenceForOperation({ id: 'operation-2', order_id: 'order-1', idempotency_key: 'PAYOUT-order-1-RETRY-operation-2' }) === 'fynx-payout-operation-2' && payoutReferenceForOperation({ id: 'operation-2', order_id: 'order-1', idempotency_key: 'PAYOUT-order-1-RETRY-operation-2' }) !== payoutReferenceForOperation({ id: 'operation-1', order_id: 'order-1', idempotency_key: 'PAYOUT-order-1' })],
  ['retry payout reference is deterministic for the same retry operation', payoutReferenceForOperation({ id: 'operation-2', order_id: 'order-1', idempotency_key: 'PAYOUT-order-1-RETRY-operation-2' }) === payoutReferenceForOperation({ id: 'operation-2', order_id: 'order-1', idempotency_key: 'PAYOUT-order-1-RETRY-operation-2' })],
  ['payout verification attempts continue within a cycle before the cap', nextPayoutVerificationProgress({ verificationAttempts: 10, verificationCycles: 2 }).shouldContinue === true && nextPayoutVerificationProgress({ verificationAttempts: 10, verificationCycles: 2 }).verificationAttempts === 11],
  ['the twelfth pending verification closes a cycle and resets attempts for future sweeps', (() => { const p = nextPayoutVerificationProgress({ verificationAttempts: 11, verificationCycles: 2 }); return p.shouldContinue === false && p.verificationAttempts === 0 && p.verificationCycles === 3; })()],
  ['a subsequent payout sweep starts a new verification cycle', (() => { const p = nextPayoutVerificationProgress({ verificationAttempts: 0, verificationCycles: 3 }); return p.shouldContinue === true && p.verificationAttempts === 1 && p.verificationCycles === 3; })()],
  ['stale payout sweeps are idempotent within a two-minute time bucket', payoutSweepIdempotencyKey('VERIFY', 'operation-1', 240000) === payoutSweepIdempotencyKey('VERIFY', 'operation-1', 299999)],
  ['stale payout sweeps get a fresh idempotency key in the next bucket', payoutSweepIdempotencyKey('VERIFY', 'operation-1', 240000) !== payoutSweepIdempotencyKey('VERIFY', 'operation-1', 360000)],
  ['release sweeps have distinct idempotency keys from verify sweeps', payoutSweepIdempotencyKey('RELEASE', 'operation-1', 240000) !== payoutSweepIdempotencyKey('VERIFY', 'operation-1', 240000)],
  ['a later verification cycle does not reuse completed attempt keys from the first cycle', payoutVerificationJobIdempotencyKey('operation-1', 'cycle-one', 1) !== payoutVerificationJobIdempotencyKey('operation-1', 'cycle-two', 1)],
  ['verification attempts within one cycle keep stable idempotency keys', payoutVerificationJobIdempotencyKey('operation-1', 'cycle-one', 1) === payoutVerificationJobIdempotencyKey('operation-1', 'cycle-one', 1)],
  ['the initial payout verification cycle has a unique cycle-scoped key', payoutVerificationJobIdempotencyKey('operation-1', 'initial', 0) !== payoutVerificationJobIdempotencyKey('operation-1', 'RELEASE-SWEEP-operation-1-2', 0)]
];
const payoutProgressFailures = payoutProgressChecks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of payoutProgressChecks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (payoutProgressFailures.length) throw new Error(`Payout resweep progress gate failed: ${payoutProgressFailures.join('; ')}`);
console.log(`Payout resweep progress gate passed (${payoutProgressChecks.length} cases)`);
const transferVerificationChecks = [
  ['Paystack transfer HTTP 404 is classified as a missing transfer', classifyPaystackTransferVerification({ status: 404, ok: false }, { status: false, message: 'Transfer not found' }, 'fynx-payout-order').found === false],
  ['Paystack transfer auth failures are not treated as a missing transfer', shouldThrow(() => classifyPaystackTransferVerification({ status: 401, ok: false }, { status: false, message: 'Unauthorized' }, 'fynx-payout-order'))],
  ['Paystack transfer server errors are not treated as a missing transfer', shouldThrow(() => classifyPaystackTransferVerification({ status: 500, ok: false }, { status: false, message: 'Temporary provider failure' }, 'fynx-payout-order'))],
  ['successful transfer verification must return the exact requested reference', classifyPaystackTransferVerification({ status: 200, ok: true }, { status: true, data: { reference: 'fynx-payout-order', amount: 10000, currency: 'NGN' } }, 'fynx-payout-order').found === true],
  ['successful transfer verification rejects a different provider reference', shouldThrow(() => classifyPaystackTransferVerification({ status: 200, ok: true }, { status: true, data: { reference: 'another-transfer', amount: 10000, currency: 'NGN' } }, 'fynx-payout-order'))],
  ['legacy stored transfer code can recover a result with a different canonical reference when the exact transfer code matches', classifyPaystackTransferFetch({ status: 200, ok: true }, { status: true, data: { reference: 'fynx-payout-order', transfer_code: 'TRF_legacy-code', amount: 10000, currency: 'NGN' } }, 'TRF_legacy-code').found === true],
  ['legacy transfer-code recovery rejects an unrelated transfer identity', shouldThrow(() => classifyPaystackTransferFetch({ status: 200, ok: true }, { status: true, data: { reference: 'other-ref', transfer_code: 'TRF_other-code', amount: 10000, currency: 'NGN' } }, 'TRF_legacy-code'))],
  ['fetching legacy transfer code returns not-found only on HTTP 404', classifyPaystackTransferFetch({ status: 404, ok: false }, { status: false, message: 'Transfer not found' }, 'TRF_legacy-code').found === false],
  ['legacy transfer-code recovery retries indeterminate provider failures', shouldThrow(() => classifyPaystackTransferFetch({ status: 503, ok: false }, { status: false, message: 'Provider unavailable' }, 'TRF_legacy-code'))],
  ['payout initialization 5xx is ambiguous and must be verified again', payoutInitializationOutcomeIsUncertain({ status: 500, ok: false }, { status: false, message: 'Internal server error' })],
  ['payout initialization 429 is ambiguous and must be verified again', payoutInitializationOutcomeIsUncertain({ status: 429, ok: false }, { status: false, message: 'Rate limited' })],
  ['duplicate-reference response stays unresolved rather than making a new payout attempt', payoutInitializationOutcomeIsUncertain({ status: 400, ok: false }, { status: false, message: 'Duplicate reference already used' })],
  ['clear validation rejection can be marked failed only after follow-up verification confirms absence', payoutInitializationOutcomeIsUncertain({ status: 400, ok: false }, { status: false, message: 'Invalid recipient code' }) === false],
  ['success envelope without transfer details is unresolved and stays pending', payoutInitializationOutcomeIsUncertain({ status: 200, ok: true }, { status: true, data: null })],
];
const transferVerificationFailures = transferVerificationChecks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of transferVerificationChecks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (transferVerificationFailures.length) throw new Error(`Payout verification behavior gate failed: ${transferVerificationFailures.join('; ')}`);
console.log(`Payout verification behavior gate passed (${transferVerificationChecks.length} cases)`);
console.log(`Batch 2 financial hardening gate passed (${checks.length} structural checks, ${refundIdentityChecks.length} refund identity cases, ${payoutIdentityChecks.length} post-success payout identity cases, ${transferVerificationChecks.length} payout verification cases, and ${payoutProgressChecks.length} resweep cases)`);

