import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY || '';
const PAYMENT_RESERVATION_TTL_MINUTES = 30;
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
  max: 2,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
  query_timeout: 20_000,
  keepAlive: true
}) : null;

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
    const valid = response.ok && data?.status === true
      && transaction.status === 'success'
      && expectedAmount !== null
      && Number(transaction.amount) === expectedAmount
      && String(transaction.currency || '').toUpperCase() === String(order.currency || '').toUpperCase()
      && String(transaction.reference || '') === String(order.payment_reference)
      && metadataOrderId === String(order.id);
    return { configured: true, paid: valid };
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

async function confirmLatePayment(client, order) {
  const locked = (await client.query(`SELECT id,buyer_id,status FROM marketplace_orders WHERE id=$1 FOR UPDATE`, [order.id])).rows[0];
  if (!locked || locked.status !== 'PAYMENT_PENDING') return false;
  await client.query(`UPDATE marketplace_orders SET status='PAID',updated_at=NOW() WHERE id=$1 AND status='PAYMENT_PENDING'`, [order.id]);
  await client.query(`
    INSERT INTO marketplace_order_events
      (order_id,actor_id,event_type,from_status,to_status,metadata)
    VALUES ($1,$2,'PAYMENT_CONFIRMED','PAYMENT_PENDING','PAID',$3::jsonb)
  `, [order.id, order.buyer_id, JSON.stringify({ reference: order.payment_reference, provider: 'paystack', source: 'payment-expiry-reconciliation' })]);
  return true;
}

export async function expireUnpaidMarketplaceReservations({ logger = console } = {}) {
  if (!pool) return 0;
  const candidates = await pool.query(`
    SELECT id, buyer_id, listing_id, quantity, total_amount, currency, payment_reference
    FROM marketplace_orders
    WHERE status='PAYMENT_PENDING'
      AND created_at <= NOW() - ($1::text || ' minutes')::interval
    ORDER BY created_at ASC
    LIMIT 50
  `, [PAYMENT_RESERVATION_TTL_MINUTES]);

  let expiredCount = 0;
  for (const order of candidates.rows) {
    if (order.payment_reference) {
      const provider = await verifyProviderPayment(order);
      if (!provider.configured || provider.providerUnavailable) continue;
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        if (provider.paid) {
          await confirmLatePayment(client, order);
        } else {
          if (await cancelExpiredOrder(client, order)) expiredCount += 1;
        }
        await client.query('COMMIT');
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        logger.error('[fynx-marketplace] payment expiry reconciliation failed', error?.message || error);
      } finally {
        client.release();
      }
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
    } finally {
      client.release();
    }
  }

  if (expiredCount) logger.log(`[fynx-marketplace] expired ${expiredCount} unpaid reservation(s)`);
  return expiredCount;
}

export function registerMarketplacePaymentExpiryWorker({ logger = console } = {}) {
  const timer = setInterval(() => { void expireUnpaidMarketplaceReservations({ logger }); }, 60_000);
  timer.unref();
  void expireUnpaidMarketplaceReservations({ logger });
  logger.log(`[fynx-marketplace] payment reservation expiry enabled (${PAYMENT_RESERVATION_TTL_MINUTES} minutes)`);
}

export async function closeMarketplacePaymentExpiryWorker() {
  if (pool) await pool.end();
}

export const MARKETPLACE_PAYMENT_RESERVATION_TTL_MINUTES = PAYMENT_RESERVATION_TTL_MINUTES;
