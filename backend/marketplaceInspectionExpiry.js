import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
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

const AUTO_COMPLETE_INTERVAL_MS = 60_000;

export async function autoCompleteExpiredMarketplaceInspections({ logger = console } = {}) {
  if (!pool) return 0;
  let completed = 0;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await client.query(`
      SELECT o.id,o.buyer_id,o.listing_id,o.quantity,o.status,o.inspection_deadline
      FROM marketplace_orders o
      WHERE o.status='INSPECTION'
        AND o.inspection_deadline IS NOT NULL
        AND o.inspection_deadline <= NOW()
        AND NOT EXISTS (
          SELECT 1 FROM marketplace_order_disputes d
          WHERE d.order_id=o.id AND d.status IN ('OPEN','UNDER_REVIEW')
        )
        AND NOT EXISTS (
          SELECT 1 FROM marketplace_protection_cases c
          WHERE c.order_id=o.id AND c.status IN ('OPEN','UNDER_REVIEW')
        )
      ORDER BY o.inspection_deadline ASC
      LIMIT 50
      FOR UPDATE OF o
    `);

    for (const order of result.rows) {
      const updated = await client.query(`
        UPDATE marketplace_orders
        SET status='COMPLETED',completed_at=COALESCE(completed_at,NOW()),updated_at=NOW()
        WHERE id=$1 AND status='INSPECTION'
        RETURNING id
      `, [order.id]);
      if (!updated.rowCount) continue;

      await client.query(`
        UPDATE marketplace_listings
        SET quantity=GREATEST(0,quantity-$1),
            reserved_quantity=GREATEST(0,reserved_quantity-$1),
            active=CASE WHEN quantity-$1 <= 0 THEN FALSE ELSE active END,
            updated_at=NOW()
        WHERE id=$2
      `, [order.quantity, order.listing_id]);

      await client.query(`
        INSERT INTO marketplace_order_events
          (order_id,actor_id,event_type,from_status,to_status,metadata)
        VALUES ($1,NULL,'INSPECTION_EXPIRED_AUTO_COMPLETED','INSPECTION','COMPLETED',$2::jsonb)
      `, [order.id, JSON.stringify({ inspectionDeadline: order.inspection_deadline, payout: 'eligible_for_release', reason: '48_hour_inspection_window_expired' })]);
      completed += 1;
    }
    await client.query('COMMIT');
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    logger.error('[fynx-marketplace] inspection auto-completion failed', error?.message || error);
  } finally {
    client.release();
  }
  if (completed) logger.log(`[fynx-marketplace] auto-completed ${completed} expired inspection(s)`);
  return completed;
}

export function registerMarketplaceInspectionExpiryWorker({ logger = console } = {}) {
  const timer = setInterval(() => { void autoCompleteExpiredMarketplaceInspections({ logger }); }, AUTO_COMPLETE_INTERVAL_MS);
  timer.unref();
  void autoCompleteExpiredMarketplaceInspections({ logger });
  logger.log('[fynx-marketplace] 48-hour inspection expiry worker enabled');
}

export async function closeMarketplaceInspectionExpiryWorker() {
  if (pool) await pool.end();
}

export const MARKETPLACE_INSPECTION_AUTO_COMPLETE_INTERVAL_MS = AUTO_COMPLETE_INTERVAL_MS;