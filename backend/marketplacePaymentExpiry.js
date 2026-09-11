import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
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

export async function expireUnpaidMarketplaceReservations({ logger = console } = {}) {
  if (!pool) return 0;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const expired = await client.query(`
      SELECT id, buyer_id, listing_id, quantity
      FROM marketplace_orders
      WHERE status='PAYMENT_PENDING'
        AND payment_reference IS NULL
        AND created_at <= NOW() - ($1::text || ' minutes')::interval
      ORDER BY created_at ASC
      FOR UPDATE SKIP LOCKED
      LIMIT 50
    `, [PAYMENT_RESERVATION_TTL_MINUTES]);

    for (const order of expired.rows) {
      const updated = await client.query(`
        UPDATE marketplace_orders
        SET status='CANCELLED', cancelled_at=NOW(), updated_at=NOW()
        WHERE id=$1 AND status='PAYMENT_PENDING' AND payment_reference IS NULL
        RETURNING id
      `, [order.id]);
      if (!updated.rowCount) continue;

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
        inventoryReleased: true
      })]);
    }

    await client.query('COMMIT');
    if (expired.rowCount) logger.log(`[fynx-marketplace] expired ${expired.rowCount} unpaid reservation(s)`);
    return expired.rowCount;
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    logger.error('[fynx-marketplace] payment expiry scan failed', error?.message || error);
    return 0;
  } finally {
    client.release();
  }
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
