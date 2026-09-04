import crypto from 'node:crypto';

export function registerMarketplaceCompletionRoutes({ app, pool, auth }) {
  let schemaPromise;

  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS fulfillment_method TEXT NOT NULL DEFAULT 'DELIVERY' CHECK (fulfillment_method IN ('DELIVERY','PICKUP'));
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_address JSONB;
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS buyer_note TEXT NOT NULL DEFAULT '';
        CREATE INDEX IF NOT EXISTS marketplace_orders_fulfillment_idx ON marketplace_orders (seller_id, status, updated_at DESC);
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

  app.get('/api/marketplace/seller/orders', auth, async (req, res) => {
    try {
      await ensureSchema();
      const status = typeof req.query?.status === 'string' ? req.query.status.trim().toUpperCase() : '';
      const allowed = new Set(['PAYMENT_PENDING','PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED','DISPUTED','CANCELLED','REFUNDED']);
      const params = [req.user.sub];
      let where = 'o.seller_id = $1';
      if (status && allowed.has(status)) { params.push(status); where += ` AND o.status = $${params.length}`; }
      const result = await pool.query(`
        SELECT o.*, u.username AS buyer_username, u.display_name AS buyer_display_name
        FROM marketplace_orders o JOIN users u ON u.id = o.buyer_id
        WHERE ${where} ORDER BY o.created_at DESC LIMIT 100
      `, params);
      return res.json({ orders: result.rows.map((row) => ({
        id: String(row.id), buyerId: String(row.buyer_id), buyerUsername: row.buyer_username, buyerDisplayName: row.buyer_display_name,
        listingId: String(row.listing_id), quantity: Number(row.quantity), unitPrice: Number(row.unit_price), deliveryFee: Number(row.delivery_fee),
        totalAmount: Number(row.total_amount), currency: row.currency, product: row.product_snapshot, status: row.status,
        fulfillmentMethod: row.fulfillment_method, shippingAddress: row.shipping_address, buyerNote: row.buyer_note,
        paymentReference: row.payment_reference, trackingReference: row.tracking_reference, shippedAt: row.shipped_at,
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
    if (method === 'DELIVERY' && (!address || typeof address.name !== 'string' || typeof address.phone !== 'string' || typeof address.address !== 'string' || !address.address.trim())) {
      return res.status(400).json({ error: 'delivery requires name, phone and address' });
    }

    const client = await pool.connect();
    try {
      await ensureSchema();
      await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isBuyer(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can choose fulfillment' }); }
      if (!['PAYMENT_PENDING','PAID'].includes(order.status)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'fulfillment can only be selected before shipping' }); }

      const snapshot = order.product_snapshot && typeof order.product_snapshot === 'object' ? order.product_snapshot : {};
      const deliveryAvailable = Boolean(snapshot.deliveryAvailable);
      const pickupAvailable = Boolean(snapshot.pickupAvailable);
      if (method === 'DELIVERY' && !deliveryAvailable) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'delivery is not available for this listing' }); }
      if (method === 'PICKUP' && !pickupAvailable) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup is not available for this listing' }); }

      const safeAddress = method === 'DELIVERY' ? {
        name: String(address.name).trim().slice(0, 120), phone: String(address.phone).trim().slice(0, 40), address: String(address.address).trim().slice(0, 500),
        city: typeof address.city === 'string' ? address.city.trim().slice(0, 100) : '', state: typeof address.state === 'string' ? address.state.trim().slice(0, 100) : '',
        country: typeof address.country === 'string' ? address.country.trim().slice(0, 100) : ''
      } : null;
      const updated = await client.query(`UPDATE marketplace_orders SET fulfillment_method=$1,shipping_address=$2::jsonb,buyer_note=$3,updated_at=NOW() WHERE id=$4 RETURNING *`, [method, safeAddress ? JSON.stringify(safeAddress) : null, note, id]);
      await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'FULFILLMENT_SELECTED',$3,$3,$4::jsonb)`, [id, req.user.sub, order.status, JSON.stringify({ method })]);
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentMethod: updated.rows[0].fulfillment_method } });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('marketplace fulfillment', error);
      return res.status(500).json({ error: 'fulfillment selection failed' });
    } finally { client.release(); }
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
      if (order.fulfillment_method === 'DELIVERY' && !order.shipping_address) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'buyer delivery details are missing' }); }
      const updated = await client.query(`UPDATE marketplace_orders SET status='SHIPPED',tracking_reference=$1,shipped_at=NOW(),updated_at=NOW() WHERE id=$2 RETURNING *`, [tracking || null, id]);
      await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'ORDER_SHIPPED',$3,'SHIPPED',$4::jsonb)`, [id, req.user.sub, order.status, JSON.stringify({ trackingReference: tracking || null })]);
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, trackingReference: updated.rows[0].tracking_reference, shippedAt: updated.rows[0].shipped_at } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace ship', error); return res.status(500).json({ error: 'order shipping failed' }); }
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
      const now = new Date(); const deadline = new Date(now.getTime() + 48 * 60 * 60 * 1000);
      const updated = await client.query(`UPDATE marketplace_orders SET status='INSPECTION',delivered_at=COALESCE(delivered_at,NOW()),inspection_deadline=$1,updated_at=NOW() WHERE id=$2 RETURNING *`, [deadline.toISOString(), id]);
      await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'DELIVERY_CONFIRMED',$3,'INSPECTION',$4::jsonb)`, [id, req.user.sub, order.status, JSON.stringify({ inspectionHours: 48 })]);
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, inspectionDeadline: updated.rows[0].inspection_deadline } });
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
      const updated = await client.query(`UPDATE marketplace_orders SET status='COMPLETED',completed_at=NOW(),updated_at=NOW() WHERE id=$1 RETURNING *`, [id]);
      await client.query('UPDATE marketplace_listings SET quantity=GREATEST(0,quantity-$1),reserved_quantity=GREATEST(0,reserved_quantity-$1),active=CASE WHEN quantity-$1 <= 0 THEN FALSE ELSE active END,updated_at=NOW() WHERE id=$2', [order.quantity, order.listing_id]);
      await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'ORDER_COMPLETED',$3,'COMPLETED',$4::jsonb)`, [id, req.user.sub, order.status, JSON.stringify({ payout: 'eligible_for_release' })]);
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, completedAt: updated.rows[0].completed_at }, payout: { status: 'eligible_for_release' } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace complete', error); return res.status(500).json({ error: 'order completion failed' }); }
    finally { client.release(); }
  });
}