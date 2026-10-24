import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const worker = read('./marketplaceSettlementWorker.js');
const webhook = read('./marketplacePaystackWebhook.js');
const settlement = read('./marketplaceSettlement.js');
const payoutRetry = read('./marketplacePayoutRetry.js');
const checkout = read('./marketplaceCheckout.js');
const checkoutOrder = read('./marketplaceCheckoutOrder.js');

const checks = [
  ['first payout attempt preserves deterministic recovery reference while each retry has a unique operation-bound reference', worker.includes('payoutReferenceForOperation(operation)') && worker.includes('`fynx-payout-${orderId}`') && worker.includes('`fynx-payout-${operationId}`')],
  ['provider-reference duplicate guard', worker.includes("if (operation.provider_reference)")],
  ['release-pending escrow gate', worker.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['pending payout recovery scan retries stale operations with bucketed jobs and separate verify/release paths', worker.includes("fo.operation_type='PAYOUT_RELEASE'") && worker.includes("fo.status='PENDING'") && worker.includes("fo.updated_at <= NOW() - INTERVAL '2 minutes'") && worker.includes("payoutSweepIdempotencyKey('VERIFY', operationId, bucketTime)") && worker.includes("payoutSweepIdempotencyKey('RELEASE', operationId, bucketTime)")],
  ['verify deterministic transfer before create', worker.includes('verifyTransferByReference(reference)') && worker.includes('recoveryCheckedFirst')],
  ['provider transfer verification', worker.includes('/transfer/verify/')],
  ['provider reference/amount/currency payout validation', worker.includes('transferMatchesOperation(data?.data, operation, reference)') && worker.includes('providerReference === String(expectedReference).trim()') && worker.includes('Paystack payout reference, amount, or currency does not match')],
  ['idempotent payout ledger', worker.includes('PAYOUT-RELEASE-${operation.order_id}') && worker.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['payout amount reconciles seller net plus marketplace fee', worker.includes('operationAmount + marketplaceFee') && worker.includes('Math.round((operationAmount + marketplaceFee) * 100) / 100 !== escrowAmount')],
  ['payout currency matches escrow', worker.includes("String(operation.currency).toUpperCase() !== String(escrow.currency).toUpperCase()")],
  ['blocked and unresolved failed refunds block payout; only verified provider failure is terminal', worker.includes("operation_type='REFUND'") && worker.includes("status IN ('PENDING','SUCCEEDED','BLOCKED')") && worker.includes("COALESCE(metadata->>'refundEvent','') <> 'refund.failed'") && worker.includes("COALESCE(metadata->>'refundRecoveryStatus','') <> 'failed'") && worker.includes('refund financial operation is unresolved for this order')],
  ['transfer success webhook', webhook.includes("eventName === 'transfer.success'")],
  ['transfer failure/reversal webhook', webhook.includes("eventName === 'transfer.failed'") && webhook.includes("eventName === 'transfer.reversed'")],
  ['payment webhook amount validation', webhook.includes('orderExpectedAmount') && webhook.includes('paidAmount') && webhook.includes('expectedAmount')],
  ['payment webhook currency validation', webhook.includes("paidCurrency !== String(order.currency).toUpperCase()")],
  ['refund amount validation', webhook.includes('providerAmount !== expectedAmount') && webhook.includes('providerCurrency !== expectedCurrency')],
  ['refund invalid data blocks protected funds', webhook.includes("status='BLOCKED'") && webhook.includes("status='DISPUTED'") && webhook.includes('refund webhook amount or currency')],
  ['refund remains asynchronous and idempotent', webhook.includes("eventName.startsWith('refund.')") && webhook.includes("eventName === 'refund.processed'") && webhook.includes("eventName === 'refund.failed'") && webhook.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['financial operation provider reference uniqueness', settlement.includes('marketplace_fin_ops_provider_ref_idx')],
  ['ledger idempotency uniqueness', settlement.includes('idempotency_key TEXT NOT NULL UNIQUE')],
  ['checkout requires a normalized three-letter listing currency', checkout.includes('normalizeCurrency') && checkout.includes('listing.currency')],
  ['protected order stores the normalized listing currency', checkoutOrder.includes('const currency = normalizeCurrency(listing.currency)') && checkoutOrder.includes('currency,product_snapshot') && checkoutOrder.includes('currency })')],
  ['protected escrow is created from the order currency', settlement.includes('NEW.total_amount,NEW.currency')],
  ['payout account currency is matched to the order currency', read('./marketplacePayoutRetry.js').includes('payoutAccount.currency') && read('./marketplacePayoutRetry.js').includes('order.currency')],
  ['dedicated failed payout retry endpoint requires release-eligible escrow and a previously failed attempt', payoutRetry.includes("app.post('/api/marketplace/settlement/retry/:id'") && payoutRetry.includes("escrow.status !== 'RELEASE_ELIGIBLE'") && payoutRetry.includes("status='FAILED'") && payoutRetry.includes('PAYOUT-RETRY-${failed.id}')],
  ['failed payout retry requires a completed order and matching escrow currency', payoutRetry.includes("order.status !== 'COMPLETED'") && payoutRetry.includes('order and escrow currency do not match') && payoutRetry.includes('sellerNetAmount + marketplaceFee')],
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace reconciliation guard FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}

console.log(`Marketplace reconciliation guard GREEN (${checks.length} checks)`);
