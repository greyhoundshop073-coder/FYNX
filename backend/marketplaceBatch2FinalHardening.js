import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const PAYSTACK_TIMEOUT_MS = Math.max(5_000, Number(process.env.PAYSTACK_REQUEST_TIMEOUT_MS || 15_000));
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 2, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 15_000, query_timeout: 20_000, keepAlive: true }) : null;

let fetchInstalled = false;

function timedPaystackFetch(originalFetch, input, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PAYSTACK_TIMEOUT_MS);
  const inputUrl = typeof input === 'string' ? input : input?.url || '';
  const signal = options.signal && typeof AbortSignal.any === 'function'
    ? AbortSignal.any([options.signal, controller.signal])
    : controller.signal;
  return originalFetch(input, { ...options, signal }).catch(error => {
    if (controller.signal.aborted && !(options.signal?.aborted)) {
      const timeout = new Error(`Paystack request timed out after ${PAYSTACK_TIMEOUT_MS}ms: ${inputUrl}`);
      timeout.code = 'PAYSTACK_TIMEOUT';
      timeout.retryable = true;
      throw timeout;
    }
    throw error;
  }).finally(() => clearTimeout(timer));
}

function installPaystackTimeoutGuard() {
  if (fetchInstalled || typeof globalThis.fetch !== 'function') return;
  const originalFetch = globalThis.fetch.bind(globalThis);
  globalThis.fetch = (input, options = {}) => {
    const url = typeof input === 'string' ? input : input?.url || '';
    if (!/^https:\/\/api\.paystack\.co\//i.test(url)) return originalFetch(input, options);
    return timedPaystackFetch(originalFetch, input, options);
  };
  fetchInstalled = true;
}

async function insertRecoveryEvent(client, row, action, metadata) {
  await client.query(`
    INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata)
    SELECT $1,NULL,$2,$3,$4,$5::jsonb
    WHERE NOT EXISTS (
      SELECT 1 FROM marketplace_order_events
      WHERE order_id=$1 AND event_type=$2
        AND metadata->>'operationId'=COALESCE($6,'')
    )
  `, [row.order_id, action, row.order_status || null, row.order_status || null, JSON.stringify({ ...metadata, operationId: String(row.id) }), String(row.id)]);
}

async function reconcileRefundRecovery({ logger = console } = {}) {
  if (!pool) return;
  const rows = await pool.query(`
    SELECT f.id,f.order_id,f.amount,f.currency,f.status AS operation_status,f.metadata,
           e.id AS escrow_id,e.amount AS escrow_amount,e.currency AS escrow_currency,e.status AS escrow_status,
           o.status AS order_status
    FROM marketplace_financial_operations f
    JOIN marketplace_escrows e ON e.order_id=f.order_id
    JOIN marketplace_orders o ON o.id=f.order_id
    WHERE f.operation_type='REFUND'
      AND f.status='SUCCEEDED'
      AND e.status='REFUND_PENDING'
    ORDER BY f.updated_at ASC
    LIMIT 50
  `);
  for (const row of rows.rows) {
    const operationAmount = Number(row.amount);
    const escrowAmount = Number(row.escrow_amount);
    const operationCurrency = String(row.currency || '').toUpperCase();
    const escrowCurrency = String(row.escrow_currency || '').toUpperCase();
    if (!Number.isFinite(operationAmount) || !Number.isFinite(escrowAmount) || operationAmount <= 0 || Math.round(operationAmount * 100) / 100 !== Math.round(escrowAmount * 100) / 100 || operationCurrency !== escrowCurrency) {
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='SUCCEEDED'`, ['refund recovery amount or currency does not reconcile with the protected escrow', row.id]);
        await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE id=$1 AND status='REFUND_PENDING'`, [row.escrow_id]);
        await insertRecoveryEvent(client, row, 'REFUND_RECOVERY_BLOCKED', { reason: 'refund_amount_or_currency_mismatch', operationAmount, escrowAmount, operationCurrency, escrowCurrency });
        await client.query('COMMIT');
      } catch (error) { await client.query('ROLLBACK').catch(() => {}); logger.error('[fynx-marketplace] refund recovery guard failed', error?.message || error); }
      finally { client.release(); }
    }
  }
}

async function auditCompletedRecovery({ logger = console } = {}) {
  if (!pool) return;
  const rows = await pool.query(`
    SELECT f.id,f.order_id,f.operation_type,f.provider_reference,f.amount,f.currency,
           e.amount AS escrow_amount,e.currency AS escrow_currency,e.status AS escrow_status,
           o.status AS order_status
    FROM marketplace_financial_operations f
    JOIN marketplace_escrows e ON e.order_id=f.order_id
    JOIN marketplace_orders o ON o.id=f.order_id
    WHERE f.status='SUCCEEDED'
      AND ((f.operation_type='PAYOUT_RELEASE' AND e.status='RELEASED') OR (f.operation_type='REFUND' AND e.status='REFUNDED'))
      AND f.updated_at >= NOW() - INTERVAL '24 hours'
    ORDER BY f.updated_at DESC
    LIMIT 100
  `);
  for (const row of rows.rows) {
    const action = row.operation_type === 'PAYOUT_RELEASE' ? 'PAYOUT_RECOVERY_RECONCILED' : 'REFUND_RECOVERY_RECONCILED';
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      await insertRecoveryEvent(client, row, action, { providerReference: row.provider_reference || null, amount: Number(row.amount), currency: String(row.currency || '').toUpperCase(), escrowAmount: Number(row.escrow_amount), escrowCurrency: String(row.escrow_currency || '').toUpperCase(), source: 'batch2-final-recovery-audit' });
      await client.query('COMMIT');
    } catch (error) { await client.query('ROLLBACK').catch(() => {}); logger.error('[fynx-marketplace] recovery audit failed', error?.message || error); }
    finally { client.release(); }
  }
}

export function registerMarketplaceBatch2FinalHardening({ logger = console } = {}) {
  installPaystackTimeoutGuard();
  if (!pool) return;
  const run = async () => {
    await reconcileRefundRecovery({ logger });
    await auditCompletedRecovery({ logger });
  };
  const timer = setInterval(() => { void run(); }, 30_000);
  timer.unref();
  void run();
  logger.log(`[fynx-marketplace] Batch 2 final hardening active; Paystack timeout=${PAYSTACK_TIMEOUT_MS}ms`);
}
