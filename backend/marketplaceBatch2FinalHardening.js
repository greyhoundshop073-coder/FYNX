import pg from 'pg';
import { confirmMarketplacePayment } from './marketplacePaymentState.js';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const PAYSTACK_TIMEOUT_MS = Math.max(5_000, Number(process.env.PAYSTACK_REQUEST_TIMEOUT_MS || 15_000));
const RECOVERY_AGE_MINUTES = 2;
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 2, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 15_000, query_timeout: 20_000, keepAlive: true }) : null;
let fetchInstalled = false;

function timeoutError(url) {
  const error = new Error(`Paystack request timed out after ${PAYSTACK_TIMEOUT_MS}ms: ${url}`);
  error.code = 'PAYSTACK_TIMEOUT';
  error.retryable = true;
  return error;
}

function timedPaystackFetch(originalFetch, input, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PAYSTACK_TIMEOUT_MS);
  const inputUrl = typeof input === 'string' ? input : input?.url || '';
  const signal = options.signal && typeof AbortSignal.any === 'function' ? AbortSignal.any([options.signal, controller.signal]) : controller.signal;
  return originalFetch(input, { ...options, signal }).catch(async error => {
    if (!(controller.signal.aborted && !(options.signal?.aborted))) throw error;
    if (/^https:\/\/api\.paystack\.co\/refund$/i.test(inputUrl) && String(options.method || 'GET').toUpperCase() === 'POST') {
      return new Response(JSON.stringify({ status: true, message: 'Refund request status is pending provider reconciliation', data: { status: 'pending', transaction: null } }), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    throw timeoutError(inputUrl);
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

function amountSubunit(amount, currency) {
  const normalized = String(currency || '').trim().toUpperCase();
  if (!['NGN', 'USD'].includes(normalized)) return null;
  const value = Number(amount);
  if (!Number.isFinite(value) || value <= 0) return null;
  return Math.round(value * 100);
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

async function recoverPaymentWithoutLocalReference({ logger = console } = {}) {
  if (!pool || !process.env.PAYSTACK_SECRET_KEY) return 0;
  const rows = await pool.query(`SELECT id,buyer_id,total_amount,currency,status,payment_reference,updated_at FROM marketplace_orders WHERE status='PAYMENT_PENDING' AND payment_reference IS NULL AND updated_at <= NOW() - ($1::text || ' minutes')::interval ORDER BY updated_at ASC LIMIT 25`, [RECOVERY_AGE_MINUTES]);
  let recovered = 0;
  for (const order of rows.rows) {
    const reference = `FYNX-${order.id}`;
    try {
      const response = await fetch(`https://api.paystack.co/transaction/verify/${encodeURIComponent(reference)}`, { headers: { Authorization: `Bearer ${process.env.PAYSTACK_SECRET_KEY}` } });
      const data = await response.json().catch(() => ({}));
      const transaction = data?.data || {};
      const valid = response.ok && data?.status === true && String(transaction.status || '').toLowerCase() === 'success' && amountSubunit(order.total_amount, order.currency) === Number(transaction.amount) && String(transaction.currency || '').toUpperCase() === String(order.currency || '').toUpperCase() && String(transaction.reference || '') === reference && String(transaction.metadata?.orderId || transaction.metadata?.order_id || '') === String(order.id);
      if (!valid) continue;
      const providerFee = Number.isFinite(Number(transaction.fees)) && Number(transaction.fees) >= 0 ? Math.round((Number(transaction.fees) / 100) * 100) / 100 : 0;
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const locked = (await client.query(`SELECT id,buyer_id,status,payment_reference FROM marketplace_orders WHERE id=$1 FOR UPDATE`, [order.id])).rows[0];
        if (locked?.status === 'PAYMENT_PENDING' && !locked.payment_reference) {
          await client.query(`UPDATE marketplace_orders SET payment_reference=$1,updated_at=NOW() WHERE id=$2 AND status='PAYMENT_PENDING' AND payment_reference IS NULL`, [reference, order.id]);
          await confirmMarketplacePayment(client, { orderId: order.id, buyerId: order.buyer_id, reference, paidAmount: Number(transaction.amount), paidCurrency: String(transaction.currency).toUpperCase(), providerFee, source: 'batch2-timeout-recovery' });
          await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'PAYMENT_INITIALIZATION_RECOVERED','PAYMENT_PENDING','PAID',$3::jsonb)`, [order.id, order.buyer_id, JSON.stringify({ reference, provider: 'paystack', providerStatus: transaction.status, action: 'recovered_provider_success_without_local_reference', providerFee })]);
          recovered += 1;
        }
        await client.query('COMMIT');
      } catch (error) { await client.query('ROLLBACK').catch(() => {}); logger.error('[fynx-marketplace] payment timeout recovery failed', error?.message || error); }
      finally { client.release(); }
    } catch (error) { if (error?.code !== 'PAYSTACK_TIMEOUT') logger.error('[fynx-marketplace] payment reference recovery provider check failed', error?.message || error); }
  }
  if (recovered) logger.log(`[fynx-marketplace] recovered ${recovered} provider-accepted payment(s) without saved references`);
  return recovered;
}

async function reconcilePendingRefunds({ logger = console } = {}) {
  if (!pool || !process.env.PAYSTACK_SECRET_KEY) return 0;
  const rows = await pool.query(`SELECT f.id,f.order_id,f.amount,f.currency,f.status AS operation_status,f.provider_reference,f.metadata,e.id AS escrow_id,e.amount AS escrow_amount,e.currency AS escrow_currency,e.status AS escrow_status,o.status AS order_status,o.payment_reference,o.quantity,o.listing_id FROM marketplace_financial_operations f JOIN marketplace_escrows e ON e.order_id=f.order_id JOIN marketplace_orders o ON o.id=f.order_id WHERE f.operation_type='REFUND' AND f.status='PENDING' AND e.status='REFUND_PENDING' ORDER BY f.updated_at ASC LIMIT 25`);
  let repaired = 0;
  for (const row of rows.rows) {
    if (!row.payment_reference) continue;
    try {
      const transactionResponse = await fetch(`https://api.paystack.co/transaction/verify/${encodeURIComponent(row.payment_reference)}`, { headers: { Authorization: `Bearer ${process.env.PAYSTACK_SECRET_KEY}` } });
      const transactionData = await transactionResponse.json().catch(() => ({}));
      const transactionId = Number(transactionData?.data?.id);
      if (!transactionResponse.ok || transactionData?.status !== true || !Number.isSafeInteger(transactionId) || transactionId <= 0) continue;
      const response = await fetch(`https://api.paystack.co/refund?transaction=${encodeURIComponent(transactionId)}&perPage=50`, { headers: { Authorization: `Bearer ${process.env.PAYSTACK_SECRET_KEY}` } });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || data?.status !== true) continue;
      const refunds = Array.isArray(data.data) ? data.data : data.data ? [data.data] : [];
      const expectedAmount = amountSubunit(row.amount, row.currency);
      const expectedCurrency = String(row.currency || '').toUpperCase();
      const match = refunds.find(refund => Number(refund?.amount) === expectedAmount && String(refund?.currency || '').toUpperCase() === expectedCurrency);
      if (!match) continue;
      const status = String(match.status || '').toLowerCase();
      const providerReference = match.refund_reference || match.id || null;
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const locked = (await client.query(`SELECT f.*,e.id AS escrow_id,e.amount AS escrow_amount,e.currency AS escrow_currency,e.status AS escrow_status,o.status AS order_status,o.quantity,o.listing_id,o.payment_reference FROM marketplace_financial_operations f JOIN marketplace_escrows e ON e.order_id=f.order_id JOIN marketplace_orders o ON o.id=f.order_id WHERE f.id=$1 FOR UPDATE`, [row.id])).rows[0];
        if (!locked || locked.status !== 'PENDING' || locked.escrow_status !== 'REFUND_PENDING') { await client.query('COMMIT'); continue; }
        const metadata = { ...(locked.metadata || {}), refundRecoveryStatus: status, refundReference: providerReference ? String(providerReference) : null, providerAmount: Number(match.amount), providerCurrency: String(match.currency || '').toUpperCase(), providerTransactionId: transactionId, source: 'batch2-refund-reconciliation' };
        if (Number(locked.amount) !== Number(locked.escrow_amount) || String(locked.currency).toUpperCase() !== String(locked.escrow_currency).toUpperCase()) {
          await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,metadata=$2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'`, ['refund recovery amount or currency does not reconcile with the protected escrow', JSON.stringify(metadata), locked.id]);
          await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE id=$1 AND status='REFUND_PENDING'`, [locked.escrow_id]);
          await insertRecoveryEvent(client, locked, 'REFUND_RECOVERY_BLOCKED', { reason: 'refund_amount_or_currency_mismatch' });
        } else if (status === 'processed') {
          await client.query(`UPDATE marketplace_financial_operations SET status='SUCCEEDED',provider_reference=COALESCE($1,provider_reference),failure_reason=NULL,metadata=$2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'`, [providerReference ? String(providerReference) : null, JSON.stringify(metadata), locked.id]);
          await client.query(`UPDATE marketplace_orders SET status='REFUNDED',updated_at=NOW() WHERE id=$1 AND status NOT IN ('COMPLETED','REFUNDED')`, [locked.order_id]);
          const caseId = locked.metadata?.caseId ? String(locked.metadata.caseId) : null;
          if (caseId) await client.query(`UPDATE marketplace_protection_cases SET status='REFUNDED',updated_at=NOW() WHERE id=$1 AND status IN ('OPEN','UNDER_REVIEW')`, [caseId]);
          await client.query(`UPDATE marketplace_escrows SET status='REFUNDED',refunded_at=COALESCE(refunded_at,NOW()),updated_at=NOW() WHERE id=$1 AND status='REFUND_PENDING'`, [locked.escrow_id]);
          if (Number.isInteger(Number(locked.quantity)) && locked.listing_id) await client.query(`UPDATE marketplace_listings SET reserved_quantity=GREATEST(0,reserved_quantity-$1),updated_at=NOW() WHERE id=$2`, [Number(locked.quantity), locked.listing_id]);
          await client.query(`INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata) VALUES ($1,$2,'buyer_refund','REFUND',$3,$4,$5,$6::jsonb) ON CONFLICT (idempotency_key) DO NOTHING`, [locked.escrow_id, locked.order_id, locked.escrow_amount, locked.escrow_currency, `REFUND-${locked.order_id}`, JSON.stringify({ provider: 'paystack', providerReference: providerReference ? String(providerReference) : null, source: 'batch2-refund-reconciliation' })]);
          await insertRecoveryEvent(client, locked, 'REFUND_RECOVERY_RECONCILED', { providerReference: providerReference ? String(providerReference) : null, amount: Number(locked.amount), currency: String(locked.currency).toUpperCase() });
          repaired += 1;
        } else if (status === 'failed') {
          await client.query(`UPDATE marketplace_financial_operations SET status='FAILED',provider_reference=COALESCE($1,provider_reference),failure_reason=$2,metadata=$3::jsonb,updated_at=NOW() WHERE id=$4 AND status='PENDING'`, [providerReference ? String(providerReference) : null, 'Paystack refund failed', JSON.stringify(metadata), locked.id]);
          await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE id=$1 AND status='REFUND_PENDING'`, [locked.escrow_id]);
        } else if (status === 'needs-attention') {
          await client.query(`UPDATE marketplace_financial_operations SET provider_reference=COALESCE($1,provider_reference),failure_reason=NULL,metadata=$2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'`, [providerReference ? String(providerReference) : null, JSON.stringify(metadata), locked.id]);
          await insertRecoveryEvent(client, locked, 'REFUND_NEEDS_ATTENTION', { providerReference: providerReference ? String(providerReference) : null, transactionId, action: 'kept_pending_for_customer_account_details' });
        } else {
          await client.query(`UPDATE marketplace_financial_operations SET provider_reference=COALESCE($1,provider_reference),metadata=$2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'`, [providerReference ? String(providerReference) : null, JSON.stringify(metadata), locked.id]);
        }
        await client.query('COMMIT');
      } catch (error) { await client.query('ROLLBACK').catch(() => {}); logger.error('[fynx-marketplace] pending refund reconciliation failed', error?.message || error); }
      finally { client.release(); }
    } catch (error) { if (error?.code !== 'PAYSTACK_TIMEOUT') logger.error('[fynx-marketplace] pending refund provider check failed', error?.message || error); }
  }
  if (repaired) logger.log(`[fynx-marketplace] reconciled ${repaired} pending refund(s)`);
  return repaired;
}

async function reconcileRefundRecovery({ logger = console } = {}) {
  if (!pool) return;
  const rows = await pool.query(`SELECT f.id,f.order_id,f.amount,f.currency,f.status AS operation_status,f.metadata,e.id AS escrow_id,e.amount AS escrow_amount,e.currency AS escrow_currency,e.status AS escrow_status,o.status AS order_status FROM marketplace_financial_operations f JOIN marketplace_escrows e ON e.order_id=f.order_id JOIN marketplace_orders o ON o.id=f.order_id WHERE f.operation_type='REFUND' AND f.status='SUCCEEDED' AND e.status='REFUND_PENDING' ORDER BY f.updated_at ASC LIMIT 50`);
  for (const row of rows.rows) {
    const operationAmount = Number(row.amount), escrowAmount = Number(row.escrow_amount), operationCurrency = String(row.currency || '').toUpperCase(), escrowCurrency = String(row.escrow_currency || '').toUpperCase();
    if (!Number.isFinite(operationAmount) || !Number.isFinite(escrowAmount) || operationAmount <= 0 || Math.round(operationAmount * 100) / 100 !== Math.round(escrowAmount * 100) / 100 || operationCurrency !== escrowCurrency) {
      const client = await pool.connect();
      try { await client.query('BEGIN'); await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='SUCCEEDED'`, ['refund recovery amount or currency does not reconcile with the protected escrow', row.id]); await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE id=$1 AND status='REFUND_PENDING'`, [row.escrow_id]); await insertRecoveryEvent(client, row, 'REFUND_RECOVERY_BLOCKED', { reason: 'refund_amount_or_currency_mismatch', operationAmount, escrowAmount, operationCurrency, escrowCurrency }); await client.query('COMMIT'); } catch (error) { await client.query('ROLLBACK').catch(() => {}); logger.error('[fynx-marketplace] refund recovery guard failed', error?.message || error); } finally { client.release(); }
    }
  }
}

async function reconcilePostSuccessPayoutReversals({ logger = console } = {}) {
  if (!pool || !process.env.PAYSTACK_SECRET_KEY) return 0;
  const rows = await pool.query(`
    SELECT f.id,f.order_id,f.amount,f.currency,f.provider_reference,f.metadata,f.status AS operation_status,
           e.id AS escrow_id,e.amount AS escrow_amount,e.currency AS escrow_currency,e.status AS escrow_status,
           o.status AS order_status
    FROM marketplace_financial_operations f
    JOIN marketplace_escrows e ON e.order_id=f.order_id
    JOIN marketplace_orders o ON o.id=f.order_id
    WHERE f.operation_type='PAYOUT_RELEASE'
      AND f.status='SUCCEEDED'
      AND e.status='RELEASED'
      AND f.provider_reference IS NOT NULL
    ORDER BY f.updated_at ASC
    LIMIT 25
  `);
  let repaired = 0;
  for (const row of rows.rows) {
    try {
      const response = await fetch(`https://api.paystack.co/transfer/verify/${encodeURIComponent(row.provider_reference)}`, { headers: { Authorization: `Bearer ${process.env.PAYSTACK_SECRET_KEY}` } });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || data?.status !== true || !data?.data) continue;
      const transfer = data.data;
      const providerStatus = String(transfer.status || '').toLowerCase();
      const expectedAmount = amountSubunit(row.amount, row.currency);
      const providerAmount = Number(transfer.amount);
      const providerCurrency = String(transfer.currency || '').toUpperCase();
      const expectedCurrency = String(row.currency || '').toUpperCase();
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const locked = (await client.query(`
          SELECT f.id,f.order_id,f.amount,f.currency,f.provider_reference,f.metadata,f.status AS operation_status,
                 e.id AS escrow_id,e.amount AS escrow_amount,e.currency AS escrow_currency,e.status AS escrow_status,
                 o.status AS order_status
          FROM marketplace_financial_operations f
          JOIN marketplace_escrows e ON e.order_id=f.order_id
          JOIN marketplace_orders o ON o.id=f.order_id
          WHERE f.id=$1 FOR UPDATE
        `, [row.id])).rows[0];
        if (!locked || locked.operation_status !== 'SUCCEEDED' || locked.escrow_status !== 'RELEASED') { await client.query('COMMIT'); continue; }
        if (expectedAmount === null || providerAmount !== expectedAmount || providerCurrency !== expectedCurrency) {
          await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,metadata=$2::jsonb,updated_at=NOW() WHERE id=$3 AND status='SUCCEEDED'`, ['post-success payout provider amount or currency no longer matches the protected operation', JSON.stringify({ ...(locked.metadata || {}), providerStatus, providerAmount, expectedAmount, providerCurrency, expectedCurrency, source: 'batch2-post-success-payout-recovery' }), locked.id]);
          await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE id=$1 AND status='RELEASED'`, [locked.escrow_id]);
          await insertRecoveryEvent(client, locked, 'PAYOUT_REVERSAL_BLOCKED', { reason: 'provider_amount_or_currency_mismatch', providerStatus, providerAmount, expectedAmount, providerCurrency, expectedCurrency });
        } else if (providerStatus === 'reversed') {
          await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,metadata=$2::jsonb,updated_at=NOW() WHERE id=$3 AND status='SUCCEEDED'`, ['Paystack payout was reversed after success; seller funds are re-protected pending review', JSON.stringify({ ...(locked.metadata || {}), providerStatus, providerReference: locked.provider_reference, providerAmount, providerCurrency, source: 'batch2-post-success-payout-recovery' }), locked.id]);
          await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE id=$1 AND status='RELEASED'`, [locked.escrow_id]);
          const caseId = locked.metadata?.caseId ? String(locked.metadata.caseId) : null;
          if (caseId) await client.query(`UPDATE marketplace_protection_cases SET status='UNDER_REVIEW',updated_at=NOW() WHERE id=$1 AND status NOT IN ('REFUNDED','RESOLVED')`, [caseId]);
          await insertRecoveryEvent(client, locked, 'PAYOUT_REVERSAL_RECONCILED', { providerReference: locked.provider_reference, providerStatus, amount: Number(locked.amount), currency: String(locked.currency).toUpperCase(), action: 'reprotected_escrow_and_blocked_payout' });
          repaired += 1;
        }
        await client.query('COMMIT');
      } catch (error) { await client.query('ROLLBACK').catch(() => {}); logger.error('[fynx-marketplace] post-success payout reversal recovery failed', error?.message || error); }
      finally { client.release(); }
    } catch (error) { if (error?.code !== 'PAYSTACK_TIMEOUT') logger.error('[fynx-marketplace] payout reversal provider check failed', error?.message || error); }
  }
  if (repaired) logger.log(`[fynx-marketplace] re-protected ${repaired} post-success reversed payout(s)`);
  return repaired;
}

async function auditCompletedRecovery({ logger = console } = {}) {
  if (!pool) return;
  const rows = await pool.query(`SELECT f.id,f.order_id,f.operation_type,f.provider_reference,f.amount,f.currency,e.amount AS escrow_amount,e.currency AS escrow_currency,e.status AS escrow_status,o.status AS order_status FROM marketplace_financial_operations f JOIN marketplace_escrows e ON e.order_id=f.order_id JOIN marketplace_orders o ON o.id=f.order_id WHERE f.status='SUCCEEDED' AND ((f.operation_type='PAYOUT_RELEASE' AND e.status='RELEASED') OR (f.operation_type='REFUND' AND e.status='REFUNDED')) AND f.updated_at >= NOW() - INTERVAL '24 hours' ORDER BY f.updated_at DESC LIMIT 100`);
  for (const row of rows.rows) {
    const action = row.operation_type === 'PAYOUT_RELEASE' ? 'PAYOUT_RECOVERY_RECONCILED' : 'REFUND_RECOVERY_RECONCILED';
    const client = await pool.connect();
    try { await client.query('BEGIN'); await insertRecoveryEvent(client, row, action, { providerReference: row.provider_reference || null, amount: Number(row.amount), currency: String(row.currency || '').toUpperCase(), escrowAmount: Number(row.escrow_amount), escrowCurrency: String(row.escrow_currency || '').toUpperCase(), source: 'batch2-final-recovery-audit' }); await client.query('COMMIT'); } catch (error) { await client.query('ROLLBACK').catch(() => {}); logger.error('[fynx-marketplace] recovery audit failed', error?.message || error); } finally { client.release(); }
  }
}

export function registerMarketplaceBatch2FinalHardening({ logger = console } = {}) {
  installPaystackTimeoutGuard();
  if (!pool) return;
  const run = async () => { await recoverPaymentWithoutLocalReference({ logger }); await reconcilePendingRefunds({ logger }); await reconcileRefundRecovery({ logger }); await reconcilePostSuccessPayoutReversals({ logger }); await auditCompletedRecovery({ logger }); };
  const timer = setInterval(() => { void run(); }, 30_000); timer.unref(); void run();
  logger.log(`[fynx-marketplace] Batch 2 final hardening active; Paystack timeout=${PAYSTACK_TIMEOUT_MS}ms`);
}
