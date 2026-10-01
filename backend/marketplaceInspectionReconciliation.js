export function startMarketplaceInspectionReconciliation({ pool }) {
  if (!pool) return () => {};

  let running = false;

  const reconcile = async () => {
    if (running) return;
    running = true;
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const result = await client.query(`
        SELECT *
        FROM marketplace_orders
        WHERE status = 'INSPECTION'
          AND inspection_deadline IS NOT NULL
          AND inspection_deadline <= NOW()
        FOR UPDATE SKIP LOCKED
      `);

      for (const order of result.rows) {
        const updated = await client.query(`
          UPDATE marketplace_orders
          SET status='COMPLETED', completed_at=COALESCE(completed_at, NOW()), updated_at=NOW()
          WHERE id=$1 AND status='INSPECTION'
          RETURNING id, status, completed_at
        `, [order.id]);
        if (!updated.rows[0]) continue;

        await client.query(`
          UPDATE marketplace_listings
          SET quantity=GREATEST(0, quantity-$1),
              reserved_quantity=GREATEST(0, reserved_quantity-$1),
              active=CASE WHEN quantity-$1 <= 0 THEN FALSE ELSE active END,
              updated_at=NOW()
          WHERE id=$2
        `, [order.quantity, order.listing_id]);

        await client.query(`
          INSERT INTO marketplace_order_events
            (order_id,actor_id,event_type,from_status,to_status,metadata)
          VALUES ($1,NULL,'INSPECTION_EXPIRED','INSPECTION','COMPLETED',$2::jsonb)
        `, [order.id, JSON.stringify({ payout: 'eligible_for_release', automatic: true })]);
      }

      await client.query('COMMIT');
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('marketplace inspection reconciliation', error);
    } finally {
      client.release();
      running = false;
    }
  };

  const interval = setInterval(reconcile, 5 * 60 * 1000);
  interval.unref?.();
  void reconcile();
  return () => clearInterval(interval);
}
