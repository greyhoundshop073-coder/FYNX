export function registerMarketplaceTransactionRoutes({ app, pool, auth }) {
  let schemaPromise;

  const ensureMarketplaceTransactionSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0);

        CREATE TABLE IF NOT EXISTS marketplace_orders (
          id UUID PRIMARY KEY,
          buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
          seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
          listing_id BIGINT NOT NULL REFERENCES marketplace_listings(id) ON DELETE RESTRICT,
          quantity INTEGER NOT NULL CHECK (quantity > 0),
          unit_price NUMERIC(14,2) NOT NULL CHECK (unit_price > 0),
          delivery_fee NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (delivery_fee >= 0),
          total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount > 0),
          currency TEXT NOT NULL,
          product_snapshot JSONB NOT NULL,
          status TEXT NOT NULL CHECK (status IN ('PAYMENT_PENDING','PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED','DISPUTED','CANCELLED','REFUNDED')),
          payment_reference TEXT,
          tracking_reference TEXT,
          shipped_at TIMESTAMPTZ,
          delivered_at TIMESTAMPTZ,
          inspection_deadline TIMESTAMPTZ,
          completed_at TIMESTAMPTZ,
          cancelled_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE UNIQUE INDEX IF NOT EXISTS marketplace_orders_buyer_idempotency_idx ON marketplace_orders (buyer_id, id);
        CREATE INDEX IF NOT EXISTS marketplace_orders_buyer_idx ON marketplace_orders (buyer_id, created_at DESC);
        CREATE INDEX IF NOT EXISTS marketplace_orders_seller_idx ON marketplace_orders (seller_id, created_at DESC);
        CREATE INDEX IF NOT EXISTS marketplace_orders_listing_idx ON marketplace_orders (listing_id, created_at DESC);
        CREATE INDEX IF NOT EXISTS marketplace_orders_status_idx ON marketplace_orders (status, updated_at DESC);

        CREATE TABLE IF NOT EXISTS marketplace_order_events (
          id BIGSERIAL PRIMARY KEY,
          order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
          actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
          event_type TEXT NOT NULL,
          from_status TEXT,
          to_status TEXT,
          metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS marketplace_order_events_order_idx ON marketplace_order_events (order_id, created_at ASC);

        CREATE TABLE IF NOT EXISTS marketplace_order_disputes (
          id UUID PRIMARY KEY,
          order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
          opened_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
          reason TEXT NOT NULL CHECK (reason IN ('ITEM_NOT_RECEIVED','WRONG_ITEM','DAMAGED','NOT_AS_DESCRIBED','SUSPECTED_SCAM','OTHER')),
          details TEXT NOT NULL DEFAULT '',
          status TEXT NOT NULL CHECK (status IN ('OPEN','UNDER_REVIEW','RESOLVED_BUYER','RESOLVED_SELLER','CANCELLED')),
          resolution_notes TEXT NOT NULL DEFAULT '',
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE UNIQUE INDEX IF NOT EXISTS marketplace_order_open_dispute_idx ON marketplace_order_disputes (order_id) WHERE status IN ('OPEN','UNDER_REVIEW');
        CREATE INDEX IF NOT EXISTS marketplace_order_disputes_order_idx ON marketplace_order_disputes (order_id, created_at DESC);

        CREATE TABLE IF NOT EXISTS marketplace_order_evidence (
          id BIGSERIAL PRIMARY KEY,
          order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
          submitted_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
          kind TEXT NOT NULL CHECK (kind IN ('PHOTO','VIDEO','DOCUMENT','MESSAGE_REFERENCE','TRACKING_REFERENCE','NOTE')),
          value TEXT NOT NULL,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS marketplace_order_evidence_order_idx ON marketplace_order_evidence (order_id, created_at ASC);
      `).catch((error) => {
        schemaPromise = undefined;
        throw error;
      });
    }
    return schemaPromise;
  };

  const parsePositiveInt = (value) => {
    const n = Number(value);
    return Number.isInteger(n) && n > 0 ? n : null;
  };

  const parseUuid = (value) => typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;

  const isParticipant = (order, userId) => String(order.buyer_id) === String(userId) || String(order.seller_id) === String(userId);

  const publicOrder = (row) => ({
    id: String(row.id),
    buyerId: String(row.buyer_id),
    sellerId: String(row.seller_id),
    listingId: String(row.listing_id),
    quantity: Number(row.quantity),
    unitPrice: Number(row.unit_price),
    deliveryFee: Number(row.delivery_fee),
    totalAmount: Number(row.total_amount),
    currency: row.currency,
    product: row.product_snapshot,
    status: row.status,
    paymentReference: row.payment_reference || null,
    trackingReference: row.tracking_reference || null,
    shippedAt: row.shipped_at,
    deliveredAt: row.delivered_at,
    inspectionDeadline: row.inspection_deadline,
    completedAt: row.completed_at,
    cancelledAt: row.cancelled_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  });

  app.post('/api/marketplace/orders', auth, async (req, res) => {
    const listingId = parsePositiveInt(req.body?.listingId);
    const quantity = parsePositiveInt(req.body?.quantity);
    const clientOrderId = parseUuid(req.body?.orderId);
    if (!listingId || !quantity) return res.status(400).json({ error: 'valid listingId and quantity are required' });

    const client = await pool.connect();
    try {
      await ensureMarketplaceTransactionSchema();
      await client.query('BEGIN');
      const listingResult = await client.query(`
        SELECT l.*, u.username AS seller_username, u.display_name AS seller_display_name
        FROM marketplace_listings l
        JOIN users u ON u.id = l.seller_id
        WHERE l.id = $1 AND l.active = TRUE
        FOR UPDATE
      `, [listingId]);
      const listing = listingResult.rows[0];
      if (!listing) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'listing not found or no longer available' });
      }
      if (String(listing.seller_id) === String(req.user.sub)) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: 'you cannot purchase your own listing' });
      }
      const available = Number(listing.quantity) - Number(listing.reserved_quantity || 0);
      if (quantity > available) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'requested quantity is not available' });
      }

      if (clientOrderId) {
        const existing = await client.query('SELECT * FROM marketplace_orders WHERE id = $1 AND buyer_id = $2', [clientOrderId, req.user.sub]);
        if (existing.rows[0]) {
          await client.query('ROLLBACK');
          return res.status(200).json({ order: publicOrder(existing.rows[0]), idempotent: true });
        }
      }

      const orderId = clientOrderId || crypto.randomUUID();
      const unitPrice = Number(listing.price);
      const deliveryFee = listing.delivery_fee == null ? 0 : Number(listing.delivery_fee);
      const totalAmount = (unitPrice * quantity) + deliveryFee;
      const snapshot = {
        listingId: String(listing.id),
        sellerId: String(listing.seller_id),
        sellerUsername: listing.seller_username,
        sellerDisplayName: listing.seller_display_name,
        storeName: listing.store_name,
        title: listing.title,
        description: listing.description,
        price: unitPrice,
        currency: listing.currency,
        category: listing.category,
        condition: listing.condition,
        location: listing.location,
        deliveryAvailable: Boolean(listing.delivery_available),
        pickupAvailable: Boolean(listing.pickup_available),
        deliveryFee,
        mediaIds: Array.isArray(listing.media_ids) ? listing.media_ids.map(String) : []
      };

      const inserted = await client.query(`
        INSERT INTO marketplace_orders
          (id,buyer_id,seller_id,listing_id,quantity,unit_price,delivery_fee,total_amount,currency,product_snapshot,status)
        VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,'PAYMENT_PENDING')
        RETURNING *
      `, [orderId, req.user.sub, listing.seller_id, listing.id, quantity, unitPrice, deliveryFee, totalAmount, listing.currency, JSON.stringify(snapshot)]);

      await client.query('UPDATE marketplace_listings SET reserved_quantity = reserved_quantity + $1, updated_at = NOW() WHERE id = $2', [quantity, listing.id]);
      await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'ORDER_CREATED',NULL,'PAYMENT_PENDING',$3::jsonb)`, [orderId, req.user.sub, JSON.stringify({ quantity, protected: true })]);
      await client.query('COMMIT');
      return res.status(201).json({ order: publicOrder(inserted.rows[0]), protection: { enabled: true, payment: 'provider_required', payout: 'not_released' } });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      if (error?.code === '23505') return res.status(409).json({ error: 'order already exists' });
      console.error('marketplace order create', error);
      return res.status(500).json({ error: 'protected order creation failed' });
    } finally { client.release(); }
  });

  app.get('/api/marketplace/orders', auth, async (req, res) => {
    try {
      await ensureMarketplaceTransactionSchema();
      const result = await pool.query(`SELECT o.* FROM marketplace_orders o WHERE o.buyer_id = $1 OR o.seller_id = $1 ORDER BY o.created_at DESC LIMIT 100`, [req.user.sub]);
      return res.json({ orders: result.rows.map(publicOrder) });
    } catch (error) { console.error('marketplace orders', error); return res.status(500).json({ error: 'order lookup failed' }); }
  });

  app.get('/api/marketplace/orders/:id', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    if (!id) return res.status(400).json({ error: 'invalid order id' });
    try {
      await ensureMarketplaceTransactionSchema();
      const result = await pool.query(`SELECT o.*, b.username AS buyer_username, b.display_name AS buyer_display_name, s.username AS seller_username, s.display_name AS seller_display_name FROM marketplace_orders o JOIN users b ON b.id=o.buyer_id JOIN users s ON s.id=o.seller_id WHERE o.id=$1`, [id]);
      const order = result.rows[0];
      if (!order) return res.status(404).json({ error: 'order not found' });
      if (!isParticipant(order, req.user.sub)) return res.status(403).json({ error: 'order unavailable' });
      const events = await pool.query(`SELECT event_type,from_status,to_status,metadata,created_at FROM marketplace_order_events WHERE order_id=$1 ORDER BY created_at ASC`, [id]);
      const disputes = await pool.query(`SELECT id,opened_by,reason,details,status,resolution_notes,created_at,updated_at FROM marketplace_order_disputes WHERE order_id=$1 ORDER BY created_at DESC`, [id]);
      return res.json({ order: publicOrder(order), events: events.rows, disputes: disputes.rows });
    } catch (error) { console.error('marketplace order detail', error); return res.status(500).json({ error: 'order lookup failed' }); }
  });

  app.post('/api/marketplace/orders/:id/cancel', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureMarketplaceTransactionSchema();
      await client.query('BEGIN');
      const orderResult = await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id]);
      const order = orderResult.rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (String(order.buyer_id) !== String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can cancel this order' }); }
      if (order.status !== 'PAYMENT_PENDING') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'only unpaid orders can be cancelled here' }); }
      const updated = await client.query(`UPDATE marketplace_orders SET status='CANCELLED',cancelled_at=NOW(),updated_at=NOW() WHERE id=$1 RETURNING *`, [id]);
      await client.query('UPDATE marketplace_listings SET reserved_quantity=GREATEST(0,reserved_quantity-$1),updated_at=NOW() WHERE id=$2', [order.quantity, order.listing_id]);
      await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status) VALUES ($1,$2,'ORDER_CANCELLED',$3,'CANCELLED')`, [id, req.user.sub, order.status]);
      await client.query('COMMIT');
      return res.json({ order: publicOrder(updated.rows[0]) });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('marketplace order cancel', error);
      return res.status(500).json({ error: 'order cancellation failed' });
    } finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/disputes', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const allowedReasons = new Set(['ITEM_NOT_RECEIVED','WRONG_ITEM','DAMAGED','NOT_AS_DESCRIBED','SUSPECTED_SCAM','OTHER']);
    const reason = typeof req.body?.reason === 'string' ? req.body.reason.trim().toUpperCase() : '';
    const details = typeof req.body?.details === 'string' ? req.body.details.trim().slice(0,4000) : '';
    if (!id || !allowedReasons.has(reason)) return res.status(400).json({ error: 'valid order id and dispute reason are required' });
    try {
      await ensureMarketplaceTransactionSchema();
      const orderResult = await pool.query('SELECT * FROM marketplace_orders WHERE id=$1', [id]);
      const order = orderResult.rows[0];
      if (!order) return res.status(404).json({ error: 'order not found' });
      if (!isParticipant(order, req.user.sub)) return res.status(403).json({ error: 'order unavailable' });
      if (['CANCELLED','REFUNDED','COMPLETED'].includes(order.status)) return res.status(409).json({ error: 'this order is no longer disputable' });
      const disputeId = crypto.randomUUID();
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const updated = await client.query(`UPDATE marketplace_orders SET status='DISPUTED',updated_at=NOW() WHERE id=$1 AND status NOT IN ('CANCELLED','REFUNDED','COMPLETED') RETURNING *`, [id]);
        if (!updated.rows[0]) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order state changed; please retry' }); }
        const dispute = await client.query(`INSERT INTO marketplace_order_disputes (id,order_id,opened_by,reason,details,status) VALUES ($1,$2,$3,$4,$5,'OPEN') RETURNING *`, [disputeId,id,req.user.sub,reason,details]);
        await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'DISPUTE_OPENED',$3,'DISPUTED',$4::jsonb)`, [id,req.user.sub,order.status,JSON.stringify({ reason })]);
        await client.query('COMMIT');
        return res.status(201).json({ order: publicOrder(updated.rows[0]), dispute: dispute.rows[0] });
      } catch (error) {
        try { await client.query('ROLLBACK'); } catch {}
        if (error?.code === '23505') return res.status(409).json({ error: 'an active dispute already exists for this order' });
        throw error;
      } finally { client.release(); }
    } catch (error) { console.error('marketplace dispute', error); return res.status(500).json({ error: 'dispute creation failed' }); }
  });

  app.post('/api/marketplace/orders/:id/evidence', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const allowedKinds = new Set(['PHOTO','VIDEO','DOCUMENT','MESSAGE_REFERENCE','TRACKING_REFERENCE','NOTE']);
    const kind = typeof req.body?.kind === 'string' ? req.body.kind.trim().toUpperCase() : '';
    const value = typeof req.body?.value === 'string' ? req.body.value.trim().slice(0,4000) : '';
    if (!id || !allowedKinds.has(kind) || !value) return res.status(400).json({ error: 'valid evidence is required' });
    try {
      await ensureMarketplaceTransactionSchema();
      const order = (await pool.query('SELECT * FROM marketplace_orders WHERE id=$1', [id])).rows[0];
      if (!order) return res.status(404).json({ error: 'order not found' });
      if (!isParticipant(order, req.user.sub)) return res.status(403).json({ error: 'order unavailable' });
      const result = await pool.query(`INSERT INTO marketplace_order_evidence (order_id,submitted_by,kind,value) VALUES ($1,$2,$3,$4) RETURNING id,order_id,submitted_by,kind,value,created_at`, [id,req.user.sub,kind,value]);
      return res.status(201).json({ evidence: result.rows[0] });
    } catch (error) { console.error('marketplace evidence', error); return res.status(500).json({ error: 'evidence submission failed' }); }
  });
}
