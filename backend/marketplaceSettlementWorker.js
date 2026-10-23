import pg from 'pg';

const { Pool } = pg;

const DATABASE_URL = process.env.DATABASE_URL || '';
const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY || '';
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
  max: 4,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
  query_timeout: 20_000,
  keepAlive: true
}) : null;

function providerHeaders() {
  return {
    Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
    'Content-Type': 'application/json'
  };
}

async function paystackJson(url, options = {}) {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => ({}));
  return { response, data };
}

export function payoutReferenceForOperation(operation) {
  const orderId = String(operation?.order_id || '');
  const operationId = String(operation?.id || '');
  if (!orderId || !operationId) throw new Error('payout operation requires order and operation IDs');
  // Preserve the original first-attempt reference for in-flight payouts from
  // older deployments. Each subsequent operation gets a separate provider ref.
  return String(operation?.idempotency_key || '') === `PAYOUT-${orderId}`
    ? `fynx-payout-${orderId}`
    : `fynx-payout-${operationId}`;
}
export function classifyPaystackTransferVerification(response, data, expectedReference) {
  // Paystack documents HTTP 404 as transfer not found. Other failed or
  // indeterminate responses must not be interpreted as proof that no transfer
  // exists, or a transient/auth error could trigger another payout attempt.
  if (response?.status === 404) return { found: false, response, data };
  if (response?.ok && data?.status === true && data?.data) {
    if (String(data.data.reference || '') !== String(expectedReference || '')) {
      throw new Error('Paystack transfer verification returned a different reference');
    }
    return { found: true, response, data };
  }
  throw new Error(data?.message || 'Paystack transfer verification failed (' + String(response?.status ?? 'unknown') + ')');
}

async function verifyTransferByReference(reference) {
  const cleanReference = String(reference || '').trim();
  if (!cleanReference) return { found: false };
  const url = 'https://api.paystack.co/transfer/verify/' + encodeURIComponent(cleanReference);
  const { response, data } = await paystackJson(url, { headers: providerHeaders() });
  return classifyPaystackTransferVerification(response, data, cleanReference);
}

export function nextPayoutVerificationProgress(metadata = {}) {
  const rawAttempts = Number(metadata?.verificationAttempts || 0);
  const priorAttempts = Number.isFinite(rawAttempts) ? Math.max(0, Math.trunc(rawAttempts)) : 0;
  const nextAttempt = priorAttempts + 1;
  const cycleComplete = nextAttempt >= 12;
  return {
    attempt: nextAttempt,
    shouldContinue: !cycleComplete,
    verificationAttempts: cycleComplete ? 0 : nextAttempt,
    verificationCycles: (Number.isFinite(Number(metadata?.verificationCycles)) ? Math.max(0, Math.trunc(Number(metadata?.verificationCycles))) : 0) + (cycleComplete ? 1 : 0)
  };
}

export function payoutSweepIdempotencyKey(kind, operationId, nowMs = Date.now()) {
  const bucket = Math.floor(Number(nowMs) / 120_000);
  return `${String(kind)}-SWEEP-${String(operationId)}-${bucket}`;
}

export function payoutVerificationJobIdempotencyKey(operationId, cycle, attempt) {
  const cleanCycle = String(cycle || 'initial').slice(0, 100);
  const cleanAttempt = Math.max(0, Math.trunc(Number(attempt) || 0));
  return `VERIFY-CYCLE-${String(operationId)}-${cleanCycle}-${cleanAttempt}`;
}
function transferMatchesOperation(transfer, operation) {
  const expectedAmount = Math.round(Number(operation.amount) * 100);
  const providerAmount = Number(transfer?.amount);
  const expectedCurrency = String(operation.currency || '').toUpperCase();
  const providerCurrency = String(transfer?.currency || '').toUpperCase();
  return Number.isFinite(expectedAmount) && expectedAmount > 0 && providerAmount === expectedAmount && providerCurrency === expectedCurrency;
}

async function markPayoutFailed(client, operation, reason) {
  const safeReason = String(reason || 'provider transfer failed').slice(0, 2_000);
  await client.query(`UPDATE marketplace_financial_operations SET status='FAILED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [safeReason, operation.id]);
  await client.query(`UPDATE marketplace_escrows SET status='RELEASE_ELIGIBLE',updated_at=NOW() WHERE order_id=$1 AND status='RELEASE_PENDING'`, [operation.order_id]);
}

async function markPayoutSucceeded(client, operation, providerReference, providerStatus) {
  const escrow = (await client.query(`SELECT id,amount,currency,status FROM marketplace_escrows WHERE order_id=$1 FOR UPDATE`, [operation.order_id])).rows[0];
  if (!escrow || escrow.status !== 'RELEASE_PENDING') return;
  const operationAmount = Number(operation.amount);
  const escrowAmount = Number(escrow.amount);
  const marketplaceFee = Number(operation.metadata?.marketplaceFee || 0);
  if (!Number.isFinite(operationAmount) || !Number.isFinite(escrowAmount) || !Number.isFinite(marketplaceFee) || operationAmount <= 0 || marketplaceFee < 0 || Math.round((operationAmount + marketplaceFee) * 100) / 100 !== escrowAmount || String(operation.currency).toUpperCase() !== String(escrow.currency).toUpperCase()) {
    await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, ['payout settlement accounting does not reconcile with the protected escrow', operation.id]);
    await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE id=$1 AND status='RELEASE_PENDING'`, [escrow.id]);
    return;
  }
  await client.query(`UPDATE marketplace_financial_operations SET status='SUCCEEDED',provider_reference=$1,failure_reason=NULL,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [providerReference, operation.id]);
  await client.query(`UPDATE marketplace_escrows SET status='RELEASED',released_at=NOW(),updated_at=NOW() WHERE id=$1 AND status='RELEASE_PENDING'`, [escrow.id]);
  await client.query(
    `INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata)
     VALUES ($1,$2,'seller_payout','RELEASE',$3,$4,$5,$6::jsonb) ON CONFLICT (idempotency_key) DO NOTHING`,
    [escrow.id, operation.order_id, operationAmount, escrow.currency, `PAYOUT-RELEASE-${operation.order_id}`, JSON.stringify({ provider: 'paystack', providerReference, providerStatus, protectedAmount: escrowAmount, sellerNetAmount: operationAmount, marketplaceFee })]
  );
  if (marketplaceFee > 0) {
    await client.query(
      `INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata)
       VALUES ($1,$2,'fynx_marketplace_fee','FEE',$3,$4,$5,$6::jsonb) ON CONFLICT (idempotency_key) DO NOTHING`,
      [escrow.id, operation.order_id, marketplaceFee, escrow.currency, `MARKETPLACE-FEE-${operation.order_id}`, JSON.stringify({ provider: 'paystack', providerReference, feePolicy: operation.metadata?.feePolicy || null, feePolicyVersion: operation.metadata?.feePolicyVersion || null })]
    );
  }
}

async function initiatePayout(payload) {
  if (!pool) throw new Error('DATABASE_URL is not configured');
  if (!PAYSTACK_SECRET_KEY) throw new Error('PAYSTACK_SECRET_KEY is not configured');
  const operationId = String(payload?.operationId || '');
  if (!operationId) throw new Error('payout operation id is required');

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const operation = (await client.query(`SELECT * FROM marketplace_financial_operations WHERE id=$1 FOR UPDATE`, [operationId])).rows[0];
    if (!operation) { await client.query('ROLLBACK'); return; }
    if (operation.status === 'SUCCEEDED') { await client.query('COMMIT'); return; }
    if (operation.status !== 'PENDING') { await client.query('COMMIT'); return; }
    if (operation.provider_reference) { await client.query('COMMIT'); return; }

    const escrow = (await client.query(`SELECT * FROM marketplace_escrows WHERE order_id=$1 FOR UPDATE`, [operation.order_id])).rows[0];
    if (!escrow || escrow.status !== 'RELEASE_PENDING') {
      await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, ['escrow is not release-pending', operation.id]);
      await client.query('COMMIT');
      return;
    }

    const operationAmount = Number(operation.amount);
    const escrowAmount = Number(escrow.amount);
    const marketplaceFee = Number(operation.metadata?.marketplaceFee || 0);
    if (!Number.isFinite(operationAmount) || !Number.isFinite(escrowAmount) || !Number.isFinite(marketplaceFee) || operationAmount <= 0 || marketplaceFee < 0 || Math.round((operationAmount + marketplaceFee) * 100) / 100 !== escrowAmount || String(operation.currency).toUpperCase() !== String(escrow.currency).toUpperCase()) {
      await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, ['payout amount, marketplace fee, or currency does not reconcile with the protected escrow', operation.id]);
      await client.query('COMMIT');
      return;
    }

    const refundConflict = (await client.query(`SELECT 1 FROM marketplace_financial_operations WHERE order_id=$1 AND operation_type='REFUND' AND status IN ('PENDING','SUCCEEDED') LIMIT 1`, [operation.order_id])).rowCount > 0;
    if (refundConflict) {
      await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, ['refund financial operation is active for this order', operation.id]);
      await client.query('COMMIT');
      return;
    }

    const recipientCode = String(operation.metadata?.recipientCode || '');
    if (!recipientCode) {
      await markPayoutFailed(client, operation, 'verified seller payout recipient is missing');
      await client.query('COMMIT');
      return;
    }
    if (!['NGN','USD'].includes(String(operation.currency || '').toUpperCase())) {
      await markPayoutFailed(client, operation, 'Marketplace payout currency is not active');
      await client.query('COMMIT');
      return;
    }
    await client.query('COMMIT');

    // Crash recovery: before creating a transfer, verify the deterministic reference.
    // If Paystack accepted the previous attempt but FYNX crashed before saving the
    // provider reference, this discovers the existing transfer instead of creating a
    // second transfer. Paystack explicitly recommends retrying with the same reference.
    const reference = payoutReferenceForOperation(operation);
    let existingTransfer = await verifyTransferByReference(reference);
    let response;
    let data;
    if (existingTransfer.found) {
      response = existingTransfer.response;
      data = existingTransfer.data;
    } else {
      ({ response, data } = await paystackJson('https://api.paystack.co/transfer', {
        method: 'POST',
        headers: providerHeaders(),
        body: JSON.stringify({ source: 'balance', amount: Math.round(operationAmount * 100), recipient: recipientCode, currency: String(operation.currency).toUpperCase(), reason: `FYNX marketplace payout ${operation.order_id}`, reference })
      }));
    }

    const providerReference = String(data?.data?.reference || reference);
    const providerStatus = String(data?.data?.status || '').toLowerCase();
    const providerDataMatches = transferMatchesOperation(data?.data, operation);
    const success = response.ok && data?.status === true && providerDataMatches && ['success', 'successful'].includes(providerStatus);
    const accepted = response.ok && data?.status === true && providerDataMatches && ['pending', 'otp', 'queued', 'processing'].includes(providerStatus);

    const finishClient = await pool.connect();
    try {
      await finishClient.query('BEGIN');
      const current = (await finishClient.query(`SELECT * FROM marketplace_financial_operations WHERE id=$1 FOR UPDATE`, [operation.id])).rows[0];
      if (!current || current.status !== 'PENDING') { await finishClient.query('COMMIT'); return; }
      if (!providerDataMatches && response.ok && data?.status === true) {
        await finishClient.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'`, ['Paystack payout amount or currency does not match the protected operation', JSON.stringify({ providerReference, providerStatus, providerAmount: data?.data?.amount ?? null, providerCurrency: data?.data?.currency ?? null }), current.id]);
        await finishClient.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE order_id=$1 AND status='RELEASE_PENDING'`, [current.order_id]);
      } else if (success) {
        await markPayoutSucceeded(finishClient, current, providerReference, providerStatus);
      } else if (accepted) {
        await finishClient.query(`UPDATE marketplace_financial_operations SET provider_reference=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'`, [providerReference, JSON.stringify({ providerStatus, initiatedAt: new Date().toISOString(), recoveryCheckedFirst: existingTransfer.found }), current.id]);
      } else {
        await markPayoutFailed(finishClient, current, data?.message || `Paystack transfer failed (${response.status})`);
      }
      await finishClient.query('COMMIT');
    } catch (error) {
      await finishClient.query('ROLLBACK').catch(() => {});
      throw error;
    } finally {
      finishClient.release();
    }
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

async function verifyPayout(payload) {
  if (!pool) throw new Error('DATABASE_URL is not configured');
  if (!PAYSTACK_SECRET_KEY) throw new Error('PAYSTACK_SECRET_KEY is not configured');
  const operationId = String(payload?.operationId || '');
  if (!operationId) throw new Error('payout operation id is required');
  const client = await pool.connect();
  try {
    const operation = (await client.query(`SELECT * FROM marketplace_financial_operations WHERE id=$1`, [operationId])).rows[0];
    if (!operation || operation.status !== 'PENDING' || !operation.provider_reference) return false;
    const { response, data } = await paystackJson(`https://api.paystack.co/transfer/verify/${encodeURIComponent(operation.provider_reference)}`, { headers: providerHeaders() });
    const verification = classifyPaystackTransferVerification(response, data, operation.provider_reference);
    const verifiedTransfer = verification.found ? verification.data.data : null;
    const providerStatus = String(verifiedTransfer?.status || '').toLowerCase();
    await client.query('BEGIN');
    const current = (await client.query(`SELECT * FROM marketplace_financial_operations WHERE id=$1 FOR UPDATE`, [operation.id])).rows[0];
    if (!current || current.status !== 'PENDING') { await client.query('COMMIT'); return false; }
    if (verification.found && !transferMatchesOperation(verifiedTransfer, current)) {
      await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, ['Paystack verified payout amount or currency does not match the protected operation', current.id]);
      await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE order_id=$1 AND status='RELEASE_PENDING'`, [current.order_id]);
      await client.query('COMMIT');
      return false;
    }
    if (verification.found && ['success', 'successful'].includes(providerStatus)) {
      await markPayoutSucceeded(client, current, current.provider_reference, providerStatus);
      await client.query('COMMIT');
      return false;
    }
    if (verification.found && ['failed', 'reversed', 'rejected'].includes(providerStatus)) {
      await markPayoutFailed(client, current, `Paystack transfer status: ${providerStatus}`);
      await client.query('COMMIT');
      return false;
    }
    if (verification.found) {
      const progress = nextPayoutVerificationProgress(current.metadata || {});
      await client.query(`UPDATE marketplace_financial_operations SET metadata=metadata || $1::jsonb,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [JSON.stringify({ providerStatus, verificationAttempts: progress.verificationAttempts, verificationCycles: progress.verificationCycles, lastVerifiedAt: new Date().toISOString() }), current.id]);
      await client.query('COMMIT');
      return progress.shouldContinue;
    }
    await client.query('COMMIT');
    return true;
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

export function registerMarketplaceSettlementWorker({ jobs, logger = console }) {
  if (!jobs?.enabled || typeof jobs.enqueue !== 'function') return;
  globalThis.__fynxJobHandlers = globalThis.__fynxJobHandlers || {};
  globalThis.__fynxJobHandlers['marketplace.payout.release'] = async (payload) => {
    await initiatePayout(payload);
    if (payload?.operationId) {
      const operationId = String(payload.operationId);
      const verificationCycle = String(payload?.verificationCycle || 'initial').slice(0, 100);
      await jobs.enqueue('marketplace.payout.verify', { operationId, attempt: 0, verificationCycle }, { idempotencyKey: payoutVerificationJobIdempotencyKey(operationId, verificationCycle, 0), delayMs: 5_000, maxAttempts: 5 });
    }
  };
  globalThis.__fynxJobHandlers['marketplace.payout.verify'] = async (payload) => {
    const operationId = String(payload?.operationId || '');
    if (!operationId) return;
    const shouldContinue = await verifyPayout(payload);
    if (!shouldContinue) return;
    const attempt = Math.max(0, Number(payload?.attempt || 0)) + 1;
    if (attempt > 11) return;
    const verificationCycle = String(payload?.verificationCycle || 'initial').slice(0, 100);
    await jobs.enqueue('marketplace.payout.verify', { operationId, attempt, verificationCycle }, { idempotencyKey: payoutVerificationJobIdempotencyKey(operationId, verificationCycle, attempt), delayMs: 10_000, maxAttempts: 3 });
  };

  const scan = async () => {
    if (!pool) return;
    try {
      const pending = await pool.query(`
        SELECT fo.id,fo.provider_reference,fo.updated_at
        FROM marketplace_financial_operations fo
        JOIN marketplace_escrows e ON e.order_id=fo.order_id
        WHERE fo.operation_type='PAYOUT_RELEASE'
          AND fo.status='PENDING'
          AND e.status='RELEASE_PENDING'
          AND fo.updated_at <= NOW() - INTERVAL '2 minutes'
        ORDER BY fo.updated_at ASC
        LIMIT 20`);
      const bucketTime = Date.now();
      for (const row of pending.rows) {
        const operationId = String(row.id);
        if (row.provider_reference) {
          // A verification chain can exhaust its finite queue attempts while the
          // provider transfer is still pending. Enqueue a fresh bounded sweep
          // after inactivity; the same bucket is idempotent across scan ticks.
          const verificationCycle = payoutSweepIdempotencyKey('VERIFY', operationId, bucketTime);
          await jobs.enqueue('marketplace.payout.verify', { operationId, attempt: 0, verificationCycle }, { idempotencyKey: verificationCycle, maxAttempts: 5 });
        } else {
          // A previous release job may be completed/dead while the operation
          // remains pending. Time-bucketed keys allow safe re-reconciliation;
          // initiatePayout verifies the deterministic provider reference first.
          const verificationCycle = payoutSweepIdempotencyKey('RELEASE', operationId, bucketTime);
          await jobs.enqueue('marketplace.payout.release', { operationId, verificationCycle }, { idempotencyKey: verificationCycle, maxAttempts: 5 });
        }
      }
    } catch (error) {
      logger.error('[fynx-marketplace] payout reconciliation failed', error?.message || error);
    }
  };
  const timer = setInterval(() => { void scan(); }, 15_000);
  timer.unref();
  void scan();
  logger.log('[fynx-marketplace] settlement worker handlers registered');
}

export async function closeMarketplaceSettlementWorker() {
  if (pool) await pool.end();
}
