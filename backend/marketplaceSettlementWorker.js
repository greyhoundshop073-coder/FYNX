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

async function markPayoutFailed(client, operation, reason) {
  const safeReason = String(reason || 'provider transfer failed').slice(0, 2_000);
  await client.query(`UPDATE marketplace_financial_operations SET status='FAILED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [safeReason, operation.id]);
  await client.query(`UPDATE marketplace_escrows SET status='RELEASE_ELIGIBLE',updated_at=NOW() WHERE order_id=$1 AND status='RELEASE_PENDING'`, [operation.order_id]);
}

async function markPayoutSucceeded(client, operation, providerReference, providerStatus) {
  await client.query(`UPDATE marketplace_financial_operations SET status='SUCCEEDED',provider_reference=$1,failure_reason=NULL,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [providerReference, operation.id]);
  const escrow = (await client.query(`SELECT id,amount,currency,status FROM marketplace_escrows WHERE order_id=$1 FOR UPDATE`, [operation.order_id])).rows[0];
  if (!escrow || escrow.status !== 'RELEASE_PENDING') return;
  await client.query(`UPDATE marketplace_escrows SET status='RELEASED',released_at=NOW(),updated_at=NOW() WHERE id=$1`, [escrow.id]);
  await client.query(
    `INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata)
     VALUES ($1,$2,'seller_payout','RELEASE',$3,$4,$5,$6::jsonb) ON CONFLICT (idempotency_key) DO NOTHING`,
    [escrow.id, operation.order_id, escrow.amount, escrow.currency, `PAYOUT-RELEASE-${operation.order_id}`, JSON.stringify({ provider: 'paystack', providerReference, providerStatus })]
  );
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
    if (operation.provider_reference) {
      await client.query('COMMIT');
      return;
    }

    const escrow = (await client.query(`SELECT * FROM marketplace_escrows WHERE order_id=$1 FOR UPDATE`, [operation.order_id])).rows[0];
    if (!escrow || escrow.status !== 'RELEASE_PENDING') {
      await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, ['escrow is not release-pending', operation.id]);
      await client.query('COMMIT');
      return;
    }

    const recipientCode = String(operation.metadata?.recipientCode || '');
    if (!recipientCode) {
      await markPayoutFailed(client, operation, 'verified seller payout recipient is missing');
      await client.query('COMMIT');
      return;
    }
    if (String(operation.currency || '').toUpperCase() !== 'NGN') {
      await markPayoutFailed(client, operation, 'Paystack seller payouts currently support NGN only');
      await client.query('COMMIT');
      return;
    }
    await client.query('COMMIT');

    const reference = `FYNX-PAYOUT-${operation.order_id}`;
    const { response, data } = await paystackJson('https://api.paystack.co/transfer', {
      method: 'POST',
      headers: providerHeaders(),
      body: JSON.stringify({ source: 'balance', amount: Math.round(Number(operation.amount) * 100), recipient: recipientCode, reason: `FYNX marketplace payout ${operation.order_id}`, reference })
    });
    const providerReference = String(data?.data?.reference || data?.data?.transfer_code || reference);
    const providerStatus = String(data?.data?.status || '').toLowerCase();
    const success = response.ok && data?.status === true && ['success', 'successful'].includes(providerStatus);
    const accepted = response.ok && data?.status === true && ['pending', 'otp', 'queued', 'processing'].includes(providerStatus);

    const finishClient = await pool.connect();
    try {
      await finishClient.query('BEGIN');
      const current = (await finishClient.query(`SELECT * FROM marketplace_financial_operations WHERE id=$1 FOR UPDATE`, [operation.id])).rows[0];
      if (!current || current.status !== 'PENDING') { await finishClient.query('COMMIT'); return; }
      if (success) {
        await markPayoutSucceeded(finishClient, current, providerReference, providerStatus);
      } else if (accepted) {
        await finishClient.query(`UPDATE marketplace_financial_operations SET provider_reference=$1,metadata=metadata || $2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'`, [providerReference, JSON.stringify({ providerStatus, initiatedAt: new Date().toISOString() }), current.id]);
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
    const providerStatus = String(data?.data?.status || '').toLowerCase();
    await client.query('BEGIN');
    const current = (await client.query(`SELECT * FROM marketplace_financial_operations WHERE id=$1 FOR UPDATE`, [operation.id])).rows[0];
    if (!current || current.status !== 'PENDING') { await client.query('COMMIT'); return false; }
    if (response.ok && data?.status === true && ['success', 'successful'].includes(providerStatus)) {
      await markPayoutSucceeded(client, current, current.provider_reference, providerStatus);
      await client.query('COMMIT');
      return false;
    }
    if (response.ok && data?.status === true && ['failed', 'reversed', 'rejected'].includes(providerStatus)) {
      await markPayoutFailed(client, current, `Paystack transfer status: ${providerStatus}`);
      await client.query('COMMIT');
      return false;
    }
    if (response.ok && data?.status === true) {
      const attempts = Number(current.metadata?.verificationAttempts || 0) + 1;
      await client.query(`UPDATE marketplace_financial_operations SET metadata=metadata || $1::jsonb,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [JSON.stringify({ providerStatus, verificationAttempts: attempts, lastVerifiedAt: new Date().toISOString() }), current.id]);
      await client.query('COMMIT');
      return attempts < 12;
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
    if (payload?.operationId) await jobs.enqueue('marketplace.payout.verify', { operationId: String(payload.operationId) }, { idempotencyKey: `VERIFY-${String(payload.operationId)}-0`, delayMs: 5_000, maxAttempts: 5 });
  };
  globalThis.__fynxJobHandlers['marketplace.payout.verify'] = async (payload) => {
    const operationId = String(payload?.operationId || '');
    if (!operationId) return;
    const shouldContinue = await verifyPayout(payload);
    if (!shouldContinue) return;
    const attempt = Math.max(0, Number(payload?.attempt || 0)) + 1;
    if (attempt > 11) return;
    await jobs.enqueue('marketplace.payout.verify', { operationId, attempt }, { idempotencyKey: `VERIFY-${operationId}-${attempt}`, delayMs: 10_000, maxAttempts: 3 });
  };

  const scan = async () => {
    if (!pool) return;
    try {
      const pending = await pool.query(`
        SELECT fo.id
        FROM marketplace_financial_operations fo
        JOIN marketplace_escrows e ON e.order_id=fo.order_id
        WHERE fo.operation_type='PAYOUT_RELEASE'
          AND fo.status='PENDING'
          AND e.status='RELEASE_PENDING'
        ORDER BY fo.created_at ASC
        LIMIT 20`);
      for (const row of pending.rows) {
        await jobs.enqueue('marketplace.payout.release', { operationId: String(row.id) }, { idempotencyKey: `RELEASE-${String(row.id)}`, maxAttempts: 5 });
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
