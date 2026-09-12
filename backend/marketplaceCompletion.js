import crypto from 'node:crypto';
import { registerMarketplaceShippingTimeline } from './marketplaceShippingTimeline.js';
import { isMarketplaceDestinationCovered } from './marketplaceShipping.js';

export function registerMarketplaceCompletionRoutes({ app, pool, auth }) {
  registerMarketplaceShippingTimeline({ app, pool, auth });
  let schemaPromise;

  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS fulfillment_method TEXT NOT NULL DEFAULT 'DELIVERY' CHECK (fulfillment_method IN ('DELIVERY','PICKUP'));
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_address JSONB;
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS buyer_note TEXT NOT NULL DEFAULT '';
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS pickup_handover_at TIMESTAMPTZ;
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS pickup_handover_by BIGINT;
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS fulfillment_status TEXT;
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS fulfillment_status_updated_at TIMESTAMPTZ;
        UPDATE marketplace_orders
        SET fulfillment_status = CASE
          WHEN status = 'COMPLETED' THEN 'COMPLETED'
          WHEN status = 'INSPECTION' THEN 'INSPECTION'
          WHEN status = 'SHIPPED' AND fulfillment_method = 'PICKUP' THEN 'PICKUP_COMPLETED'
          WHEN status = 'SHIPPED' THEN 'DISPATCHED'
          WHEN status = 'PAID' THEN 'PAID'
          ELSE 'PENDING'
        END,
        fulfillment_status_updated_at = COALESCE(fulfillment_status_updated_at, updated_at, NOW())
        WHERE fulfillment_status IS NULL;
        ALTER TABLE marketplace_orders ALTER COLUMN fulfillment_status SET DEFAULT 'PENDING';
        ALTER TABLE marketplace_orders ALTER COLUMN fulfillment_status SET NOT NULL;
        ALTER TABLE marketplace_orders ALTER COLUMN fulfillment_status_updated_at SET DEFAULT NOW();
        ALTER TABLE marketplace_orders ALTER COLUMN fulfillment_status_updated_at SET NOT NULL;
        CREATE INDEX IF NOT EXISTS marketplace_orders_fulfillment_idx ON marketplace_orders (seller_id, status, updated_at DESC);
        CREATE INDEX IF NOT EXISTS marketplace_orders_fulfillment_status_idx ON marketplace_orders (seller_id, fulfillment_status, updated_at DESC);
      `).catch((error) => {
        schemaPromise = undefined;
        throw error;
      });
    }
    return schemaPromise;
  };

  const parseUuid = (value) => typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;
  const isSeller = (order, userId) => String(order.seller_id) === String(userId);
  const isBuyer = (order, userId) => String(order.buyer_id) === String(userId);
  const DELIVERY_TRANSITIONS = new Map([
    ['PAID->PREPARING', true],
    ['PREPARING->DISPATCHED', true],
    ['DISPATCHED->IN_TRANSIT', true],
    ['IN_TRANSIT->DELIVERED', true],
    ['DISPATCHED->FAILED_DELIVERY', true],
    ['IN_TRANSIT->FAILED_DELIVERY', true],
    ['FAILED_DELIVERY->IN_TRANSIT', true],
    ['FAILED_DELIVERY->RETURNED', true]
  ]);
  const PICKUP_TRANSITIONS = new Map([
    ['PAID->READY_FOR_PICKUP', true]
  ]);

  const canAdvanceFulfillment = (from, to, method) => {
    if (from === to) return false;
    if (method === 'PICKUP') return Boolean(PICKUP_TRANSITIONS.get(`${from}->${to}`));
    return Boolean(DELIVERY_TRANSITIONS.get(`${from}->${to}`));
  };

  const hasActiveProtectionCase = async (client, orderId) => (await client.query(`
    SELECT 1 FROM marketplace_order_disputes WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')
    UNION ALL
    SELECT 1 FROM marketplace_protection_cases WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')
    LIMIT 1
  `, [orderId])).rowCount > 0;

  const recordFulfillmentEvent = async (client, orderId, actorId, eventType, fromState, toState, metadata = {}) => {
    await client.query(
      `INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,$3,$4,$5,$6::jsonb)`,
      [orderId, actorId, eventType, fromState, toState, JSON.stringify(metadata)]
    );
  };

  app.get('/api/marketplace/media/:id', auth, async (req, res) => {
    try {
      const mediaId = Number(req.params.id);
      if (!Number.isInteger(mediaId) || mediaId < 1) return res.status(400).json({ error: 'invalid media id' });
      const result = await pool.query(`
        SELECT mm.mime_type, mm.data
        FROM message_media mm
        WHERE mm.id = $1
          AND (
            EXISTS (
              SELECT 1 FROM marketplace_listings ml
              WHERE ml.active = TRUE
                AND EXISTS (
                  SELECT 1
                  FROM regexp_split_to_table(regexp_replace(CAST(ml.media_ids AS TEXT), '[^0-9]+', ',', 'g'), ',') AS listing_media_id
                  WHERE listing_media_id = CAST(mm.id AS TEXT)
                )
            )
            OR EXISTS (
              SELECT 1 FROM marketplace_orders mo
              WHERE (mo.buyer_id = $2 OR mo.seller_id = $2)
                AND EXISTS (
                  SELECT 1
                  FROM jsonb_array_elements_text(COALESCE(mo.product_snapshot->'mediaIds', '[]'::jsonb)) AS order_media_id
                  WHERE order_media_id = CAST(mm.id AS TEXT)
                )
            )
          )
        LIMIT 1`, [mediaId, req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error: 'marketplace media not found' });
      res.set('Cache-Control', 'private, max-age=3600');
      res.type(result.rows[0].mime_type);
      return res.send(result.rows[0].data);
    } catch (error) {
      console.error('marketplace media fetch', error);
      return res.status(500).json({ error: 'marketplace media fetch failed' });
    }
  });

  app.get('/api/marketplace/seller/orders', auth, async (req, res) => {
    try {
      await ensureSchema();
      const status = typeof req.query?.status === 'string' ? req.query.status.trim().toUpperCase() : '';
      const allowed = new Set(['PAYMENT_PENDING','PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED','DISPUTED','CANCELLED','REFUNDED']);
      const params = [req.user.sub];
      let where = 'o.seller_id = $1';
      if (status && allowed.has(status)) { params.push(status); where += ` AND o.status = $${params.length}`; }
      const result = await pool.query(`SELECT o.*, u.username AS buyer_username, u.display_name AS buyer_display_name FROM marketplace_orders o JOIN users u ON u.id = o.buyer_id WHERE ${where} ORDER BY o.created_at DESC LIMIT 100`, params);
      return res.json({ orders: result.rows.map((row) => ({
        id: String(row.id), buyerId: String(row.buyer_id), buyerUsername: row.buyer_username, buyerDisplayName: row.buyer_display_name,
        listingId: String(row.listing_id), quantity: Number(row.quantity), unitPrice: Number(row.unit_price), deliveryFee: Number(row.delivery_fee),
        totalAmount: Number(row.total_amount), currency: row.currency, product: row.product_snapshot, status: row.status,
        fulfillmentStatus: row.fulfillment_status, fulfillmentStatusUpdatedAt: row.fulfillment_status_updated_at,
        fulfillmentMethod: row.fulfillment_method, shippingAddress: row.shipping_address, buyerNote: row.buyer_note,
        paymentReference: row.payment_reference, trackingReference: row.tracking_reference, shippedAt: row.shipped_at,
        pickupHandoverAt: row.pickup_handover_at, pickupHandoverBy: row.pickup_handover_by ? String(row.pickup_handover_by) : null,
        deliveredAt: row.delivered_at, inspectionDeadline: row.inspection_deadline, completedAt: row.completed_at,
        createdAt: row.created_at, updatedAt: row.updated_at
      })) });
    } catch (error) { console.error('marketplace seller orders', error); return res.status(500).json({ error: 'seller order lookup failed' }); }
  });

  app.post('/api/marketplace/orders/:id/fulfillment', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const method = typeof req.body?.method === 'string' ? req.body.method.trim().toUpperCase() : '';
    const address = req.body?.shippingAddress && typeof req.body.shippingAddress === 'object' && !Array.isArray(req.body.shippingAddress) ? req.body.shippingAddress : null;
    const note = typeof req.body?.buyerNote === 'string' ? req.body.buyerNote.trim().slice(0, 1000) : '';
    if (!id || !['DELIVERY','PICKUP'].includes(method)) return res.status(400).json({ error: 'valid order id and fulfillment method are required' });
    if (method === 'DELIVERY' && (!address || typeof address.name !== 'string' || typeof address.phone !== 'string' || typeof address.address !== 'string' || !address.address.trim())) return res.status(400).json({ error: 'delivery requires name, phone and address' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isBuyer(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can choose fulfillment' }); }
      if (!['PAYMENT_PENDING','PAID'].includes(order.status)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'fulfillment can only be selected before shipping' }); }
      const snapshot = order.product_snapshot && typeof order.product_snapshot === 'object' ? order.product_snapshot : {};
      if (method === 'DELIVERY' && !Boolean(snapshot.deliveryAvailable)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'delivery is not available for this listing' }); }
      if (method === 'PICKUP' && !Boolean(snapshot.pickupAvailable)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup is not available for this listing' }); }
      const safeAddress = method === 'DELIVERY' ? { name: String(address.name).trim().slice(0, 120), phone: String(address.phone).trim().slice(0, 40), address: String(address.address).trim().slice(0, 500), city: typeof address.city === 'string' ? address.city.trim().slice(0, 100) : '', state: typeof address.state === 'string' ? address.state.trim().slice(0, 100) : '', country: typeof address.country === 'string' ? address.country.trim().slice(0, 100) : '' } : null;
      if (method === 'DELIVERY' && !(await isMarketplaceDestinationCovered(client, order.listing_id, safeAddress))) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'seller does not currently deliver to this destination', code: 'DESTINATION_NOT_COVERED' }); }
      const updated = await client.query(`UPDATE marketplace_orders SET fulfillment_method=$1,shipping_address=$2::jsonb,buyer_note=$3,updated_at=NOW() WHERE id=$4 RETURNING *`, [method, safeAddress ? JSON.stringify(safeAddress) : null, note, id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'FULFILLMENT_SELECTED', order.fulfillment_status || 'PENDING', order.fulfillment_status || 'PENDING', { method });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentMethod: updated.rows[0].fulfillment_method, fulfillmentStatus: updated.rows[0].fulfillment_status } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace fulfillment', error); return res.status(500).json({ error: 'fulfillment selection failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/fulfillment-progress', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const requested = typeof req.body?.state === 'string' ? req.body.state.trim().toUpperCase() : '';
    const note = typeof req.body?.note === 'string' ? req.body.note.trim().slice(0, 500) : '';
    const allowedStates = new Set(['PREPARING','DISPATCHED','IN_TRANSIT','DELIVERED','READY_FOR_PICKUP','FAILED_DELIVERY','RETURNED']);
    if (!id || !allowedStates.has(requested)) return res.status(400).json({ error: 'valid order id and fulfillment state are required' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isSeller(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can update fulfillment progress' }); }
      if (order.status !== 'PAID' && order.status !== 'SHIPPED') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order is not ready for fulfillment progress' }); }
      const current = order.fulfillment_status || (order.status === 'SHIPPED' ? (order.fulfillment_method === 'PICKUP' ? 'PICKUP_COMPLETED' : 'DISPATCHED') : 'PAID');
      if (!canAdvanceFulfillment(current, requested, order.fulfillment_method)) { await client.query('ROLLBACK'); return res.status(409).json({ error: `invalid fulfillment transition from ${current} to ${requested}` }); }
      if (['FAILED_DELIVERY','RETURNED'].includes(requested) && await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      if (requested === 'RETURNED' && current !== 'FAILED_DELIVERY') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'return requires a failed delivery state first' }); }
      const updated = await client.query(`UPDATE marketplace_orders SET fulfillment_status=$1,fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$2 RETURNING *`, [requested, id]);
      await recordFulfillmentEvent(client, id, req.user.sub, `FULFILLMENT_${requested}`, current, requested, { fulfillmentMethod: order.fulfillment_method, note: note || null });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, fulfillmentStatusUpdatedAt: updated.rows[0].fulfillment_status_updated_at } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace fulfillment progress', error); return res.status(500).json({ error: 'fulfillment progress update failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/media/:id', auth, async (req, res) => {
    return res.status(405).json({ error: 'method not allowed' });
  });

  app.post('/api/marketplace/orders/:id/ship', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const tracking = typeof req.body?.trackingReference === 'string' ? req.body.trackingReference.trim().slice(0, 160) : '';
    if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isSeller(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can ship this order' }); }
      if (order.status !== 'PAID') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'only paid orders can be shipped' }); }
      if (order.fulfillment_method === 'PICKUP') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup orders require seller handover confirmation' }); }
      if (order.fulfillment_method === 'DELIVERY' && !order.shipping_address) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'buyer delivery details are missing' }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const current = order.fulfillment_status || 'PAID';
      if (!['PAID','PREPARING'].includes(current)) { await client.query('ROLLBACK'); return res.status(409).json({ error: `order cannot be dispatched from ${current}` }); }
      const updated = await client.query(`UPDATE marketplace_orders SET status='SHIPPED',tracking_reference=$1,shipped_at=NOW(),fulfillment_status='DISPATCHED',fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$2 RETURNING *`, [tracking || null, id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'ORDER_SHIPPED', current, 'DISPATCHED', { trackingReference: tracking || null, fulfillmentMethod: order.fulfillment_method });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, trackingReference: updated.rows[0].tracking_reference, shippedAt: updated.rows[0].shipped_at } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace ship', error); return res.status(500).json({ error: 'order shipping failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/pickup-handover', auth, async (req, res) => {
    const id = parseUuid(req.params.id); if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isSeller(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can confirm pickup handover' }); }
      if (order.status !== 'PAID') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'only paid orders can be handed over for pickup' }); }
      if (order.fulfillment_method !== 'PICKUP') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup handover is only for pickup orders' }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const current = order.fulfillment_status || 'PAID';
      if (!['PAID','READY_FOR_PICKUP'].includes(current)) { await client.query('ROLLBACK'); return res.status(409).json({ error: `pickup cannot be handed over from ${current}` }); }
      const updated = await client.query(`UPDATE marketplace_orders SET status='SHIPPED',pickup_handover_at=NOW(),pickup_handover_by=$1,shipped_at=NOW(),fulfillment_status='PICKUP_COMPLETED',fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$2 AND status='PAID' RETURNING *`, [req.user.sub, id]);
      if (!updated.rows[0]) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order changed before pickup handover' }); }
      await recordFulfillmentEvent(client, id, req.user.sub, 'PICKUP_HANDOVER_CONFIRMED', current, 'PICKUP_COMPLETED', { fulfillmentMethod: 'PICKUP' });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, pickupHandoverAt: updated.rows[0].pickup_handover_at } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace pickup handover', error); return res.status(500).json({ error: 'pickup handover failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/confirm-delivery', auth, async (req, res) => {
    const id = parseUuid(req.params.id); if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isBuyer(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can confirm delivery' }); }
      if (!['SHIPPED','DELIVERED'].includes(order.status)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order is not ready for delivery confirmation' }); }
      if (order.fulfillment_method === 'PICKUP' && !order.pickup_handover_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup handover must be confirmed by the seller first' }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const current = order.fulfillment_status || (order.fulfillment_method === 'PICKUP' ? 'PICKUP_COMPLETED' : 'DISPATCHED');
      if (!['DELIVERED','DISPATCHED','PICKUP_COMPLETED'].includes(current)) { await client.query('ROLLBACK'); return res.status(409).json({ error: `order is not ready for buyer receipt from ${current}` }); }
      const deadline = new Date(Date.now() + 48 * 60 * 60 * 1000);
      const updated = await client.query(`UPDATE marketplace_orders SET status='INSPECTION',delivered_at=COALESCE(delivered_at,NOW()),inspection_deadline=$1,fulfillment_status='INSPECTION',fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$2 RETURNING *`, [deadline.toISOString(), id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'BUYER_RECEIVED', current, 'INSPECTION', { inspectionHours: 48, fulfillmentMethod: order.fulfillment_method });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, inspectionDeadline: updated.rows[0].inspection_deadline } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace confirm delivery', error); return res.status(500).json({ error: 'delivery confirmation failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/complete', auth, async (req, res) => {
    const id = parseUuid(req.params.id); if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isBuyer(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can complete this order' }); }
      if (order.status !== 'INSPECTION') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order is not in inspection' }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const updated = await client.query(`UPDATE marketplace_orders SET status='COMPLETED',completed_at=NOW(),fulfillment_status='COMPLETED',fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$1 AND status='INSPECTION' RETURNING *`, [id]);
      if (!updated.rows[0]) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order changed before completion' }); }
      await client.query('UPDATE marketplace_listings SET quantity=GREATEST(0,quantity-$1),reserved_quantity=GREATEST(0,reserved_quantity-$1),active=CASE WHEN quantity-$1 <= 0 THEN FALSE ELSE active END,updated_at=NOW() WHERE id=$2', [order.quantity, order.listing_id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'ORDER_COMPLETED', 'INSPECTION', 'COMPLETED', { payout: 'eligible_for_release', fulfillmentMethod: order.fulfillment_method });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, completedAt: updated.rows[0].completed_at }, payout: { status: 'eligible_for_release' } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace complete', error); return res.status(500).json({ error: 'order completion failed' }); }
    finally { client.release(); }
  });
}
