const DELIVERY_STATES = new Set(['PREPARING', 'DISPATCHED', 'IN_TRANSIT', 'DELIVERED', 'INSPECTION', 'COMPLETED']);
const PICKUP_STATES = new Set(['READY_FOR_PICKUP', 'PICKUP_COMPLETED', 'INSPECTION', 'COMPLETED']);
const EXCEPTION_STATES = new Set(['FAILED_DELIVERY', 'RETURNED']);

const TRANSITIONS = new Map([
  ['PAID->PREPARING', true],
  ['PAID->DISPATCHED', true],
  ['PREPARING->DISPATCHED', true],
  ['DISPATCHED->IN_TRANSIT', true],
  ['IN_TRANSIT->DELIVERED', true],
  ['DELIVERED->INSPECTION', true],
  ['READY_FOR_PICKUP->PICKUP_COMPLETED', true],
  ['PICKUP_COMPLETED->INSPECTION', true],
  ['INSPECTION->COMPLETED', true],
  ['DISPATCHED->FAILED_DELIVERY', true],
  ['IN_TRANSIT->FAILED_DELIVERY', true],
  ['FAILED_DELIVERY->RETURNED', true],
  ['FAILED_DELIVERY->IN_TRANSIT', true],
  ['RETURNED->COMPLETED', true]
]);

export function isMarketplaceFulfillmentState(value) {
  return typeof value === 'string' && (DELIVERY_STATES.has(value) || PICKUP_STATES.has(value) || EXCEPTION_STATES.has(value));
}

export function canTransitionMarketplaceFulfillment(from, to, fulfillmentMethod) {
  if (!isMarketplaceFulfillmentState(to)) return false;
  if (from === to) return false;
  if (to === 'READY_FOR_PICKUP' || to === 'PICKUP_COMPLETED') return fulfillmentMethod === 'PICKUP' && Boolean(TRANSITIONS.get(`${from}->${to}`));
  if (to === 'DISPATCHED' || to === 'IN_TRANSIT' || to === 'DELIVERED' || to === 'FAILED_DELIVERY' || to === 'RETURNED') return fulfillmentMethod === 'DELIVERY' && Boolean(TRANSITIONS.get(`${from}->${to}`));
  return Boolean(TRANSITIONS.get(`${from}->${to}`));
}

export function registerMarketplaceFulfillmentStateRoutes({ app, pool, auth }) {
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS fulfillment_status TEXT NOT NULL DEFAULT 'PAID';
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS fulfillment_status_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
        CREATE INDEX IF NOT EXISTS marketplace_orders_fulfillment_status_idx ON marketplace_orders (seller_id, fulfillment_status, updated_at DESC);
      `).catch((error) => { schemaPromise = undefined; throw error; });
    }
    return schemaPromise;
  };

  app.post('/api/marketplace/orders/:id/fulfillment-state', auth, async (req, res) => {
    const id = typeof req.params?.id === 'string' ? req.params.id.trim() : '';
    const nextState = typeof req.body?.state === 'string' ? req.body.state.trim().toUpperCase() : '';
    const note = typeof req.body?.note === 'string' ? req.body.note.trim().slice(0, 500) : '';
    if (!/^[0-9a-f-]{36}$/i.test(id) || !isMarketplaceFulfillmentState(nextState)) return res.status(400).json({ error: 'valid order id and fulfillment state are required' });

    const client = await pool.connect();
    try {
      await ensureSchema();
      await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      const userId = String(req.user.sub);
      const isSeller = String(order.seller_id) === userId;
      const isBuyer = String(order.buyer_id) === userId;
      if (!isSeller && !isBuyer) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'not authorized for this order' }); }

      const current = order.fulfillment_status || (order.status === 'COMPLETED' ? 'COMPLETED' : order.status === 'INSPECTION' ? 'INSPECTION' : order.fulfillment_method === 'PICKUP' && order.pickup_handover_at ? 'PICKUP_COMPLETED' : order.status === 'SHIPPED' ? 'DISPATCHED' : 'PAID');
      if (!canTransitionMarketplaceFulfillment(current, nextState, order.fulfillment_method)) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: `invalid fulfillment transition from ${current} to ${nextState}` });
      }

      if (nextState === 'INSPECTION' && !isBuyer) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can confirm receipt for inspection' }); }
      if (nextState === 'COMPLETED' && !isBuyer) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can complete inspection' }); }
      if (!isSeller && ['PREPARING','DISPATCHED','IN_TRANSIT','DELIVERED','READY_FOR_PICKUP','PICKUP_COMPLETED','FAILED_DELIVERY','RETURNED'].includes(nextState)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can update fulfillment progress' }); }
      if (['FAILED_DELIVERY','RETURNED'].includes(nextState)) {
        const conflict = (await client.query(`
          SELECT 1 FROM marketplace_order_disputes WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')
          UNION ALL
          SELECT 1 FROM marketplace_protection_cases WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')
          LIMIT 1
        `, [id])).rowCount > 0;
        if (conflict) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      }

      const updated = await client.query(`UPDATE marketplace_orders SET fulfillment_status=$1,fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$2 RETURNING *`, [nextState, id]);
      await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,$3,$4,$5,$6::jsonb)`, [id, req.user.sub, `FULFILLMENT_${nextState}`, current, nextState, JSON.stringify({ fulfillmentMethod: order.fulfillment_method, note: note || null })]);
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, fulfillmentStatusUpdatedAt: updated.rows[0].fulfillment_status_updated_at } });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('marketplace fulfillment state', error);
      return res.status(500).json({ error: 'fulfillment state update failed' });
    } finally { client.release(); }
  });
}
