import { getFynxDatabasePool } from "./databasePool.js";
import pg from 'pg';
import { confirmMarketplacePayment } from './marketplacePaymentState.js';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY || '';
const PAYMENT_RESERVATION_TTL_MINUTES = 30;
const PAYMENT_INITIALIZATION_RECOVERY_MINUTES = 2;
const pool = getFynxDatabasePool();

function amountSubunit(amount, currency) {
  const normalized = String(currency || '').trim().toUpperCase();
  if (!['NGN', 'USD'].includes(normalized)) return null;
  const value = Number(amount);
  if (!Number.isFinite(value) || value <= 0) return null;
  return Math.round(value * 100);
}

async function verifyProviderPayment(order) {
  if (!PAYSTACK_SECRET_KEY || !order.payment_reference) return { configured: false, paid: false };
  try {
    const response = await fetch(`https://api.paystack.co/transaction/verify/${encodeURIComponent(order.payment_reference)}`, {
      headers: { Authorization: `Bearer ${PAYSTACK_SECRET_KEY}` }
    });
    const data = await response.json().catch(() => ({}));
    const transaction = data?.data || {};
    const expectedAmount = amountSubunit(order.total_amount, order.currency);
    const metadataOrderId = String(transaction.metadata?.orderId || transaction.metadata?.order_id || '');
    const providerFee = Number(transaction.fees);
    const validProviderFee = Number.isFinite(providerFee) && providerFee >= 0 ? Math.round((providerFee / 100) * 100) / 100 : 0;
    const status = String(transaction.status || '').toLowerCase();
    const valid = response.ok && data?.status === true
      && status === 'success'
      && expectedAmount !== null
      && Number(transaction.amount) === expectedAmount
      && String(transaction.currency || '').toUpperCase() === String(order.currency || '').toUpperCase()
      && String(transaction.reference || '') === String(order.payment_reference)
      && metadataOrderId === String(order.id);
    return { configured: true, paid: valid, status, providerFee: validProviderFee };
  } catch {
    return { configured: true, paid: false, providerUnavailable: true };
  }
}

async function cancelExpiredOrder(client, order) {
  const updated = await client.query(`
    UPDATE marketplace_orders
    SET status='CANCELLED',cancelled_at=NOW(),updated_at=NOW()
    WHERE id=$1 AND status='PAYMENT_PENDING'
    RETURNING id
  `, [order.id]);
  if (!updated.rowCount) return false;

  await client.query(`
    UPDATE marketplace_listings
    SET reserved_quantity=GREATEST(0,reserved_quantity-$1),updated_at=NOW()
    WHERE id=$2
  `, [order.quantity, order.listing_id]);

  await client.query(`
    INSERT INTO marketplace_order_events
      (order_id,actor_id,event_type,from_status,to_status,metadata)
    VALUES ($1,$2,'ORDER_PAYMENT_EXPIRED','PAYMENT_PENDING','CANCELLED',$3::jsonb)
  `, [order.id, order.buyer_id, JSON.stringify({
    reason: 'payment_reservation_expired',
    reservationTtlMinutes: PAYMENT_RESERVATION_TTL_MINUTES,
    inventoryReleased: true,
    hadPaymentReference: Boolean(order.payment_reference)
  })]);
  return true;
}

async function confirmLatePayment(client, order, providerFee = 0) {
  const locked = (await client.query(`SELECT id,buyer_id,status,payment_reference FROM marketplace_orders WHERE id=$1 FOR UPDATE`, [order.id])).rows[0];
  if (!locked || locked.status !== 'PAYMENT_PENDING' || String(locked.payment_reference || '') !== String(order.payment_reference || '')) return false;
  await client.query(`UPDATE marketplace_orders SET status='PAID',payment_provider_fee=$2,updated_at=NOW() WHERE id=$1 AND status='PAYMENT_PENDING'`, [order.id, providerFee]);
  await client.query(`
    INSERT INTO marketplace_order_events
      (order_id,actor_id,event_type,from_status,to_status,metadata)
    VALUES ($1,$2,'PAYMENT_CONFIRMED','PAYMENT_PENDING','PAID',$3::jsonb)
  `, [order.id, order.buyer_id, JSON.stringify({ reference: order.payment_reference, provider: 'paystack', source: 'payment-expiry-reconciliation', providerFee })]);
  return true;
}

async function recoverInterruptedPaymentInitializations({ logger = console } = {}) {
  if (!pool || !PAYSTACK_SECRET_KEY) return 0;
  const candidates = await pool.query(`
    SELECT id,buyer_id,total_amount,currency,payment_reference,payment_authorization_url,payment_access_code,updated_at
    FROM marketplace_orders
    WHERE status='PAYMENT_PENDING'
      AND payment_reference IS NOT NULL
      AND payment_authorization_url IS NULL
      AND updated_at <= NOW() - ($1::text || ' minutes')::interval
    ORDER BY updated_at ASC
    LIMIT 25
  `, [PAYMENT_INITIALIZATION_RECOVERY_MINUTES]);
  let recovered = 0;
  for (const order of candidates.rows) {
    const provider = await verifyProviderPayment(order);
    if (!provider.configured || provider.providerUnavailable) continue;
    if (provider.paid) {
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        if (await confirmLatePayment(client, order, provider.providerFee)) recovered += 1;
        await client.query('COMMIT');
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        logger.error('[fynx-marketplace] interrupted payment recovery failed', error?.message || error);
      } finally { client.release(); }
      continue;
    }
    if (!['failed', 'abandoned', 'reversed'].includes(provider.status)) continue;
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const locked = (await client.query(`SELECT id,buyer_id,status,payment_reference FROM marketplace_orders WHERE id=$1 FOR UPDATE`, [order.id])).rows[0];
      if (locked && locked.status === 'PAYMENT_PENDING' && String(locked.payment_reference || '') === String(order.payment_reference || '')) {
        await client.query(`UPDATE marketplace_orders SET payment_reference=NULL,payment_authorization_url=NULL,payment_access_code=NULL,updated_at=NOW() WHERE id=$1 AND status='PAYMENT_PENDING'`, [order.id]);
        await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'PAYMENT_INITIALIZATION_RECOVERED','PAYMENT_PENDING','PAYMENT_PENDING',$3::jsonb)`, [order.id, order.buyer_id, JSON.stringify({ reference: order.payment_reference, provider: 'paystack', providerStatus: provider.status, action: 'cleared_stale_reference_for_retry' })]);
        recovered += 1;
      }
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK').catch(() => {});
      logger.error('[fynx-marketplace] stale payment reference recovery failed', error?.message || error);
    } finally { client.release(); }
  }
  if (recovered) logger.log(`[fynx-marketplace] recovered ${recovered} interrupted payment initialization(s)`);
  return recovered;
}

async function reconcileTerminalFinancialOperations({ logger = console } = {}) {
  if (!pool || !PAYSTACK_SECRET_KEY) return 0;
  let repaired = 0;
  const payoutRows = await pool.query(`
    SELECT f.id,f.order_id,f.amount,f.currency,f.provider_reference,f.metadata,e.id AS escrow_id,e.amount AS escrow_amount,e.currency AS escrow_currency,e.status AS escrow_status
    FROM marketplace_financial_operations f
    JOIN marketplace_escrows e ON e.order_id=f.order_id
    WHERE f.operation_type='PAYOUT_RELEASE' AND f.status='SUCCEEDED' AND e.status='RELEASE_PENDING' AND f.provider_reference IS NOT NULL
    ORDER BY f.updated_at ASC LIMIT 25
  `);
  for (const op of payoutRows.rows) {
    try {
      const response = await fetch(`https://api.paystack.co/transfer/verify/${encodeURIComponent(op.provider_reference)}`, { headers: { Authorization: `Bearer ${PAYSTACK_SECRET_KEY}` } });
      const data = await response.json().catch(() => ({}));
      const transfer = data?.data || {};
      const status = String(transfer.status || '').toLowerCase();
      const expected = amountSubunit(op.amount, op.currency);
      const valid = response.ok && data?.status === true && ['success', 'successful'].includes(status)
        && expected !== null && Number(transfer.amount) === expected
        && String(transfer.currency || '').toUpperCase() === String(op.currency || '').toUpperCase();
      if (!valid) continue;
      const marketplaceFee = Number(op.metadata?.marketplaceFee || 0);
      const escrowAmount = Number(op.escrow_amount);
      const payoutAmount = Number(op.amount);
      if (!Number.isFinite(marketplaceFee) || !Number.isFinite(escrowAmount) || !Number.isFinite(payoutAmount)
        || Math.round((payoutAmount + marketplaceFee) * 100) / 100 !== escrowAmount
        || String(op.currency).toUpperCase() !== String(op.escrow_currency).toUpperCase()) continue;
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const escrow = (await client.query(`SELECT id,status,amount,currency FROM marketplace_escrows WHERE id=$1 FOR UPDATE`, [op.escrow_id])).rows[0];
        if (escrow?.status === 'RELEASE_PENDING') {
          await client.query(`UPDATE marketplace_escrows SET status='RELEASED',released_at=COALESCE(released_at,NOW()),updated_at=NOW() WHERE id=$1 AND status='RELEASE_PENDING'`, [escrow.id]);
          await client.query(`INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata) VALUES ($1,$2,'seller_payout','RELEASE',$3,$4,$5,$6::jsonb) ON CONFLICT (idempotency_key) DO NOTHING`, [escrow.id, op.order_id, payoutAmount, escrow.currency, `PAYOUT-RELEASE-${op.order_id}`, JSON.stringify({ provider: 'paystack', providerReference: op.provider_reference, source: 'terminal-financial-recovery', providerStatus: status, protectedAmount: escrowAmount, sellerNetAmount: payoutAmount, marketplaceFee })]);
          if (marketplaceFee > 0) await client.query(`INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata) VALUES ($1,$2,'fynx_marketplace_fee','FEE',$3,$4,$5,$6::jsonb) ON CONFLICT (idempotency_key) DO NOTHING`, [escrow.id, op.order_id, marketplaceFee, escrow.currency, `MARKETPLACE-FEE-${op.order_id}`, JSON.stringify({ provider: 'paystack', providerReference: op.provider_reference, source: 'terminal-financial-recovery' })]);
          repaired += 1;
        }
        await client.query('COMMIT');
      } catch (error) { await client.query('ROLLBACK').catch(() => {}); logger.error('[fynx-marketplace] terminal payout recovery failed', error?.message || error); }
      finally { client.release(); }
    } catch (error) { logger.error('[fynx-marketplace] terminal payout provider check failed', error?.message || error); }
  }

  const refundRows = await pool.query(`
    SELECT f.id,f.order_id,f.amount,f.currency,f.metadata,e.id AS escrow_id,e.status AS escrow_status,o.status AS order_status
    FROM marketplace_financial_operations f
    JOIN marketplace_escrows e ON e.order_id=f.order_id
    JOIN marketplace_orders o ON o.id=f.order_id
    WHERE f.operation_type='REFUND' AND f.status='SUCCEEDED' AND e.status='REFUND_PENDING'
    ORDER BY f.updated_at ASC LIMIT 25
  `);
  for (const op of refundRows.rows) {
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const escrow = (await client.query(`SELECT id,status,amount,currency FROM marketplace_escrows WHERE id=$1 FOR UPDATE`, [op.escrow_id])).rows[0];
      if (escrow?.status === 'REFUND_PENDING') {
        await client.query(`UPDATE marketplace_escrows SET status='REFUNDED',refunded_at=COALESCE(refunded_at,NOW()),updated_at=NOW() WHERE id=$1 AND status='REFUND_PENDING'`, [escrow.id]);
        await client.query(`UPDATE marketplace_orders SET status='REFUNDED',updated_at=NOW() WHERE id=$1 AND status NOT IN ('COMPLETED','REFUNDED')`, [op.order_id]);
        await client.query(`INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata) VALUES ($1,$2,'buyer_refund','REFUND',$3,$4,$5,$6::jsonb) ON CONFLICT (idempotency_key) DO NOTHING`, [escrow.id, op.order_id, escrow.amount, escrow.currency, `REFUND-${op.order_id}`, JSON.stringify({ source: 'terminal-financial-recovery', caseId: op.metadata?.caseId || null })]);
        repaired += 1;
      }
      await client.query('COMMIT');
    } catch (error) { await client.query('ROLLBACK').catch(() => {}); logger.error('[fynx-marketplace] terminal refund recovery failed', error?.message || error); }
    finally { client.release(); }
  }
  if (repaired) logger.log(`[fynx-marketplace] repaired ${repaired} terminal financial state(s)`);
  return repaired;
}


async function verifyProviderCheckoutGroupPayment(group) {
  if (!PAYSTACK_SECRET_KEY || !group.payment_reference) return { configured: false, paid: false };
  try {
    const response = await fetch(`https://api.paystack.co/transaction/verify/${encodeURIComponent(group.payment_reference)}`, {
      headers: { Authorization: `Bearer ${PAYSTACK_SECRET_KEY}` }
    });
    const data = await response.json().catch(() => ({}));
    const transaction = data?.data || {};
    const expectedAmount = amountSubunit(group.buyer_total, group.currency);
    const status = String(transaction.status || '').toLowerCase();
    const valid = response.ok && data?.status === true
      && status === 'success'
      && expectedAmount !== null
      && Number(transaction.amount) === expectedAmount
      && String(transaction.currency || '').toUpperCase() === String(group.currency || '').toUpperCase()
      && String(transaction.reference || '') === String(group.payment_reference)
      && String(transaction.metadata?.checkoutGroupId || transaction.metadata?.checkout_group_id || '') === String(group.id)
      && String(transaction.metadata?.buyerId || transaction.metadata?.buyer_id || '') === String(group.buyer_id)
      && Number.isFinite(Number(transaction.fees)) && Number(transaction.fees) >= 0;
    return { configured: true, paid: valid, status, providerFee: valid ? Math.round(Number(transaction.fees)) : 0 };
  } catch {
    return { configured: true, paid: false, providerUnavailable: true };
  }
}

/**
 * Reconcile an expired multi-product checkout as one unit. Never release stock
 * while Paystack is unavailable or still processing a referenced payment.
 */
async function expireUnpaidMarketplaceCheckoutGroups({ logger = console } = {}) {
  if (!pool) return 0;
  let expired = 0;
  try {
    await pool.query(`ALTER TABLE marketplace_checkout_groups
      ADD COLUMN IF NOT EXISTS payment_reference TEXT,
      ADD COLUMN IF NOT EXISTS payment_authorization_url TEXT,
      ADD COLUMN IF NOT EXISTS payment_access_code TEXT`);
    const candidates = await pool.query(`
      SELECT id,buyer_id,buyer_total,currency,status,payment_reference,created_at
      FROM marketplace_checkout_groups
      WHERE status='PAYMENT_PENDING'
        AND created_at <= NOW() - ($1::text || ' minutes')::interval
      ORDER BY created_at ASC
      LIMIT 25
    `, [PAYMENT_RESERVATION_TTL_MINUTES]);

    for (const candidate of candidates.rows) {
      const provider = candidate.payment_reference
        ? await verifyProviderCheckoutGroupPayment(candidate)
        : { configured: true, paid: false, status: 'uninitialized' };
      if (candidate.payment_reference && (!provider.configured || provider.providerUnavailable)) continue;
      if (candidate.payment_reference && !provider.paid
        && !['failed', 'abandoned', 'reversed'].includes(provider.status)) continue;

      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const group = (await client.query(`
          SELECT id,buyer_id,buyer_total,currency,status,payment_reference
          FROM marketplace_checkout_groups WHERE id=$1 FOR UPDATE
        `, [candidate.id])).rows[0];
        if (!group || group.status !== 'PAYMENT_PENDING'
          || String(group.payment_reference || '') !== String(candidate.payment_reference || '')) {
          await client.query('ROLLBACK');
          continue;
        }
        const children = (await client.query(`
          SELECT o.id,o.buyer_id,o.listing_id,o.quantity,o.total_amount,o.currency,o.status
          FROM marketplace_checkout_group_items gi
          JOIN marketplace_orders o ON o.id=gi.order_id
          WHERE gi.checkout_id=$1 ORDER BY o.id ASC FOR UPDATE OF o
        `, [group.id])).rows;
        if (!children.length || children.some((child) => child.status !== 'PAYMENT_PENDING')) {
          await client.query('ROLLBACK');
          continue;
        }

        if (provider.paid) {
          const expected = amountSubunit(group.buyer_total, group.currency);
          const childCents = children.map((child) => amountSubunit(child.total_amount, child.currency));
          if (!expected || childCents.some((amount) => !amount)
            || childCents.reduce((sum, amount) => sum + amount, 0) !== expected
            || children.some((child) => String(child.currency).toUpperCase() !== String(group.currency).toUpperCase())) {
            await client.query('ROLLBACK');
            logger.error('[fynx-marketplace] expired checkout group payment totals did not reconcile', String(group.id));
            continue;
          }
          let allocated = 0;
          for (let index = 0; index < children.length; index += 1) {
            const child = children[index];
            const fee = index === children.length - 1
              ? provider.providerFee - allocated
              : Math.floor((provider.providerFee * childCents[index]) / expected);
            allocated += fee;
            await client.query(`UPDATE marketplace_orders SET status='PAID',payment_provider_fee=$2,updated_at=NOW() WHERE id=$1 AND status='PAYMENT_PENDING'`, [child.id, fee / 100]);
            await client.query(`
              INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata)
              VALUES ($1,$2,'PAYMENT_CONFIRMED','PAYMENT_PENDING','PAID',$3::jsonb)
            `, [child.id, child.buyer_id, JSON.stringify({ reference: group.payment_reference, provider: 'paystack', source: 'checkout-group-expiry-reconciliation', checkoutGroupId: String(group.id), providerFee: fee / 100 })]);
          }
          await client.query(`UPDATE marketplace_checkout_groups SET status='PAID',updated_at=NOW() WHERE id=$1 AND status='PAYMENT_PENDING'`, [group.id]);
        } else {
          for (const child of children) {
            await client.query(`UPDATE marketplace_orders SET status='CANCELLED',cancelled_at=NOW(),updated_at=NOW() WHERE id=$1 AND status='PAYMENT_PENDING'`, [child.id]);
            await client.query(`UPDATE marketplace_listings SET reserved_quantity=GREATEST(0,reserved_quantity-$1),updated_at=NOW() WHERE id=$2`, [child.quantity, child.listing_id]);
            await client.query(`
              INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata)
              VALUES ($1,$2,'ORDER_PAYMENT_EXPIRED','PAYMENT_PENDING','CANCELLED',$3::jsonb)
            `, [child.id, child.buyer_id, JSON.stringify({ reason: 'checkout_group_payment_expired', checkoutGroupId: String(group.id), inventoryReleased: true, hadPaymentReference: Boolean(group.payment_reference) })]);
          }
          await client.query(`UPDATE marketplace_checkout_groups SET status='CANCELLED',updated_at=NOW() WHERE id=$1 AND status='PAYMENT_PENDING'`, [group.id]);
          expired += 1;
        }
        await client.query('COMMIT');
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        logger.error('[fynx-marketplace] checkout group expiry reconciliation failed', error?.message || error);
      } finally { client.release(); }
    }
  } catch (error) {
    // Multi-product tables may not exist until the feature is first used.
    if (!/marketplace_checkout_groups/i.test(String(error?.message || ''))) logger.error('[fynx-marketplace] checkout group expiry scan failed', error?.message || error);
  }
  if (expired) logger.log(`[fynx-marketplace] expired ${expired} unpaid multi-product checkout group(s)`);
  return expired;
}

export async function expireUnpaidMarketplaceReservations({ logger = console } = {}) {
  if (!pool) return 0;
  await expireUnpaidMarketplaceCheckoutGroups({ logger });
  // Multi-product child orders are owned by their checkout-group lifecycle.
  // Exclude them from this single-order scan so an unavailable/inconclusive
  // Paystack group check cannot be bypassed by releasing child reservations.
  const groupItemsTable = (await pool.query(
    "SELECT to_regclass('public.marketplace_checkout_group_items') AS table_name"
  )).rows[0]?.table_name;
  const groupChildExclusion = groupItemsTable
    ? "AND NOT EXISTS (SELECT 1 FROM marketplace_checkout_group_items gi WHERE gi.order_id = marketplace_orders.id)"
    : "";
  const candidates = await pool.query(`
    SELECT id, buyer_id, listing_id, quantity, total_amount, currency, payment_reference
    FROM marketplace_orders
    WHERE status='PAYMENT_PENDING'
      AND created_at <= NOW() - ($1::text || ' minutes')::interval
      ${groupChildExclusion}
    ORDER BY created_at ASC
    LIMIT 50
  `, [PAYMENT_RESERVATION_TTL_MINUTES]);

  let expiredCount = 0;
  for (const order of candidates.rows) {
    if (order.payment_reference) {
      const provider = await verifyProviderPayment(order);
      if (!provider.configured || provider.providerUnavailable) continue;
      // A referenced payment that is still pending/processing (or has an unknown
      // provider status) must keep its inventory reservation. Only a confirmed
      // success or an explicit terminal failure can be reconciled here.
      if (!provider.paid && !['failed', 'abandoned', 'reversed'].includes(provider.status)) continue;
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        if (provider.paid) {
          await confirmLatePayment(client, order, provider.providerFee);
        } else {
          if (await cancelExpiredOrder(client, order)) expiredCount += 1;
        }
        await client.query('COMMIT');
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        logger.error('[fynx-marketplace] payment expiry reconciliation failed', error?.message || error);
      } finally { client.release(); }
      continue;
    }

    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const locked = (await client.query(`
        SELECT id,buyer_id,listing_id,quantity,payment_reference
        FROM marketplace_orders
        WHERE id=$1 AND status='PAYMENT_PENDING'
        FOR UPDATE SKIP LOCKED
      `, [order.id])).rows[0];
      if (locked && !locked.payment_reference && await cancelExpiredOrder(client, locked)) expiredCount += 1;
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK').catch(() => {});
      logger.error('[fynx-marketplace] payment expiry scan failed', error?.message || error);
    } finally { client.release(); }
  }

  if (expiredCount) logger.log(`[fynx-marketplace] expired ${expiredCount} unpaid reservation(s)`);
  return expiredCount;
}

export function registerMarketplacePaymentExpiryWorker({ logger = console } = {}) {
  const timer = setInterval(() => { void expireUnpaidMarketplaceReservations({ logger }); }, 60_000);
  timer.unref();
  const recoveryTimer = setInterval(() => { void recoverInterruptedPaymentInitializations({ logger }); }, 20_000);
  recoveryTimer.unref();
  const terminalTimer = setInterval(() => { void reconcileTerminalFinancialOperations({ logger }); }, 30_000);
  terminalTimer.unref();
  void expireUnpaidMarketplaceReservations({ logger });
  void recoverInterruptedPaymentInitializations({ logger });
  void reconcileTerminalFinancialOperations({ logger });
  logger.log(`[fynx-marketplace] payment reservation expiry enabled (${PAYMENT_RESERVATION_TTL_MINUTES} minutes); initialization recovery enabled (${PAYMENT_INITIALIZATION_RECOVERY_MINUTES} minutes)`);
}

export async function closeMarketplacePaymentExpiryWorker() {
  if (pool) await pool.end();
}

export const MARKETPLACE_PAYMENT_RESERVATION_TTL_MINUTES = PAYMENT_RESERVATION_TTL_MINUTES;
export const MARKETPLACE_PAYMENT_INITIALIZATION_RECOVERY_MINUTES = PAYMENT_INITIALIZATION_RECOVERY_MINUTES;
